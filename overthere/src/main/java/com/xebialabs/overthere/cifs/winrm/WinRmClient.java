/*
 * This file is part of WinRM.
 *
 * WinRM is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * WinRM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with WinRM.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.xebialabs.overthere.cifs.winrm;

import com.xebialabs.overthere.OverthereProcessOutputHandler;
import com.xebialabs.overthere.RuntimeIOException;
import com.xebialabs.overthere.cifs.winrm.exception.WinRMRuntimeIOException;
import org.apache.commons.codec.binary.Base64;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

public class WinRmClient {

	private final URL targetURL;
	private final HttpConnector connector;

	private String timeout;
	private int envelopSize;
	private String locale;

	private String exitCode;
	private String shellId;
	private String commandId;

	private int chunk = 0;

	public WinRmClient(HttpConnector connector, URL targetURL) {
		this.connector = connector;
		this.targetURL = targetURL;
	}

	public void runCmd(String command, OverthereProcessOutputHandler handler) {
		try {
			shellId = openShell();
			commandId = runCommand(command);
			getCommandOutput(handler);
		} finally {
			cleanUp();
			closeShell();
		}
	}


	private void closeShell() {
		if (shellId == null)
			return;
		logger.debug("closeShell shellId {}", shellId);
		final Document requestDocument = getRequestDocument(Action.WS_DELETE, ResourceURI.RESOURCE_URI_CMD, null, shellId, null);
        sendMessage(requestDocument, null);
	}

	private void cleanUp() {
		if (commandId == null)
			return;
		logger.debug("cleanUp shellId {} commandId {} ", shellId, commandId);
		final Element bodyContent = DocumentHelper.createElement(QName.get("Signal", Namespaces.NS_WIN_SHELL)).addAttribute("CommandId", commandId);
		bodyContent.addElement(QName.get("Code", Namespaces.NS_WIN_SHELL)).addText("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/signal/terminate");
		final Document requestDocument = getRequestDocument(Action.WS_SIGNAL, ResourceURI.RESOURCE_URI_CMD, null, shellId, bodyContent);
        sendMessage(requestDocument, SoapAction.SIGNAL);
	}

	private void getCommandOutput(OverthereProcessOutputHandler handler) {
		logger.debug("getCommandOutput shellId {} commandId {} ", shellId, commandId);
		final Element bodyContent = DocumentHelper.createElement(QName.get("Receive", Namespaces.NS_WIN_SHELL));
		bodyContent.addElement(QName.get("DesiredStream", Namespaces.NS_WIN_SHELL)).addAttribute("CommandId", commandId).addText("stdout stderr");
		final Document requestDocument = getRequestDocument(Action.WS_RECEIVE, ResourceURI.RESOURCE_URI_CMD, null, shellId, bodyContent);

		for (;;) {
			Document responseDocument = sendMessage(requestDocument, SoapAction.RECEIVE);
			String stdout = handleStream(responseDocument, ResponseExtractor.STDOUT);
			BufferedReader stdoutReader = new BufferedReader(new StringReader(stdout));
			try {
				for(;;) {
					String line = stdoutReader.readLine();
					if(line == null) {
						break;
					}
					handler.handleOutputLine(line);
				}
			} catch(IOException exc) {
				throw new RuntimeIOException("Unexpected I/O exception while reading stdout", exc);
			}

			String stderr = handleStream(responseDocument, ResponseExtractor.STDERR);
			BufferedReader stderrReader = new BufferedReader(new StringReader(stderr));
			try {
				for(;;) {
					String line = stderrReader.readLine();
					if(line == null) {
						break;
					}
					handler.handleErrorLine(line);
				}
			} catch(IOException exc) {
				throw new RuntimeIOException("Unexpected I/O exception while reading stderr", exc);
			}

			if (chunk == 0) {
				try {
					exitCode = getFirstElement(responseDocument, ResponseExtractor.EXIT_CODE);
					logger.info("exit code {}", exitCode);
				} catch (Exception e) {
					logger.debug("not found");
				}
			}
			chunk++;

			/* We may need to get additional output if the stream has not finished.
										The CommandState will change from Running to Done like so:
										@example

										 from...
										 <rsp:CommandState CommandId="..." State="http://schemas.microsoft.com/wbem/wsman/1/windows/shell/CommandState/Running"/>
										 to...
										 <rsp:CommandState CommandId="..." State="http://schemas.microsoft.com/wbem/wsman/1/windows/shell/CommandState/Done">
											 <rsp:ExitCode>0</rsp:ExitCode>
										 </rsp:CommandState>
									 */
			final List<?> list = ResponseExtractor.STREAM_DONE.getXPath().selectNodes(responseDocument);
			if (!list.isEmpty()) {
				exitCode = getFirstElement(responseDocument, ResponseExtractor.EXIT_CODE);
				logger.info("exit code {}", exitCode);
				break;
			}
		}


		logger.debug("all the command output has been fetched (chunk={})", chunk);


	}

	private String handleStream(Document responseDocument, ResponseExtractor stream) {
		StringBuffer buffer = new StringBuffer();
		@SuppressWarnings("unchecked")
        final List<Element> streams = (List<Element>) stream.getXPath().selectNodes(responseDocument);
		if (!streams.isEmpty()) {
			final Base64 base64 = new Base64();
			Iterator<Element> itStreams = streams.iterator();
			while (itStreams.hasNext()) {
				Element e = itStreams.next();
				//TODO check performance with http://www.iharder.net/current/java/base64/
				final byte[] decode = base64.decode(e.getText());
				buffer.append(new String(decode));
			}
		}
		logger.debug("handleStream {} buffer {}", stream, buffer);
		return buffer.toString();

	}


	private String runCommand(String command) {
		logger.debug("runCommand shellId {} command {}", shellId, command);
		final Element bodyContent = DocumentHelper.createElement(QName.get("CommandLine", Namespaces.NS_WIN_SHELL));

		String encoded = command;
		encoded = "\"" + encoded + "\"";


		logger.info("Encoded command is {}", encoded);

		bodyContent.addElement(QName.get("Command", Namespaces.NS_WIN_SHELL)).addText(encoded);

		final Document requestDocument = getRequestDocument(Action.WS_COMMAND, ResourceURI.RESOURCE_URI_CMD, OptionSet.RUN_COMMAND, shellId, bodyContent);
		Document responseDocument = sendMessage(requestDocument, SoapAction.COMMAND_LINE);

		return getFirstElement(responseDocument, ResponseExtractor.COMMAND_ID);
	}


	private String getFirstElement(Document doc, ResponseExtractor extractor) {
		@SuppressWarnings("unchecked")
        final List<Element> nodes = (List<Element>) extractor.getXPath().selectNodes(doc);
		if (nodes.isEmpty())
			throw new RuntimeException("Cannot find " + extractor.getXPath() + " in " + toString(doc));

		final Element next = (Element) nodes.iterator().next();
		return next.getText();
	}

	private String openShell() {
		logger.debug("openShell");

		final Element bodyContent = DocumentHelper.createElement(QName.get("Shell", Namespaces.NS_WIN_SHELL));
		bodyContent.addElement(QName.get("InputStreams", Namespaces.NS_WIN_SHELL)).addText("stdin");
		bodyContent.addElement(QName.get("OutputStreams", Namespaces.NS_WIN_SHELL)).addText("stdout stderr");


		final Document requestDocument = getRequestDocument(Action.WS_ACTION, ResourceURI.RESOURCE_URI_CMD, OptionSet.OPEN_SHELL, null, bodyContent);
		Document responseDocument = sendMessage(requestDocument, SoapAction.SHELL);

		return getFirstElement(responseDocument, ResponseExtractor.SHELL_ID);

	}

	private Document sendMessage(Document requestDocument, SoapAction soapAction) {
		return connector.sendMessage(requestDocument, soapAction);
	}

	private Document getRequestDocument(Action action, ResourceURI resourceURI, OptionSet optionSet, String shelId, Element bodyContent) {
		Document doc = DocumentHelper.createDocument();
		final Element envelope = doc.addElement(QName.get("Envelope", Namespaces.NS_SOAP_ENV));
		envelope.add(getHeader(action, resourceURI, optionSet, shelId));

		final Element body = envelope.addElement(QName.get("Body", Namespaces.NS_SOAP_ENV));

		if (bodyContent != null)
			body.add(bodyContent);

		return doc;
	}


	private Element getHeader(Action action, ResourceURI resourceURI, OptionSet optionSet, String shellId) {
		final Element header = DocumentHelper.createElement(QName.get("Header", Namespaces.NS_SOAP_ENV));
		header.addElement(QName.get("To", Namespaces.NS_ADDRESSING)).addText(targetURL.toString());
		final Element replyTo = header.addElement(QName.get("ReplyTo", Namespaces.NS_ADDRESSING));
		replyTo.addElement(QName.get("Address", Namespaces.NS_ADDRESSING)).addAttribute("mustUnderstand", "true").addText("http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous");
		header.addElement(QName.get("MaxEnvelopeSize", Namespaces.NS_WSMAN_DMTF)).addAttribute("mustUnderstand", "true").addText("" + envelopSize);
		header.addElement(QName.get("MessageID", Namespaces.NS_ADDRESSING)).addText(getUUID());
		header.addElement(QName.get("Locale", Namespaces.NS_WSMAN_DMTF)).addAttribute("mustUnderstand", "false").addAttribute("xml:lang", locale);
		header.addElement(QName.get("DataLocale", Namespaces.NS_WSMAN_MSFT)).addAttribute("mustUnderstand", "false").addAttribute("xml:lang", locale);
		header.addElement(QName.get("OperationTimeout", Namespaces.NS_WSMAN_DMTF)).addText(timeout);
		header.add(action.getElement());
		if (shellId != null) {
			header.addElement(QName.get("SelectorSet", Namespaces.NS_WSMAN_DMTF)).addElement(QName.get("Selector", Namespaces.NS_WSMAN_DMTF)).addAttribute("Name", "ShellId").addText(shellId);
		}
		header.add(resourceURI.getElement());
		if (optionSet != null) {
			header.add(optionSet.getElement());
		}

		return header;
	}


	private String toString(Document doc) {
		StringWriter stringWriter = new StringWriter();
		XMLWriter xmlWriter = new XMLWriter(stringWriter, OutputFormat.createPrettyPrint());
		try {
			xmlWriter.write(doc);
			xmlWriter.close();
		} catch (IOException e) {
			throw new WinRMRuntimeIOException("error ", e);
		}
		return stringWriter.toString();
	}

	private String getUUID() {
		return "uuid:" + java.util.UUID.randomUUID().toString().toUpperCase();
	}

	public int getExitCode() {
		return Integer.parseInt(exitCode);
	}

	public String getTimeout() {
		return timeout;
	}

	public void setTimeout(String timeout) {
		this.timeout = timeout;
	}

	public int getEnvelopSize() {
		return envelopSize;
	}

	public void setEnvelopSize(int envelopSize) {
		this.envelopSize = envelopSize;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public URL getTargetURL() {
		return targetURL;
	}

	private static Logger logger = LoggerFactory.getLogger(WinRmClient.class);


}

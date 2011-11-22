package com.xebialabs.overthere.cifs.winrm;

import static com.google.common.base.Preconditions.checkArgument;
import static com.xebialabs.overthere.ConnectionOptions.PASSWORD;
import static com.xebialabs.overthere.ConnectionOptions.USERNAME;
import static com.xebialabs.overthere.OperatingSystemFamily.WINDOWS;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.CIFS_PROTOCOL;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.CONTEXT;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.DEFAULT_ENVELOP_SIZE;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.DEFAULT_LOCALE;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.DEFAULT_TIMEOUT;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.DEFAULT_WINRM_CONTEXT;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.ENVELOP_SIZE;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.LOCALE;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.TIMEMOUT;
import static com.xebialabs.overthere.cifs.CifsConnectionType.WINRM_HTTP;

import java.net.MalformedURLException;
import java.net.URL;

import com.xebialabs.overthere.CmdLine;
import com.xebialabs.overthere.ConnectionOptions;
import com.xebialabs.overthere.Overthere;
import com.xebialabs.overthere.OverthereProcessOutputHandler;
import com.xebialabs.overthere.cifs.CifsConnection;
import com.xebialabs.overthere.cifs.CifsConnectionType;
import com.xebialabs.overthere.cifs.winrm.connector.JdkHttpConnector;
import com.xebialabs.overthere.cifs.winrm.connector.LaxJdkHttpConnector;
import com.xebialabs.overthere.cifs.winrm.exception.WinRMRuntimeIOException;
import com.xebialabs.overthere.cifs.winrm.tokengenerator.BasicTokenGenerator;

/**
 * A connection to a remote host using CIFS and WinRM.
 * 
 * Limitations:
 * <ul>
 * <li>Shares with names like C$ need to available for all drives accessed. In practice, this means that Administrator access is needed.</li>
 * <li>Can only authenticate with basic authentication to WinRM</li>
 * <li>Not tested with domain accounts.</li>
 * </ul>
 */
public class CifsWinRmConnection extends CifsConnection {

	private final WinRmClient winRmClient;

	/**
	 * Creates a {@link CifsWinRmConnection}. Don't invoke directly. Use {@link Overthere#getConnection(String, ConnectionOptions)} instead.
	 */
	public CifsWinRmConnection(String type, ConnectionOptions options) {
		super(type, options, false);
		checkArgument(os == WINDOWS, "Cannot start a " + CIFS_PROTOCOL + ":%s connection to a non-Windows operating system", cifsConnectionType.toString().toLowerCase());

		TokenGenerator tokenGenerator = getTokenGenerator(options);
		URL targetURL = getTargetURL(options);
		HttpConnector httpConnector = newHttpConnector(cifsConnectionType, targetURL, tokenGenerator);

		winRmClient = new WinRmClient(httpConnector, targetURL);
		winRmClient.setTimeout(options.get(TIMEMOUT, DEFAULT_TIMEOUT));
		winRmClient.setEnvelopSize(options.get(ENVELOP_SIZE, DEFAULT_ENVELOP_SIZE));
		winRmClient.setLocale(options.get(LOCALE, DEFAULT_LOCALE));
	}

	private TokenGenerator getTokenGenerator(ConnectionOptions options) {
		String username = options.get(USERNAME);
		String password = options.get(PASSWORD);
		return new BasicTokenGenerator(username, password);
	}

	private URL getTargetURL(ConnectionOptions options) {
		String scheme = cifsConnectionType == WINRM_HTTP ? "http" : "https";
		String context = options.get(CONTEXT, DEFAULT_WINRM_CONTEXT);
		try {
			return new URL(scheme, address, port, context);
		} catch (MalformedURLException e) {
			throw new WinRMRuntimeIOException("Cannot build a new URL for " + this, e);
		}
	}

	public static HttpConnector newHttpConnector(CifsConnectionType ccType, URL targetURL, TokenGenerator tokenGenerator) {
		switch (ccType) {
			case WINRM_HTTP:
				return new JdkHttpConnector(targetURL, tokenGenerator);
			case WINRM_HTTPS:
				return new LaxJdkHttpConnector(targetURL, tokenGenerator);
		}
		throw new IllegalArgumentException("Invalid CIFS connection type " + ccType);
	}

	@Override
	public int execute(final OverthereProcessOutputHandler handler, final CmdLine commandLine) {
		String cmd = commandLine.toCommandLine(getHostOperatingSystem(), false);
		if(workingDirectory != null) {
			cmd = "CD " + workingDirectory.getPath() + " & " + cmd;
		}
		winRmClient.runCmd(cmd, handler);
		return winRmClient.getExitCode();
	}

}

/* License added by: GRADLE-LICENSE-PLUGIN
 *
 * Copyright 2008-2012 XebiaLabs
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xebialabs.overthere.ssh;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.xebialabs.overthere.ConnectionOptions.ADDRESS;
import static com.xebialabs.overthere.ConnectionOptions.PASSWORD;
import static com.xebialabs.overthere.ConnectionOptions.PORT;
import static com.xebialabs.overthere.ConnectionOptions.USERNAME;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.ALLOCATE_DEFAULT_PTY;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.ALLOCATE_DEFAULT_PTY_DEFAULT;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.ALLOCATE_PTY;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.ALLOCATE_PTY_DEFAULT;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.CONNECTION_TYPE;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.INTERACTIVE_KEYBOARD_AUTH_PROMPT_REGEX;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.INTERACTIVE_KEYBOARD_AUTH_PROMPT_REGEX_DEFAULT;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.PASSPHRASE;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.PRIVATE_KEY_FILE;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.SSH_PORT_DEFAULT;

import java.io.IOException;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.xebialabs.overthere.*;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.common.SSHException;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive;
import net.schmizz.sshj.userauth.method.AuthPassword;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Base class for host connections using SSH.
 */
abstract class SshConnection extends BaseOverthereConnection {

    public static final String PTY_PATTERN = "(\\w+):(\\d+):(\\d+):(\\d+):(\\d+)";

	protected final SshConnectionType sshConnectionType;

    protected final String host;

    protected final int port;

    protected final String username;

    protected final String password;

    protected String interactiveKeyboardAuthPromptRegex;

    protected final String privateKeyFile;

    protected final String passphrase;

    protected final boolean allocateDefaultPty;

    protected final String allocatePty;

    protected SSHClient sshClient;

    private static final Pattern ptyPattern = Pattern.compile(PTY_PATTERN);

	@VisibleForTesting
    protected Factory<SSHClient> sshClientFactory = new Factory<SSHClient>() {
        @Override
        public SSHClient create() {
            return new SSHClient();
        }
    };

	public SshConnection(final String protocol, final ConnectionOptions options) {
        super(protocol, options, true);
        this.sshConnectionType = options.get(CONNECTION_TYPE);
        this.host = options.get(ADDRESS);
        this.port = options.get(PORT, SSH_PORT_DEFAULT);
        this.username = options.get(USERNAME);
        this.password = options.getOptional(PASSWORD);
        this.interactiveKeyboardAuthPromptRegex = options.get(INTERACTIVE_KEYBOARD_AUTH_PROMPT_REGEX, INTERACTIVE_KEYBOARD_AUTH_PROMPT_REGEX_DEFAULT);
        this.privateKeyFile = options.getOptional(PRIVATE_KEY_FILE);
        this.passphrase = options.getOptional(PASSPHRASE);
        this.allocateDefaultPty = options.get(ALLOCATE_DEFAULT_PTY, ALLOCATE_DEFAULT_PTY_DEFAULT);
        this.allocatePty = options.get(ALLOCATE_PTY, ALLOCATE_PTY_DEFAULT);
    }

    protected void connect() {
        try {
            SSHClient client = sshClientFactory.create();
            client.setConnectTimeout(connectionTimeoutMillis);
            client.addHostKeyVerifier(new PromiscuousVerifier());

            try {
                client.connect(host, port);
            } catch (IOException e) {
                throw new RuntimeIOException("Cannot connect to " + host + ":" + port, e);
            }

            if (privateKeyFile != null) {
                if (password != null) {
					logger.warn("The " + PRIVATE_KEY_FILE + " and " + PASSWORD + " connection options have both been set for the connection {}. Ignoring " + PASSWORD
					        + " and using " + PRIVATE_KEY_FILE + ".", this);
                }
                KeyProvider keys;
                try {
                    if (passphrase == null) {
                        keys = client.loadKeys(privateKeyFile);
                    } else {
                        keys = client.loadKeys(privateKeyFile, passphrase);
                    }
                } catch (IOException e) {
                    throw new RuntimeIOException("Cannot read key from private key file " + privateKeyFile, e);
                }
                client.authPublickey(username, keys);
            } else if (password != null) {
	            PasswordFinder passwordFinder = getPasswordFinder();
	            client.auth(username, new AuthPassword(passwordFinder),
			            new AuthKeyboardInteractive(new RegularExpressionPasswordResponseProvider(passwordFinder, interactiveKeyboardAuthPromptRegex)));
            }
            sshClient = client;
        } catch (SSHException e) {
            throw new RuntimeIOException("Cannot connect to " + this, e);
        }
    }

	private PasswordFinder getPasswordFinder() {
		return new PasswordFinder() {

			public char[] reqPassword(Resource<?> resource) {
				return password.toCharArray();
			}

			public boolean shouldRetry(Resource<?> resource) {
				return false;
			}
		};
	}

	@Override
    public void doClose() {
    	checkState(sshClient != null, "Already disconnected");
        try {
            sshClient.disconnect();
        } catch (Exception e) {
        	// Even though we get an exception, we expect the connection to have been closed, so we are ignoring 
            logger.error("Unexpected exception received while disconnecting from " + this, e);
        } finally {
            sshClient = null;
        }
    }

    protected SSHClient getSshClient() {
        checkState(sshClient != null, "Not (yet) connected");
        return sshClient;
    }

    public final OverthereFile getFile(String hostPath) throws RuntimeIOException {
        return getFile(hostPath, false);
    }

    public final OverthereFile getFile(OverthereFile parent, String child) throws RuntimeIOException {
        return getFile(parent, child, false);
    }

    @Override
    protected final OverthereFile getFileForTempFile(OverthereFile parent, String name) {
    	return getFile(parent, name, true);
    }

    protected abstract OverthereFile getFile(String hostPath, boolean isTempFile) throws RuntimeIOException;

    protected OverthereFile getFile(OverthereFile parent, String child, boolean isTempFile) throws RuntimeIOException {
        if (!(parent instanceof SshFile)) {
            throw new IllegalStateException("parent is not a file on an SSH host");
        }
        if (parent.getConnection() != this) {
            throw new IllegalStateException("parent is not a file in this connection");
        }
        return getFile(parent.getPath() + getHostOperatingSystem().getFileSeparator() + child, isTempFile);
    }

    public OverthereProcess startProcess(final CmdLine commandLine) {
		checkNotNull(commandLine, "Cannot execute null command line");
		checkArgument(commandLine.getArguments().size() > 0, "Cannot execute empty command line");

		CmdLine cmd = processCommandLine(commandLine);
        try {
        	Session session = getSshClient().startSession();
        	if(allocatePty != null && !allocatePty.isEmpty()) {
        		if(allocateDefaultPty) {
					logger.warn("The " + ALLOCATE_PTY + " and " + ALLOCATE_DEFAULT_PTY + " connection options have both been set for the connection {}. Ignoring "
					        + ALLOCATE_DEFAULT_PTY + " and using " + ALLOCATE_PTY + ".", this);
        		}
        		Matcher matcher = ptyPattern.matcher(allocatePty);
        		checkArgument(matcher.matches(), "Value for allocatePty [%s] does not match pattern \"" + PTY_PATTERN + "\"", allocateDefaultPty);

        		String term = matcher.group(1);
        		int cols = Integer.valueOf(matcher.group(2));
        		int rows = Integer.valueOf(matcher.group(3));
        		int width = Integer.valueOf(matcher.group(4));
        		int height = Integer.valueOf(matcher.group(5));
        		logger.debug("Allocating PTY {}:{}:{}:{}:{}", new Object[] { term, cols, rows, width, height});
        		session.allocatePTY(term, cols, rows, width, height, Collections.<PTYMode, Integer>emptyMap());
        	} else if(allocateDefaultPty) {
        		logger.debug("Allocating default PTY");
        		session.allocateDefaultPTY();
        	}
			return createProcess(session, cmd);
        } catch (SSHException e) {
            throw new RuntimeIOException("Cannot execute remote command \"" + cmd.toCommandLine(getHostOperatingSystem(), true) + "\" on " + this, e);
        }

    }

    protected CmdLine processCommandLine(final CmdLine commandLine) {
        if(getWorkingDirectory() != null) {
    		CmdLine commandLineWithCd = new CmdLine();
    		commandLineWithCd.addArgument("cd");
    		commandLineWithCd.addArgument(workingDirectory.getPath());
    		addCommandSeparator(commandLineWithCd);
    		for (CmdLineArgument a : commandLine.getArguments()) {
    			commandLineWithCd.add(a);
    		}
    		return commandLineWithCd;
        } else {
        	return commandLine;
        }
    }

	protected void addCommandSeparator(CmdLine commandLine) {
	    commandLine.addRaw(os.getCommandSeparator());
    }

	protected SshProcess createProcess(Session session, CmdLine commandLine) throws TransportException, ConnectionException {
    	return new SshProcess(this, session, commandLine);
    }

    @Override
    public String toString() {
        return "ssh:" + sshConnectionType.toString().toLowerCase() + "://" + username + "@" + host + ":" + port;
    }

    private static Logger logger = LoggerFactory.getLogger(SshConnection.class);

}


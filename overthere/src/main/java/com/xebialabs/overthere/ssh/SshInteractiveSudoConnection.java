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

import com.xebialabs.overthere.CmdLine;
import com.xebialabs.overthere.ConnectionOptions;
import com.xebialabs.overthere.spi.AddressPortMapper;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.SUDO_PASSWORD_PROMPT_REGEX;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.SUDO_PASSWORD_PROMPT_REGEX_DEFAULT;

/**
 * A connection to a Unix host using SSH w/ interactive SUDO.
 */
class SshInteractiveSudoConnection extends SshSudoConnection {

	private String passwordPromptRegex;

	public SshInteractiveSudoConnection(String type, ConnectionOptions options, AddressPortMapper mapper) {
		super(type, options, mapper);
		passwordPromptRegex = options.get(SUDO_PASSWORD_PROMPT_REGEX, SUDO_PASSWORD_PROMPT_REGEX_DEFAULT);
		checkArgument(!passwordPromptRegex.endsWith("*"), SUDO_PASSWORD_PROMPT_REGEX + " should not end in a wildcard");
		checkArgument(!passwordPromptRegex.endsWith("?"), SUDO_PASSWORD_PROMPT_REGEX + " should not end in a wildcard");
		checkArgument(password != null, "Cannot start a ssh:%s: connection without a password", sshConnectionType.toString().toLowerCase());
		if (!allocateDefaultPty && allocatePty == null) {
			logger.warn("SSH Interactive Sudo requires a PTY, allocating a default one.");
			allocateDefaultPty = true;
		}
	}

    @Override
    protected SshProcess createProcess(final Session session, final CmdLine commandLine) throws TransportException, ConnectionException {
        return new SshProcess(this, os, session, commandLine) {
            @Override
            public InputStream getStdout() {
                return new SshInteractiveSudoPasswordHandlingStream(super.getStdout(), getStdin(), password, passwordPromptRegex);
            }
        };
    }
	
	private static final Logger logger = LoggerFactory.getLogger(SshInteractiveSudoConnection.class);
}


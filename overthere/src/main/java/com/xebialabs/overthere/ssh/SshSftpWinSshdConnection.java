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
import static com.xebialabs.overthere.OperatingSystemFamily.WINDOWS;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.SSH_PROTOCOL;
import static java.lang.Character.toUpperCase;

import com.xebialabs.overthere.spi.AddressPortMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xebialabs.overthere.ConnectionOptions;
import com.xebialabs.overthere.RuntimeIOException;

/**
 * A connection to a Windows host running WinSSHD.
 */
class SshSftpWinSshdConnection extends SshSftpConnection {

	public SshSftpWinSshdConnection(String type, ConnectionOptions options, AddressPortMapper mapper) {
	    super(type, options, mapper);
		checkArgument(os == WINDOWS, "Cannot start a " + SSH_PROTOCOL + ":%s connection to a non-Windows operating system", sshConnectionType.toString().toLowerCase());
    }

	@Override
    protected String pathToSftpPath(String path) {
		String translatedPath;
		if(path.length() >= 2 && path.charAt(1) == ':') {
			char driveLetter = toUpperCase(path.charAt(0));
			String pathInDrive = path.substring(2).replace('\\', '/');
			translatedPath = "/" + driveLetter + pathInDrive;
		} else {
			throw new RuntimeIOException("Cannot translate path " + path + " because it is not a Windows path");
		}
		logger.trace("Translated path {} to sftp path {}", path, translatedPath);
		return translatedPath;
    }

	private Logger logger = LoggerFactory.getLogger(SshSftpWinSshdConnection.class);

}


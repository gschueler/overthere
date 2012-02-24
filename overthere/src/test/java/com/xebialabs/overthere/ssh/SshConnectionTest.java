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

import static com.xebialabs.overthere.ConnectionOptions.ADDRESS;
import static com.xebialabs.overthere.ConnectionOptions.OPERATING_SYSTEM;
import static com.xebialabs.overthere.ConnectionOptions.PASSWORD;
import static com.xebialabs.overthere.ConnectionOptions.USERNAME;
import static com.xebialabs.overthere.OperatingSystemFamily.UNIX;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.ALLOCATE_DEFAULT_PTY;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.ALLOCATE_PTY;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.CONNECTION_TYPE;
import static com.xebialabs.overthere.ssh.SshConnectionBuilder.PRIVATE_KEY_FILE;
import static com.xebialabs.overthere.ssh.SshConnectionType.SFTP;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import net.schmizz.sshj.MockitoFriendlySSHClient;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.method.AuthMethod;

import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.xebialabs.overthere.CmdLine;
import com.xebialabs.overthere.ConnectionOptions;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for {@link SshConnection}
 */
public class SshConnectionTest {

	@Mock MockitoFriendlySSHClient client;

	private ConnectionOptions connectionOptions;

    @BeforeMethod
    public void init() {
	    MockitoAnnotations.initMocks(this);
        connectionOptions = new ConnectionOptions();
        connectionOptions.set(CONNECTION_TYPE, SFTP);
        connectionOptions.set(OPERATING_SYSTEM, UNIX);
        connectionOptions.set(ADDRESS, "nowhere.example.com");
        connectionOptions.set(USERNAME, "some-user");
    }

    @Test
    public void shouldUsePasswordIfNoKeyfile() throws IOException {
        String password = "secret";
        connectionOptions.set(PASSWORD, password);
        newConnectionWithClient(client).connect();
        
        verify(client).auth(eq("some-user"), isA(AuthMethod.class), isA(AuthMethod.class));
    }

    @Test
    public void shouldUseKeyfileIfNoPassword() throws IOException {
        connectionOptions.set(PRIVATE_KEY_FILE, "/path/to/keyfile");
        newConnectionWithClient(client).connect();
        
        verify(client).authPublickey(eq("some-user"), Matchers.<KeyProvider>anyVararg());
    }
    
    @Test
    public void keyfileShouldOverridePassword() throws IOException {
        String password = "secret";
        connectionOptions.set(PASSWORD, password);
        connectionOptions.set(PRIVATE_KEY_FILE, "/path/to/keyfile");
        newConnectionWithClient(client).connect();
        
        verify(client).authPublickey(eq("some-user"), Matchers.<KeyProvider>anyVararg());
        verify(client, never()).authPassword(anyString(), anyString());
    }

	@Test
	public void shouldNotAllocateDefaultPty() throws IOException {
		Session session = mock(Session.class);
		when(client.startSession()).thenReturn(session);
		connectionOptions.set(ALLOCATE_DEFAULT_PTY, false);

		SshConnection connection = newConnectionWithClient(client);
		connection.connect();
		connection.startProcess(CmdLine.build("dummy"));

		verify(session, times(0)).allocateDefaultPTY();
	}

	@Test
	public void shouldAllocateDefaultPty() throws IOException {
		Session session = mock(Session.class);
		when(client.startSession()).thenReturn(session);
		connectionOptions.set(ALLOCATE_DEFAULT_PTY, true);

		SshConnection connection = newConnectionWithClient(client);
		connection.connect();
		connection.startProcess(CmdLine.build("dummy"));

		verify(session).allocateDefaultPTY();
	}

	@SuppressWarnings("unchecked")
    @Test
	public void shouldAllocatePty() throws IOException {
		Session session = mock(Session.class);
		when(client.startSession()).thenReturn(session);
		connectionOptions.set(ALLOCATE_PTY, "xterm:132:50:264:100");

		SshConnection connection = newConnectionWithClient(client);
		connection.connect();
		connection.startProcess(CmdLine.build("dummy"));

		verify(session).allocatePTY(eq("xterm"), eq(132), eq(50), eq(264), eq(100), anyMap());
	}

	@SuppressWarnings("unchecked")
    @Test
	public void allocatePtyShouldOverrideAllocateDefaultPty() throws IOException {
		Session session = mock(Session.class);
		when(client.startSession()).thenReturn(session);
		connectionOptions.set(ALLOCATE_PTY, "xterm:132:50:264:100");
		connectionOptions.set(ALLOCATE_DEFAULT_PTY, true);

		SshConnection connection = newConnectionWithClient(client);
		connection.connect();
		connection.startProcess(CmdLine.build("dummy"));

		verify(session).allocatePTY(eq("xterm"), eq(132), eq(50), eq(264), eq(100), anyMap());
		verify(session, times(0)).allocateDefaultPTY();
	}

    private SshConnection newConnectionWithClient(SSHClient client) {
        return new PresetClientSshConnection(connectionOptions, client);
    }
    
}


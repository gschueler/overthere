package com.xebialabs.overthere.winrm;

import org.junit.After;
import org.junit.Before;

import static com.xebialabs.overthere.ConnectionOptions.*;

public class WinRMHttpsLazyKerberosItest extends WinRMItestBase {

	@Override
	protected void setTypeAndOptions() throws Exception {
		super.setTypeAndOptions();
		options.set(USERNAME, DEFAULT_USERNAME);
		options.set(PASSWORD, DEFAULT_PASSWORD);
		options.set(PORT, CifsWinRMConnectionBuilder.DEFAULT_HTTPS_PORT);
		options.set(CifsWinRMConnectionBuilder.PROTOCOL, Protocol.HTTPS_LAZY);
		options.set(CifsWinRMConnectionBuilder.AUTHENTICATION, AuthenticationMode.KERBEROS);
	}

	@Before
	public void setup() {
		System.setProperty("java.security.krb5.conf", KRB5_CONF);
		System.setProperty("java.security.auth.login.config", LOGIN_CONF);
		System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
	}

	@After
	public void tearDown() {
		System.setProperty("java.security.krb5.conf", "");
		System.setProperty("java.security.auth.login.config", "");
		System.setProperty("javax.security.auth.useSubjectCredsOnly", "");
	}


}

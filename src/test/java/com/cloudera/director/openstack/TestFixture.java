/*
 * Copyright (c) 2015 Intel Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.director.openstack;

import java.io.IOException;
import java.nio.charset.Charset;

public final class TestFixture {
	
	private String keystoneEndpoint;
	private String tenantName;
	private String userName;
	private String password;
	private String sshPublicKey;
	private String sshUserName;
	
	private TestFixture(
			String keystoneEndpoint, String tenantName, String userName, String password, 
			String sshPublicKey, String sshUserName) {
		this.keystoneEndpoint = keystoneEndpoint;
		this.tenantName = tenantName;
		this.userName =  userName;
		this.password = password;
		this.sshPublicKey = sshPublicKey;
		this.sshUserName = sshUserName;
	}
	
	public static TestFixture newTestFixture(boolean sshPublicKeyAndsshUserNameAreRequired) throws IOException {
		String keystoneEndpoint = TestUtils.readRequiredSystemProperty("KEYSTONE_ENDPOINT");
		String tenantName = TestUtils.readRequiredSystemProperty("TENANT_NAME");
		String userName = TestUtils.readRequiredSystemProperty("USER_NAME");
		String password = TestUtils.readRequiredSystemProperty("PASSWORD");
		String sshPublicKey = null;
		String sshUserName = null;
		
		if (sshPublicKeyAndsshUserNameAreRequired) {
			sshPublicKey = TestUtils.readFile(TestUtils.readRequiredSystemProperty("SSH_PUBLIC_KEY_PATH"),
					Charset.defaultCharset());
			sshUserName = TestUtils.readRequiredSystemProperty("SSH_USER_NAME");
		}
		
		return new TestFixture(keystoneEndpoint, tenantName, userName,  password, 
				sshPublicKey, sshUserName);
		
	}

	public String getKeystoneEndpoint() {
		return keystoneEndpoint;
	}

	public String getTenantName() {
		return tenantName;
	}

	public String getUserName() {
		return userName;
	}

	public String getPassword() {
		return password;
	}

	public String getSshPublicKey() {
		return sshPublicKey;
	}

	public String getSshUserName() {
		return sshUserName;
	}
}


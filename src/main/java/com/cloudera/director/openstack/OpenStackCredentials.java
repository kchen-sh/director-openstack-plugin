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

import com.google.common.base.Preconditions;

public class OpenStackCredentials {

	private final String endpoint;
	private final String identity;
	private final String credential;

	public OpenStackCredentials(String endpoint, String tenant, String user, String credential) {
		
		this.endpoint = Preconditions.checkNotNull(endpoint, "endpoint is null");
		
		Preconditions.checkNotNull(tenant, "tenant is null");
		Preconditions.checkNotNull(user, "user is null");
		this.identity = tenant + ":" + user;
		this.credential = Preconditions.checkNotNull(credential, "credential is null");
	}
	
	public String getEndpoint() {
		return endpoint;
	}
	
	public String getIdentity() {
		return identity;
	}
	
	public String getCredential() {
		return credential;
	}
	
	public boolean equals(OpenStackCredentials cre) {
		return endpoint.equals(cre.getEndpoint()) && 
			   identity.equals(cre.getIdentity()) &&
			   credential.equals(cre.getCredential());
	}
}

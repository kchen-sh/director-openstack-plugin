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

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;


/**
 * An enum of properties required for building credentials.
 */
public enum OpenStackCredentialsProviderConfigurationProperty implements ConfigurationPropertyToken{
	
	KEYSTONE_ENDPOINT(new SimpleConfigurationPropertyBuilder()
		  .configKey("keystoneEndpoint")
		  .name("Keystone Endpoint")
		  .defaultDescription("Endpoint of openstack keystone.")
		  .defaultErrorMessage("OpenStack credentials configuration is missing the keystone endpoint.")
		  .required(true)
		  .build()),
	TENANT_NAME(new SimpleConfigurationPropertyBuilder()
		  .configKey("tenantName")
		  .name("OpenStack Tenant Name")
		  .defaultDescription("Tenant Name of openstack.")
		  .defaultErrorMessage("OpenStack credentials configuration is missing the tenant name.")
		  .required(true)
		  .build()),
	USER_NAME(new SimpleConfigurationPropertyBuilder()
		  .configKey("userName")
		  .name("OpenStack User Name")
		  .defaultDescription("Username of openstack.")
		  .defaultErrorMessage("OpenStack credentials configuration is missing the username.")
		  .required(true)
		  .build()),
	PASSWORD(new SimpleConfigurationPropertyBuilder()
		  .configKey("password")
		  .name("OpenStack Password")
		  .defaultDescription("Password of openstack.")
		  .defaultErrorMessage("OpenStack credentials configuration is missing the password.")
		  .required(true)
		  .build());
	
	/**
	 * The configuration property.
	 */
	private final ConfigurationProperty configurationProperty;

	/**
	 * Creates a configuration property token with the specified parameters.
	 *
	 * @param configurationProperty the configuration property
	 */
	private OpenStackCredentialsProviderConfigurationProperty(
			ConfigurationProperty configurationProperty) {
		this.configurationProperty = configurationProperty;
	}

	@Override
	public ConfigurationProperty unwrap() {
		return configurationProperty;
	}

}


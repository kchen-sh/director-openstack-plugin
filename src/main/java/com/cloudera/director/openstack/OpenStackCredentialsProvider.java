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

import java.util.List;

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.provider.CredentialsProvider;
import com.cloudera.director.spi.v1.provider.CredentialsProviderMetadata;
import com.cloudera.director.spi.v1.provider.util.SimpleCredentialsProviderMetadata;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;

import static com.cloudera.director.openstack.OpenStackCredentialsProviderConfigurationProperty.KEYSTONE_ENDPOINT;
import static com.cloudera.director.openstack.OpenStackCredentialsProviderConfigurationProperty.TENANT_NAME;
import static com.cloudera.director.openstack.OpenStackCredentialsProviderConfigurationProperty.USER_NAME;
import static com.cloudera.director.openstack.OpenStackCredentialsProviderConfigurationProperty.PASSWORD;

public class OpenStackCredentialsProvider implements CredentialsProvider<OpenStackCredentials> {

	private static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES =
			ConfigurationPropertiesUtil.asConfigurationPropertyList(
					OpenStackCredentialsProviderConfigurationProperty.values());

	public static CredentialsProviderMetadata METADATA =
			new SimpleCredentialsProviderMetadata(CONFIGURATION_PROPERTIES);
	
	@Override
	public CredentialsProviderMetadata getMetadata() {
		return METADATA;
	}

	@Override
	public OpenStackCredentials createCredentials(Configured configuration,
			LocalizationContext localizationContext) {	
		return new OpenStackCredentials(
			configuration.getConfigurationValue(KEYSTONE_ENDPOINT, localizationContext),
			configuration.getConfigurationValue(TENANT_NAME, localizationContext),
			configuration.getConfigurationValue(USER_NAME, localizationContext),
			configuration.getConfigurationValue(PASSWORD, localizationContext));
	}

}


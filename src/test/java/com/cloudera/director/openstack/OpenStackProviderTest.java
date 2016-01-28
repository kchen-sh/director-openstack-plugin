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

import static com.cloudera.director.openstack.OpenStackCredentialsProviderConfigurationProperty.KEYSTONE_ENDPOINT;
import static com.cloudera.director.openstack.OpenStackCredentialsProviderConfigurationProperty.PASSWORD;
import static com.cloudera.director.openstack.OpenStackCredentialsProviderConfigurationProperty.TENANT_NAME;
import static com.cloudera.director.openstack.OpenStackCredentialsProviderConfigurationProperty.USER_NAME;
import static com.cloudera.director.openstack.nova.NovaProviderConfigurationProperty.REGION;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.cloudera.director.openstack.nova.NovaProvider;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.CredentialsProviderMetadata;
import com.cloudera.director.spi.v1.provider.ResourceProvider;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.typesafe.config.Config;

/**
 * Performs 'live' test of {@link OpenStackProvider}
 * 
 * This system property is required: KEYSTONE_ENDPOINT
 * This system property is required: TENANT_NAME
 * This system property is required: USER_NAME
 * This system property is required: PASSWORD
 *
 */
public class OpenStackProviderTest {
	
	private static TestFixture testFixture;
	
	@BeforeClass
	public static void beforeClass() throws IOException {
		testFixture = TestFixture.newTestFixture(false);
	}
	
	@Rule
	public TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();
	
	
	@Test
	public void testProvider() throws IOException {
		CloudProviderMetadata openstackProviderMetadata = OpenStackProvider.METADATA;
		
		CredentialsProviderMetadata credentialsProviderMetadata = 
				openstackProviderMetadata.getCredentialsProviderMetadata();
		List<ConfigurationProperty> credentialsConfigurationProperties = 
				credentialsProviderMetadata.getCredentialsConfigurationProperties();
		assertTrue(credentialsConfigurationProperties.contains(KEYSTONE_ENDPOINT.unwrap()));
		assertTrue(credentialsConfigurationProperties.contains(TENANT_NAME.unwrap()));
		assertTrue(credentialsConfigurationProperties.contains(USER_NAME.unwrap()));
		assertTrue(credentialsConfigurationProperties.contains(PASSWORD.unwrap()));
		
		Config openstackConfig = TestUtils.buildOpenStackConfig(Configurations.CONFIGURATION_FILE_NAME);
		OpenStackCredentialsProvider openstackCredentialsProvider = new OpenStackCredentialsProvider();
		assertNotNull(openstackCredentialsProvider);
		
		Map<String, String> environmentConfig = new HashMap<String, String>();
		environmentConfig.put(KEYSTONE_ENDPOINT.unwrap().getConfigKey(), testFixture.getKeystoneEndpoint());
		environmentConfig.put(TENANT_NAME.unwrap().getConfigKey(), testFixture.getTenantName());
		environmentConfig.put(USER_NAME.unwrap().getConfigKey(), testFixture.getUserName());
		environmentConfig.put(PASSWORD.unwrap().getConfigKey(), testFixture.getPassword());
		environmentConfig.put(REGION.unwrap().getConfigKey(), testFixture.getRegion());

		LocalizationContext localizationContext = DefaultLocalizationContext.FACTORY.createRootLocalizationContext(Locale.getDefault());

		OpenStackCredentials openstackCredentials = openstackCredentialsProvider.createCredentials(
				new SimpleConfiguration(environmentConfig), localizationContext);
		assertNotNull(openstackCredentials);
		OpenStackProvider openstackProvider = new OpenStackProvider(new SimpleConfiguration(environmentConfig), openstackConfig, localizationContext);
		assertNotNull(openstackProvider);
		assertSame(openstackProviderMetadata, openstackProvider.getProviderMetadata());
		
		ResourceProviderMetadata computeResourceProviderMetadata = null;
		List<ResourceProviderMetadata> resourceProviderMetadatas = openstackProviderMetadata.getResourceProviderMetadata();
		
		for (ResourceProviderMetadata resourceProviderMetadata : resourceProviderMetadatas) {
			String resourceProviderID = resourceProviderMetadata.getId();
			if (NovaProvider.METADATA.getId().equals(resourceProviderID)) {
				computeResourceProviderMetadata = resourceProviderMetadata;
			}
			else {
				throw new IllegalArgumentException("Unexcepted resource provider: " + resourceProviderID);
				
			}
			
			assertNotNull(computeResourceProviderMetadata);
		}
		
		ResourceProvider<?,?> computeResourceProvider =
				openstackProvider.createResourceProvider(NovaProvider.ID,
						new SimpleConfiguration(environmentConfig));
		assertEquals(NovaProvider.class, computeResourceProvider.getClass());;
		
	}

}


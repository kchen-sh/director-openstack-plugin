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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.cloudera.director.spi.v1.provider.CloudProvider;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.Launcher;

/**
 * Performs 'live' test of ({@link OpenStackLauncher}
 * 
 * This system property is required: KEYSTONE_ENDPOINT
 * This system property is required: TENANT_NAME
 * This system property is required: USER_NAME
 * This system property is required: PASSWORD
 * 
 */
public class OpenStackLauncherTest {
	
	private static TestFixture testFixture;
	
	@BeforeClass
	public static void beforeClass() throws IOException {
		testFixture = TestFixture.newTestFixture(false);
	}
	
	@Rule
	public TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();
	
	@Test
	public void testLauncher() throws InterruptedException, IOException {
		Launcher launcher = new OpenStackLauncher();
		launcher.initialize(TEMPORARY_FOLDER.getRoot(), null);
		
		assertEquals(1, launcher.getCloudProviderMetadata().size());
		CloudProviderMetadata metadata = launcher.getCloudProviderMetadata().get(0);
		
		assertEquals(OpenStackProvider.ID, metadata.getId());
		
		List<ConfigurationProperty> credentialsConfigurationProperties = 
				metadata.getCredentialsProviderMetadata().getCredentialsConfigurationProperties();
		assertEquals(4, credentialsConfigurationProperties.size());
		assertTrue(credentialsConfigurationProperties.contains(KEYSTONE_ENDPOINT.unwrap()));
		assertTrue(credentialsConfigurationProperties.contains(TENANT_NAME.unwrap()));
		assertTrue(credentialsConfigurationProperties.contains(USER_NAME.unwrap()));
		assertTrue(credentialsConfigurationProperties.contains(PASSWORD.unwrap()));
		
		//In order to create a cloud provider we need to configure credentials
		// (We except them to be eagerly validated on cloud provider creation).
		Map<String, String> environmentConfig = new HashMap<String, String>();
		environmentConfig.put(KEYSTONE_ENDPOINT.unwrap().getConfigKey(), testFixture.getKeystoneEndpoint());
		environmentConfig.put(TENANT_NAME.unwrap().getConfigKey(), testFixture.getTenantName());
		environmentConfig.put(USER_NAME.unwrap().getConfigKey(), testFixture.getUserName());
		environmentConfig.put(PASSWORD.unwrap().getConfigKey(), testFixture.getPassword());
		 CloudProvider cloudProvider = launcher.createCloudProvider(
				OpenStackProvider.ID,
				new SimpleConfiguration(environmentConfig),
				Locale.getDefault());
		
		assertEquals(OpenStackProvider.class, cloudProvider.getClass());
		
		CloudProvider cloudProvider2 = launcher.createCloudProvider(
				OpenStackProvider.ID,
				new SimpleConfiguration(environmentConfig),
				Locale.getDefault());
		
		assertNotSame(cloudProvider, cloudProvider2);
	}
	
	@Test
	public void testLauncherConfig() throws InterruptedException, IOException {
		OpenStackLauncher launcher = new OpenStackLauncher();
		File configDir = TEMPORARY_FOLDER.getRoot();
		File configFile = new File(configDir, Configurations.CONFIGURATION_FILE_NAME);
		PrintWriter printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(configFile), "UTF-8")));
		
		printWriter.println("openstack {");
		printWriter.println("	NovaProvider {");
		printWriter.println("		resourceConfigs {");
		printWriter.println("			 type : 5");
		printWriter.println("						}");
		printWriter.println("				}");
		printWriter.println("		  }");
		printWriter.close();
		launcher.initialize(configDir, null);
		
		//Verify that base config is reflected.
		assertEquals(5, launcher.config.getInt(Configurations.INSTANCE_FLAVOR_ID_SECTION + "type"));
		
	}

}

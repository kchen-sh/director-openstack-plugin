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
package com.cloudera.director.openstack.trove;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.util.Lists;
import org.jclouds.openstack.trove.v1.TroveApi;
import org.jclouds.openstack.trove.v1.domain.Flavor;
import org.jclouds.openstack.trove.v1.domain.Instance;
import org.jclouds.openstack.trove.v1.domain.Instance.Status;
import org.jclouds.openstack.trove.v1.features.InstanceApi;
import org.junit.Before;
import org.junit.Test;

import com.cloudera.director.openstack.OpenStackCredentials;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.exception.UnrecoverableProviderException;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;

import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.MASTER_USER_PASSWORD;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.MASTER_USER_NAME;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.FLAVOR_ID;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.VOLUME_SIZE;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.ENGINE;
import static com.cloudera.director.openstack.trove.TroveProviderConfigurationProperty.REGION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

public class TroveProviderTest {
	
	private static final DefaultLocalizationContext DEFAULT_LOCALIZATION_CONTEXT = new DefaultLocalizationContext(Locale.getDefault(), "");
	
	private static final String TEST_REGION_NAME = "RegionOne";
	private static final String TEST_FLAVOR_ID = "3";
	private static final String TEST_VOLUME_SIZE = "1";
	private static final String TEST_MASTER_USER_NAME = "root";
	private static final String TEST_MASTER_USER_PASSWORD = "root";
	private static final String TEST_ENGINE = "MYSQL";
	
	private TroveProvider troveProvider;
	private TroveApi troveApi;
	private InstanceApi instanceApi;
	private OpenStackCredentials credentials;
	
	@Before
	public void setUp() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		credentials = mock(OpenStackCredentials.class);
		troveApi = mock(TroveApi.class);
		when(credentials.buildTroveApi()).thenReturn(troveApi);
		instanceApi = mock(InstanceApi.class);
		when(troveApi.getInstanceApi(TEST_REGION_NAME)).thenReturn(instanceApi);
		
		Map<String, String> sqlAdminConfig = new HashMap<String, String>();
		sqlAdminConfig.put(REGION.unwrap().getConfigKey(), TEST_REGION_NAME);
		Configured resourceProviderConfiguration = new SimpleConfiguration(sqlAdminConfig);
		troveProvider = new TroveProvider(resourceProviderConfiguration, credentials, DEFAULT_LOCALIZATION_CONTEXT);
	}
	
	private Instance mockInstance(String flavorId, int volumeSize, String instanceName, String instanceNameSuffix, Status status) {
		Instance instance = mock(Instance.class);
		Flavor flavor = mock(Flavor.class);
//		FluentIterable<Instance> instances = mock(FluentIterable.class);
//		mockIterable(instances, instance);
		when(instanceApi.create(flavorId, volumeSize, instanceName)).thenReturn(instance);
		when(instanceApi.get(instanceNameSuffix)).thenReturn(instance);
		when(instanceApi.enableRoot(instanceNameSuffix)).thenReturn(TEST_MASTER_USER_PASSWORD);
//		when(instanceApi.list()).thenReturn(instances);
		when(instance.getName()).thenReturn(instanceName);
		when(instance.getSize()).thenReturn(volumeSize);
		when(instance.getId()).thenReturn(instanceNameSuffix);
		when(instance.getFlavor()).thenReturn(flavor);
		when(instance.getStatus()).thenReturn(status);
		when(flavor.getId()).thenReturn(Integer.parseInt(flavorId));
		return instance;
	}
	
	private Configured prepareTemplateConfig() {
		
		//Prepare resource template
		Map<String, String> templateConfig = new HashMap<String, String>();
		templateConfig.put(FLAVOR_ID.unwrap().getConfigKey(), TEST_FLAVOR_ID);
		templateConfig.put(VOLUME_SIZE.unwrap().getConfigKey(), TEST_VOLUME_SIZE);
		templateConfig.put(ENGINE.unwrap().getConfigKey(), TEST_ENGINE);
		templateConfig.put(MASTER_USER_NAME.unwrap().getConfigKey(), TEST_MASTER_USER_NAME);
		templateConfig.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), TEST_MASTER_USER_PASSWORD);
		Configured configuration = new SimpleConfiguration(templateConfig);
		
		return configuration;
	}
	
	@Test
	public void testAllocate_standard() throws InterruptedException {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		
		String instanceNameSuffix = UUID.randomUUID().toString();
		String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix;
		Instance instanceCreated = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName, instanceNameSuffix, Status.ACTIVE);
		troveProvider.allocate(template, Lists.newArrayList(instanceNameSuffix), 1);
		
		verify(instanceCreated, times(1)).getId();
		verify(instanceCreated, times(1)).getStatus();
		verify(instanceApi, times(0)).delete(instanceNameSuffix);
		assertThat(instanceCreated.getName()).isEqualTo(decoratedInstanceName);
		assertThat(instanceCreated.getId()).isEqualTo(instanceNameSuffix);
		assertThat(instanceCreated.getSize()).isEqualTo(Integer.parseInt(TEST_VOLUME_SIZE));
		assertThat(instanceCreated.getFlavor().getId()).isEqualTo(Integer.parseInt(TEST_FLAVOR_ID));
		assertThat(instanceCreated.getStatus()).isEqualTo(Status.ACTIVE);
			
	}
	
	@Test
	public void testAllocate_Pending() throws InterruptedException {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		
		String instanceNameSuffix = UUID.randomUUID().toString();
		String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix;
		Instance instanceCreated = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName, instanceNameSuffix, Status.BUILD);
		try {
			troveProvider.allocate(template, Lists.newArrayList(instanceNameSuffix), 1);
		} catch (UnrecoverableProviderException e) {
			assertThat(e.getMessage()).isEqualTo("Problem allocating instances.");
		}
		
		verify(instanceCreated, times(1)).getId();
		verify(instanceApi, times(1)).delete(instanceNameSuffix);
		assertThat(instanceCreated.getName()).isEqualTo(decoratedInstanceName);
		assertThat(instanceCreated.getId()).isEqualTo(instanceNameSuffix);
		assertThat(instanceCreated.getSize()).isEqualTo(Integer.parseInt(TEST_VOLUME_SIZE));
		assertThat(instanceCreated.getFlavor().getId()).isEqualTo(Integer.parseInt(TEST_FLAVOR_ID));
		assertThat(instanceCreated.getStatus()).isEqualTo(Status.BUILD);		
	}
	
	@Test
	public void testAllocate_BelowMinCount() throws InterruptedException {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		
		String instanceNameSuffix = UUID.randomUUID().toString();
		String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix;
		Instance instanceCreated = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName, instanceNameSuffix, Status.ACTIVE);
		try {
			troveProvider.allocate(template, Lists.newArrayList(instanceNameSuffix), 2);
		} catch (UnrecoverableProviderException e) {
			assertThat(e.getMessage()).isEqualTo("Problem allocating instances.");
		}
		verify(instanceCreated, times(1)).getId();
		verify(instanceApi, times(1)).delete(instanceNameSuffix);
		assertThat(instanceCreated.getName()).isEqualTo(decoratedInstanceName);
		assertThat(instanceCreated.getId()).isEqualTo(instanceNameSuffix);
		assertThat(instanceCreated.getSize()).isEqualTo(Integer.parseInt(TEST_VOLUME_SIZE));
		assertThat(instanceCreated.getFlavor().getId()).isEqualTo(Integer.parseInt(TEST_FLAVOR_ID));
		assertThat(instanceCreated.getStatus()).isEqualTo(Status.ACTIVE);		
	}
	
	@Test
	public void testFind() throws InterruptedException, UnknownHostException {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		String instanceNameSuffix1 = UUID.randomUUID().toString();
		String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix1;
		String instanceNameSuffix2 = UUID.randomUUID().toString();
		String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix2;		
		Instance instanceCreated1 = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName1, instanceNameSuffix1, Status.ACTIVE);
		Instance instanceCreated2 = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName2, instanceNameSuffix2, Status.ACTIVE);
		when(instanceCreated1.getName()).thenReturn(InetAddress.getLocalHost().getHostName());
		when(instanceCreated2.getName()).thenReturn(InetAddress.getLocalHost().getHostName());
		Collection<TroveInstance> existedInstances = null;
		existedInstances = troveProvider.find(template, Lists.newArrayList(instanceNameSuffix1, instanceNameSuffix2));
		
		assertThat(existedInstances.size()).isEqualTo(2);
		Iterator<TroveInstance> iterator = existedInstances.iterator();
		TroveInstance troveInstance1 = iterator.next();
		assertThat(troveInstance1.getId()).isEqualTo(instanceNameSuffix1);
		TroveInstance troveInstance2 = iterator.next();
		assertThat(troveInstance2.getId()).isEqualTo(instanceNameSuffix2);		
	}
	
	@Test
	public void testFind_PartialSuccess() throws InterruptedException, UnknownHostException {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		String instanceNameSuffix1 = UUID.randomUUID().toString();
		String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix1;
		String instanceNameSuffix2 = UUID.randomUUID().toString();
		String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix2;		
		Instance instanceCreated1 = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName1, instanceNameSuffix1, Status.BUILD);
		Instance instanceCreated2 = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName2, instanceNameSuffix2, Status.ACTIVE);
		when(instanceCreated1.getName()).thenReturn(InetAddress.getLocalHost().getHostName());
		when(instanceCreated2.getName()).thenReturn(InetAddress.getLocalHost().getHostName());
		Collection<TroveInstance> existedInstances = null;
		existedInstances = troveProvider.find(template, Lists.newArrayList(instanceNameSuffix1, instanceNameSuffix2));
		
		assertThat(existedInstances.size()).isEqualTo(1);
		Iterator<TroveInstance> iterator = existedInstances.iterator();
		TroveInstance troveInstance2 = iterator.next();
		assertThat(troveInstance2.getId()).isEqualTo(instanceNameSuffix2);		
	}
	
	@Test
	public void testFind_InvalidPrivateIp() throws InterruptedException, UnknownHostException {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		String instanceNameSuffix = UUID.randomUUID().toString();
		String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix;
		Instance instanceCreated = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName, instanceNameSuffix, Status.ACTIVE);
		Collection<TroveInstance> existedInstances = null;
		try {
			existedInstances = troveProvider.find(template, Lists.newArrayList(instanceNameSuffix));	
		} catch(IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("Invalid private IP address");
		}
		
	}
	
	@Test
	public void testDelete() throws InterruptedException {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		String instanceNameSuffix = UUID.randomUUID().toString();
		String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix;
		Instance instanceCreated = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName, instanceNameSuffix, Status.ACTIVE);
	
		troveProvider.delete(template, Lists.newArrayList(instanceNameSuffix));
		verify(instanceApi, times(1)).delete(instanceNameSuffix);
	}
	
	@Test
	public void testGetInstanceState() {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		String instanceNameSuffix1 = UUID.randomUUID().toString();
		String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix1;
		String instanceNameSuffix2 = UUID.randomUUID().toString();
		String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix2;		
		Instance instanceCreated1 = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName1, instanceNameSuffix1, Status.ACTIVE);
		Instance instanceCreated2 = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName2, instanceNameSuffix2, Status.ACTIVE);		
	
		Map<String, InstanceState> instanceStates = troveProvider.getInstanceState(template, Lists.newArrayList(instanceNameSuffix1, instanceNameSuffix2));
		assertThat(instanceStates.size()).isEqualTo(2);
		assertThat(instanceStates.get(instanceNameSuffix1).getInstanceStatus()).isEqualTo(InstanceStatus.RUNNING);
		assertThat(instanceStates.get(instanceNameSuffix2).getInstanceStatus()).isEqualTo(InstanceStatus.RUNNING);
	}
	
	@Test
	public void testGetInstanceState_PartialSuccess() {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		String instanceNameSuffix1 = UUID.randomUUID().toString();
		String decoratedInstanceName1 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix1;
		String instanceNameSuffix2 = UUID.randomUUID().toString();
		String decoratedInstanceName2 = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix2;		
		Instance instanceCreated1 = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName1, instanceNameSuffix1, Status.BUILD);
		Instance instanceCreated2 = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName2, instanceNameSuffix2, Status.ACTIVE);		
	
		Map<String, InstanceState> instanceStates = troveProvider.getInstanceState(template, Lists.newArrayList(instanceNameSuffix1, instanceNameSuffix2));
		assertThat(instanceStates.size()).isEqualTo(2);
		assertThat(instanceStates.get(instanceNameSuffix1).getInstanceStatus()).isEqualTo(InstanceStatus.PENDING);
		assertThat(instanceStates.get(instanceNameSuffix2).getInstanceStatus()).isEqualTo(InstanceStatus.RUNNING);
	}
	
	@Test
	public void testFullCycle() throws InterruptedException, UnknownHostException {
		Configured configuration = prepareTemplateConfig();
		TroveInstanceTemplate template = troveProvider.createResourceTemplate("trove-template", configuration, new HashMap<String, String>());
		String instanceNameSuffix = UUID.randomUUID().toString();
		String decoratedInstanceName = INSTANCE_NAME_PREFIX.unwrap().getDefaultValue() + "-" + instanceNameSuffix;
		Instance instanceCreated = mockInstance(TEST_FLAVOR_ID, Integer.parseInt(TEST_VOLUME_SIZE), decoratedInstanceName, instanceNameSuffix, Status.ACTIVE);

		troveProvider.allocate(template, Lists.newArrayList(instanceNameSuffix), 1);
		verify(instanceCreated, times(1)).getId();
		verify(instanceCreated, times(1)).getStatus();
		verify(instanceApi, times(0)).delete(instanceNameSuffix);
		assertThat(instanceCreated.getName()).isEqualTo(decoratedInstanceName);
		assertThat(instanceCreated.getId()).isEqualTo(instanceNameSuffix);
		assertThat(instanceCreated.getSize()).isEqualTo(Integer.parseInt(TEST_VOLUME_SIZE));
		assertThat(instanceCreated.getFlavor().getId()).isEqualTo(Integer.parseInt(TEST_FLAVOR_ID));
		assertThat(instanceCreated.getStatus()).isEqualTo(Status.ACTIVE);
		
		when(instanceCreated.getName()).thenReturn(InetAddress.getLocalHost().getHostName());
		Collection<TroveInstance> existedInstances = troveProvider.find(template, Lists.newArrayList(instanceNameSuffix));
		assertThat(existedInstances.size()).isEqualTo(1);
		Iterator<TroveInstance> iterator = existedInstances.iterator();
		TroveInstance troveInstance = iterator.next();
		assertThat(troveInstance.getId()).isEqualTo(instanceNameSuffix);
		
		troveProvider.delete(template, Lists.newArrayList(instanceNameSuffix));
		verify(instanceApi, times(1)).delete(instanceNameSuffix);
	}
	
}

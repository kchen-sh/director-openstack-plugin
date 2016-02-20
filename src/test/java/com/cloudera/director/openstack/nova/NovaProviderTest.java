/*
 * Copyright (c) 2015 Google, Inc.
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

package com.cloudera.director.openstack.nova;

import static com.cloudera.director.openstack.nova.NovaProviderConfigurationProperty.REGION;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.NETWORK_ID;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.TYPE;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.SECURITY_GROUP_NAMES;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.AVAILABILITY_ZONE;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.KEY_NAME;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.FLOATING_IP_POOL;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.VOLUME_NUMBER;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.VOLUME_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

import com.cloudera.director.openstack.OpenStackCredentials;
import org.jclouds.collect.IterableWithMarkers;
import org.jclouds.collect.PagedIterable;
import org.jclouds.collect.PagedIterables;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.jclouds.openstack.cinder.v1.features.VolumeApi;
import org.jclouds.openstack.cinder.v1.options.CreateVolumeOptions;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.Flavor;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIPPool;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.Server.Status;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.domain.VolumeAttachment;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPPoolApi;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.v2_0.domain.Resource;

import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.exception.UnrecoverableProviderException;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.junit.Before;
import org.junit.Test;

import org.mockito.stubbing.OngoingStubbing;

import com.typesafe.config.Config;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tests {@link NovaProvider}.
 */
public class NovaProviderTest {

	private static final Logger LOG = Logger.getLogger(NovaProviderTest.class.getName());

	private static final DefaultLocalizationContext DEFAULT_LOCALIZATION_CONTEXT =
			new DefaultLocalizationContext(Locale.getDefault(), "");
	
	private static final String END_POINT_VALUE = "http://localhost:5000/v2.0";
	private static final String IDENTITY_VALUE = "admin:admin";
	private static final String CREDENTIAL_VALUE = "mycredential";
	private static final String REGION_NAME = "regionOne";
	private static final String IMAGE_ALIAS_RHEL = "rhel6";
	private static final String FLAVOR_TYPE_VALUE = "m1.large";
	private static final String NETWORK_ID_VALUE = "FakeID111";
	private static final String AVAILABILITY_ZONE_VALUE = "zone1";
	private static final String SECURITY_GROUP_NAMES_VALUE = "default";
	private static final String KEY_NAME_VALUE = "TestKey";
	private static final String FLOATING_IP_POOL_VALUE = "public";
	private static final String VOLUME_NUMBER_VALUE = "1";
	private static final String VOLUME_SIZE_VALUE = "10";
	
	private NovaProvider novaProvider;
	private OpenStackCredentials credentials;

	private static final int VOLUMESIZE = 10;
	private static final String DEFAULT_FLAVOR = "m1.large";
	private static final String DEFAULT_FLAVOR_ID = "4";
	private static final String DEFAULT_PRIVATE_IP1 = "10.0.0.1";
	private static final String DEFAULT_PRIVATE_IP2 = "10.0.0.2";
	private static final String DEFAULT_FLOATING_IP1 = "172.16.0.1";
	private static final String DEFAULT_FLOATING_IP2 = "172.16.0.2";

	NovaInstanceTemplate template;
	NovaApi novaApi;
	CinderApi cinderApi;
	ServerApi serverApi;
	FlavorApi flavorApi;
	Optional<FloatingIPApi> fltIpApi;
	Optional<FloatingIPPoolApi> fltIpPoolApi;
	Optional<VolumeAttachmentApi> volAttApi;
	FloatingIPApi floatingIpApi;
	FloatingIPPoolApi floatingIpPoolApi;
	VolumeAttachmentApi volumeAttachmentApi;
	VolumeApi volumeApi;
	Flavor flavor;

	String instanceId1;
	String instanceId2;
	String novaInstanceId1;
	String novaInstanceId2;
	String floatingIpId1;
	String floatingIpId2;
	String volumeId1;
	String volumeId2;

	Map<String, String> templateConfig = new HashMap<String, String>();

	
	@Before
	public void setUp() throws IOException {
		credentials = mock(OpenStackCredentials.class);
		
		when(credentials.getEndpoint()).thenReturn(END_POINT_VALUE);
		when(credentials.getIdentity()).thenReturn(IDENTITY_VALUE);
		when(credentials.getCredential()).thenReturn(CREDENTIAL_VALUE);
		
		// Prepare configuration for Google compute provider.
		Map<String, String> openstackConfig = new HashMap<String, String>();
		openstackConfig.put(REGION.unwrap().getConfigKey(), REGION_NAME);
		Configured providerConfiguration = new SimpleConfiguration(openstackConfig);
		
		Config buildOsConfig = mock(Config.class);
		
		// Create the Nova provider.
		novaProvider = spy(new NovaProvider(providerConfiguration, credentials,
				buildOsConfig, DEFAULT_LOCALIZATION_CONTEXT));
		
		// Prepare configuration for resource template.
		templateConfig.put(IMAGE.unwrap().getConfigKey(), IMAGE_ALIAS_RHEL);
		templateConfig.put(TYPE.unwrap().getConfigKey(), FLAVOR_TYPE_VALUE);
		templateConfig.put(NETWORK_ID.unwrap().getConfigKey(), NETWORK_ID_VALUE);
		templateConfig.put(AVAILABILITY_ZONE.unwrap().getConfigKey(), AVAILABILITY_ZONE_VALUE);
		templateConfig.put(SECURITY_GROUP_NAMES.unwrap().getConfigKey(), SECURITY_GROUP_NAMES_VALUE);
		templateConfig.put(KEY_NAME.unwrap().getConfigKey(), KEY_NAME_VALUE);
		templateConfig.put(FLOATING_IP_POOL.unwrap().getConfigKey(), FLOATING_IP_POOL_VALUE);
		templateConfig.put(VOLUME_NUMBER.unwrap().getConfigKey(), "0");
		templateConfig.put(VOLUME_SIZE.unwrap().getConfigKey(), "0");
		
		// Configure stub for successful server create method.
		novaApi = mock(NovaApi.class);
		cinderApi = mock(CinderApi.class);
		serverApi = mock(ServerApi.class);
		flavorApi = mock(FlavorApi.class);
		floatingIpApi = mock(FloatingIPApi.class);
		floatingIpPoolApi = mock(FloatingIPPoolApi.class);
		volumeAttachmentApi = mock(VolumeAttachmentApi.class);
		fltIpApi = Optional.of(floatingIpApi);
		fltIpPoolApi = Optional.of(floatingIpPoolApi);
		volAttApi = Optional.of(volumeAttachmentApi);
		volumeApi = mock(VolumeApi.class);

		when(novaApi.getServerApi(REGION_NAME)).thenReturn(serverApi);
		when(novaApi.getFlavorApi(REGION_NAME)).thenReturn(flavorApi);
		when(novaApi.getFloatingIPApi(REGION_NAME)).thenReturn(fltIpApi);
		when(novaApi.getFloatingIPPoolApi(REGION_NAME)).thenReturn(fltIpPoolApi);
		when(novaApi.getVolumeAttachmentApi(REGION_NAME)).thenReturn(volAttApi);
		when(cinderApi.getVolumeApi(REGION_NAME)).thenReturn(volumeApi);
		
		when(novaProvider.getNovaApi()).thenReturn(novaApi);
		when(novaProvider.getCinderApi()).thenReturn(cinderApi);
		
		instanceId1 = UUID.randomUUID().toString();
		instanceId2 = UUID.randomUUID().toString();
		novaInstanceId1 =  UUID.randomUUID().toString();
		novaInstanceId2 =  UUID.randomUUID().toString();
		floatingIpId1 = UUID.randomUUID().toString();
		floatingIpId2 = UUID.randomUUID().toString();
		volumeId1 = UUID.randomUUID().toString();
		volumeId2 = UUID.randomUUID().toString();

		flavor = mock(Flavor.class);
		when(flavor.getName()).thenReturn(DEFAULT_FLAVOR);
		when(flavor.getId()).thenReturn(DEFAULT_FLAVOR_ID);
		PagedIterable<Resource> flavorList = PagedIterables.onlyPage(IterableWithMarkers.from(Lists.newArrayList(flavor)));
		when(flavorApi.list()).thenReturn(flavorList);
		
	}
		
	@Test
	public void testAllocate_Instances() throws InterruptedException, IOException {
		// We do not test floating IP allocation in this method.
		templateConfig.put(FLOATING_IP_POOL.unwrap().getConfigKey(), null);
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		String decoratedInstanceName1 = template.getInstanceNamePrefix() + "-" + instanceId1;
		String decoratedInstanceName2 = template.getInstanceNamePrefix() + "-" + instanceId2;
		
		ServerCreated servercreated1 = mock(ServerCreated.class);
		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);
		when(servercreated1.getId()).thenReturn(novaInstanceId1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
		when(server1.getName()).thenReturn(decoratedInstanceName1);
		when(server1.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.DELETED);
		
		ServerCreated servercreated2 = mock(ServerCreated.class);
		Address address2 = mock(Address.class);
		when(address2.getAddr()).thenReturn(DEFAULT_PRIVATE_IP2);
		when(address2.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses2 = ArrayListMultimap.create();
		addresses2.put("1", address2);
		when(servercreated2.getId()).thenReturn(novaInstanceId2);

		Server server2 = mock(Server.class);
		when(server2.getId()).thenReturn(novaInstanceId2);
		when(server2.getAddresses()).thenReturn(addresses2);
		when(server2.getName()).thenReturn(decoratedInstanceName2);
		when(server2.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.DELETED);

		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		Map<String, String> meta2 = Maps.newHashMap();
		meta2.put("DIRECTOR_ID", instanceId2);
		when(server2.getMetadata()).thenReturn(meta2);
		
		PagedIterable<Server> emptyServers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1, server2)));
		// The first thenReturn is at the beginning of allocate->releaseResources.
		when(serverApi.listInDetail())
			.thenReturn(emptyServers).thenReturn(servers);

		when(serverApi.create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated1);
		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		when(serverApi.create(eq(decoratedInstanceName2), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated2);
		when(serverApi.get(novaInstanceId2)).thenReturn(server2);
		
		//Create the two Instances.
		novaProvider.allocate(template, instanceIds, 2);
		
		//Verify instance creatings was called.
		verify(serverApi).create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));
		verify(serverApi).create(eq(decoratedInstanceName2), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));
		

		// Verify instance name and private IP.
		assertThat(serverApi.get(novaInstanceId1).getName()).isEqualTo(decoratedInstanceName1);
		Multimap<String, Address> adds1 = serverApi.get(novaInstanceId1).getAddresses();
		boolean ipInAdds1 = false;
		for (Address pAddr : adds1.values()) {
			if (pAddr.getAddr().equals(DEFAULT_PRIVATE_IP1)) {
				ipInAdds1 = true;
			}
		}
		assertThat(ipInAdds1).isEqualTo(true);

		assertThat(serverApi.get(novaInstanceId2).getName()).isEqualTo(decoratedInstanceName2);
		Multimap<String, Address> adds2 = serverApi.get(novaInstanceId2).getAddresses();
		boolean ipInAdds2 = false;
		for (Address pAddr : adds2.values()) {
			if (pAddr.getAddr().equals(DEFAULT_PRIVATE_IP2)) {
				ipInAdds2 = true;
			}
		}
		assertThat(ipInAdds2).isEqualTo(true);
	}

	@Test
	public void testAllocate_Instances_MinTwo_FailOne() throws InterruptedException, IOException {
		// We do not test floating IP allocation in this method.
		templateConfig.put(FLOATING_IP_POOL.unwrap().getConfigKey(), null);
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());
		
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		String decoratedInstanceName1 = template.getInstanceNamePrefix() + "-" + instanceId1;
		String decoratedInstanceName2 = template.getInstanceNamePrefix() + "-" + instanceId2;
		
		ServerCreated servercreated1 = mock(ServerCreated.class);
		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);
		when(servercreated1.getId()).thenReturn(novaInstanceId1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
		when(server1.getName()).thenReturn(decoratedInstanceName1);
		when(server1.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.DELETED);
		
		ServerCreated servercreated2 = mock(ServerCreated.class);
		Address address2 = mock(Address.class);
		when(address2.getAddr()).thenReturn(DEFAULT_PRIVATE_IP2);
		when(address2.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses2 = ArrayListMultimap.create();
		addresses2.put("1", address2);
		when(servercreated2.getId()).thenReturn(novaInstanceId2);
		
		// Server2 will never get private IP.
		Server server2 = mock(Server.class);
		when(server2.getId()).thenReturn(novaInstanceId2);
		when(server2.getAddresses()).thenReturn(null);
		when(server2.getName()).thenReturn(decoratedInstanceName2);
		when(server2.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ERROR);

		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		Map<String, String> meta2 = Maps.newHashMap();
		meta2.put("DIRECTOR_ID", instanceId2);
		when(server2.getMetadata()).thenReturn(meta2);
		
		PagedIterable<Server> emptyServers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1, server2)));
		// The first thenReturn is at the beginning of allocate->releaseResources.
		when(serverApi.listInDetail())
			.thenReturn(emptyServers).thenReturn(servers);

		when(serverApi.create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated1);
		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		when(serverApi.create(eq(decoratedInstanceName2), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated2);
		when(serverApi.get(novaInstanceId2)).thenReturn(server2);
		when(serverApi.delete(novaInstanceId1)).thenReturn(true);
		when(serverApi.delete(novaInstanceId2)).thenReturn(true);
		
		//Create the two Instances.
		try {
			novaProvider.allocate(template, instanceIds, 2);
			fail("An exception should have been thrown when we failed to provision at least minCount instances.");
		} catch (UnrecoverableProviderException e) {
			LOG.info("Caught: " + e.getMessage());
			assertThat(e.getMessage()).isEqualTo("Problem allocating 2 instances: Can only get 1 instances with IPs while we want 2.");
		}
		
		//Verify instance creatings was called.
		verify(serverApi).create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));
		verify(serverApi).create(eq(decoratedInstanceName2), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));

		// Verify both instances were deleted.
		verify(serverApi).delete(eq(novaInstanceId1));
		verify(serverApi).delete(eq(novaInstanceId2));

		// Verify instance name and private IP.
		assertThat(serverApi.get(novaInstanceId1).getName()).isEqualTo(decoratedInstanceName1);
		Multimap<String, Address> adds1 = serverApi.get(novaInstanceId1).getAddresses();
		boolean ipInAdds1 = false;
		for (Address pAddr : adds1.values()) {
			if (pAddr.getAddr().equals(DEFAULT_PRIVATE_IP1)) {
				ipInAdds1 = true;
			}
		}
		assertThat(ipInAdds1).isEqualTo(true);

		assertThat(serverApi.get(novaInstanceId2).getName()).isEqualTo(decoratedInstanceName2);
		Multimap<String, Address> adds2 = serverApi.get(novaInstanceId2).getAddresses();
		assertThat(adds2).isEqualTo(null);
	}

	@Test
	public void testAllocate_Instances_MinOne_FailOne() throws InterruptedException, IOException {
		// We do not test floating IP allocation in this method.
		templateConfig.put(FLOATING_IP_POOL.unwrap().getConfigKey(), null);
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());
		
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		String decoratedInstanceName1 = template.getInstanceNamePrefix() + "-" + instanceId1;
		String decoratedInstanceName2 = template.getInstanceNamePrefix() + "-" + instanceId2;
		
		ServerCreated servercreated1 = mock(ServerCreated.class);
		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);
		when(servercreated1.getId()).thenReturn(novaInstanceId1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
		when(server1.getName()).thenReturn(decoratedInstanceName1);
		when(server1.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.DELETED);
		
		ServerCreated servercreated2 = mock(ServerCreated.class);
		Address address2 = mock(Address.class);
		when(address2.getAddr()).thenReturn(DEFAULT_PRIVATE_IP2);
		when(address2.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses2 = ArrayListMultimap.create();
		addresses2.put("1", address2);
		when(servercreated2.getId()).thenReturn(novaInstanceId2);
		
		// Server2 will never get private IP.
		Server server2 = mock(Server.class);
		when(server2.getId()).thenReturn(novaInstanceId2);
		when(server2.getAddresses()).thenReturn(null);
		when(server2.getName()).thenReturn(decoratedInstanceName2);
		when(server2.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ERROR);

		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		Map<String, String> meta2 = Maps.newHashMap();
		meta2.put("DIRECTOR_ID", instanceId2);
		when(server2.getMetadata()).thenReturn(meta2);
		
		PagedIterable<Server> emptyServers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1, server2)));
		// The first thenReturn is at the beginning of allocate->releaseResources.
		when(serverApi.listInDetail())
			.thenReturn(emptyServers).thenReturn(servers);

		when(serverApi.create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated1);
		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		when(serverApi.create(eq(decoratedInstanceName2), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated2);
		when(serverApi.get(novaInstanceId2)).thenReturn(server2);
		when(serverApi.delete(novaInstanceId1)).thenReturn(true);
		when(serverApi.delete(novaInstanceId2)).thenReturn(true);
		
		//Create the two Instances.		
		novaProvider.allocate(template, instanceIds, 1);
		
		//Verify instance creatings was called.
		verify(serverApi).create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));
		verify(serverApi).create(eq(decoratedInstanceName2), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));

		// Verify instance name and private IP.
		assertThat(serverApi.get(novaInstanceId1).getName()).isEqualTo(decoratedInstanceName1);
		Multimap<String, Address> adds1 = serverApi.get(novaInstanceId1).getAddresses();
		boolean ipInAdds1 = false;
		for (Address pAddr : adds1.values()) {
			if (pAddr.getAddr().equals(DEFAULT_PRIVATE_IP1)) {
				ipInAdds1 = true;
			}
		}
		assertThat(ipInAdds1).isEqualTo(true);

		assertThat(serverApi.get(novaInstanceId2).getName()).isEqualTo(decoratedInstanceName2);
		Multimap<String, Address> adds2 = serverApi.get(novaInstanceId2).getAddresses();
		assertThat(adds2).isEqualTo(null);

		// Verify instance 2 were deleted.
		verify(serverApi).delete(eq(novaInstanceId2));
	}

	@Test
	public void testAllocate_Instance_Volume() throws InterruptedException, IOException {
		// We do not test floating IP allocation in this method.
		templateConfig.put(FLOATING_IP_POOL.unwrap().getConfigKey(), null);
		templateConfig.put(VOLUME_NUMBER.unwrap().getConfigKey(), VOLUME_NUMBER_VALUE);
		templateConfig.put(VOLUME_SIZE.unwrap().getConfigKey(), VOLUME_SIZE_VALUE);
		
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);

		String decoratedInstanceName1 = template.getInstanceNamePrefix() + "-" + instanceId1;
		
		ServerCreated servercreated1 = mock(ServerCreated.class);
		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);
		when(servercreated1.getId()).thenReturn(novaInstanceId1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
		when(server1.getName()).thenReturn(decoratedInstanceName1);
		when(server1.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.DELETED);
		
		FloatingIP floatingIp1 = mock(FloatingIP.class);
		when(floatingIp1.getId()).thenReturn(floatingIpId1);
		when(floatingIp1.getIp()).thenReturn(DEFAULT_FLOATING_IP1);
		when(floatingIp1.getInstanceId()).thenReturn(null).thenReturn(novaInstanceId1);
		when(floatingIpApi.allocateFromPool(FLOATING_IP_POOL_VALUE))
				.thenReturn(floatingIp1);
		when(floatingIpApi.list())
				.thenReturn(FluentIterable.from(Lists.newArrayList(floatingIp1)));
		when(floatingIpApi.get(floatingIpId1)).thenReturn(floatingIp1);
		doNothing().when(floatingIpApi).addToServer(anyString(), anyString());
		doNothing().when(floatingIpApi).delete(floatingIpId1);

		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		
		PagedIterable<Server> emptyServers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1)));
		
		// The first thenReturn is at the beginning of allocate->releaseResources.
		// So return an empty list.
		when(serverApi.listInDetail())
			.thenReturn(emptyServers).thenReturn(servers);

		when(serverApi.create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated1);
		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		when(serverApi.delete(novaInstanceId1)).thenReturn(true);

		Volume volume1 = mock(Volume.class);		
		when(volume1.getId()).thenReturn(volumeId1);
		when(volume1.getMetadata()).thenReturn(meta1);
		when(volume1.getStatus()).thenReturn(Volume.Status.CREATING)
				.thenReturn(Volume.Status.AVAILABLE)
				.thenReturn(Volume.Status.ATTACHING)
				.thenReturn(Volume.Status.IN_USE)
				.thenReturn(Volume.Status.AVAILABLE)
				.thenReturn(Volume.Status.DELETING);
		when(volumeApi.create(anyInt(), any(CreateVolumeOptions.class))).thenReturn(volume1);
		// At last the volume is deleted, so return null.
		when(volumeApi.get(volumeId1)).thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(null);
		when(volumeApi.delete(volumeId1)).thenReturn(true);
		
		// The first thenReturn is at the beginning of allocate->releaseResources.
		// So return an empty list.
		FluentIterable<? extends Volume> emptyVolumes = FluentIterable.from(Lists.newArrayList());
		FluentIterable<? extends Volume> volumes1 = FluentIterable.from(Lists.newArrayList(volume1));
		OngoingStubbing<FluentIterable<? extends Volume>> stub = when(volumeApi.listInDetail());
		stub.thenReturn(emptyVolumes).thenReturn(volumes1);
		
		VolumeAttachment volumeAttachment = mock(VolumeAttachment.class);
		when(volumeAttachmentApi.attachVolumeToServerAsDevice(volumeId1, instanceId1, "")).thenReturn(volumeAttachment);
		
		//Create the Instance.
		novaProvider.allocate(template, instanceIds, 1);
		
		//Verify instance creatings was called.
		verify(serverApi).create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));

		// Verify instance name and private IP.
		assertThat(serverApi.get(novaInstanceId1).getName()).isEqualTo(decoratedInstanceName1);
		Multimap<String, Address> adds1 = serverApi.get(novaInstanceId1).getAddresses();
		boolean ipInAdds1 = false;
		for (Address pAddr : adds1.values()) {
			if (pAddr.getAddr().equals(DEFAULT_PRIVATE_IP1)) {
				ipInAdds1 = true;
			}
		}
		assertThat(ipInAdds1).isEqualTo(true);
		
		//Verify volume creating and attaching was called.
		verify(volumeApi).create(eq(VOLUMESIZE), any(CreateVolumeOptions.class));
		verify(volumeAttachmentApi).attachVolumeToServerAsDevice(eq(volumeId1), eq(novaInstanceId1), eq(""));
	}

	@Test
	public void testAllocate_TwoInstancesVolumes_MinTwo_FailOne() throws InterruptedException, IOException {
		// We do not test floating IP allocation in this method.
		templateConfig.put(FLOATING_IP_POOL.unwrap().getConfigKey(), null);
		templateConfig.put(VOLUME_NUMBER.unwrap().getConfigKey(), VOLUME_NUMBER_VALUE);
		templateConfig.put(VOLUME_SIZE.unwrap().getConfigKey(), VOLUME_SIZE_VALUE);
		
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		String decoratedInstanceName1 = template.getInstanceNamePrefix() + "-" + instanceId1;
		String decoratedInstanceName2 = template.getInstanceNamePrefix() + "-" + instanceId2;
		
		ServerCreated servercreated1 = mock(ServerCreated.class);
		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);
		when(servercreated1.getId()).thenReturn(novaInstanceId1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
		when(server1.getName()).thenReturn(decoratedInstanceName1);
		when(server1.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.DELETED);
		
		ServerCreated servercreated2 = mock(ServerCreated.class);
		Address address2 = mock(Address.class);
		when(address2.getAddr()).thenReturn(DEFAULT_PRIVATE_IP2);
		when(address2.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses2 = ArrayListMultimap.create();
		addresses2.put("1", address2);
		when(servercreated2.getId()).thenReturn(novaInstanceId2);

		Server server2 = mock(Server.class);
		when(server2.getId()).thenReturn(novaInstanceId2);
		when(server2.getAddresses()).thenReturn(addresses2);
		when(server2.getName()).thenReturn(decoratedInstanceName2);
		when(server2.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.DELETED);

		FloatingIP floatingIp1 = mock(FloatingIP.class);
		when(floatingIp1.getId()).thenReturn(floatingIpId1);
		when(floatingIp1.getIp()).thenReturn(DEFAULT_FLOATING_IP1);
		when(floatingIp1.getInstanceId()).thenReturn(null).thenReturn(novaInstanceId1);

		FloatingIP floatingIp2 = mock(FloatingIP.class);
		when(floatingIp2.getId()).thenReturn(floatingIpId2);
		when(floatingIp2.getIp()).thenReturn(DEFAULT_FLOATING_IP2);
		when(floatingIp2.getInstanceId()).thenReturn(null).thenReturn(novaInstanceId2);

		when(floatingIpApi.allocateFromPool(FLOATING_IP_POOL_VALUE))
				.thenReturn(floatingIp1)
				.thenReturn(floatingIp2);
		when(floatingIpApi.list())
				.thenReturn(FluentIterable.from(Lists.newArrayList(floatingIp1)));
		when(floatingIpApi.get(floatingIpId1)).thenReturn(floatingIp1);
		doNothing().when(floatingIpApi).addToServer(anyString(), anyString());
		doNothing().when(floatingIpApi).delete(floatingIpId1);
		doNothing().when(floatingIpApi).delete(floatingIpId2);

		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		Map<String, String> meta2 = Maps.newHashMap();
		meta2.put("DIRECTOR_ID", instanceId2);
		when(server2.getMetadata()).thenReturn(meta2);
		
		PagedIterable<Server> emptyServers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1, server2)));
		
		// The first thenReturn is at the beginning of allocate->releaseResources.
		// So return an empty list.
		when(serverApi.listInDetail())
			.thenReturn(emptyServers).thenReturn(servers);

		when(serverApi.create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated1);
		when(serverApi.create(eq(decoratedInstanceName2), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
		.thenReturn(servercreated2);
		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		when(serverApi.get(novaInstanceId2)).thenReturn(server2);
		when(serverApi.delete(novaInstanceId1)).thenReturn(true);
		when(serverApi.delete(novaInstanceId2)).thenReturn(true);
		
		Volume volume1 = mock(Volume.class);
		when(volume1.getId()).thenReturn(volumeId1);
		when(volume1.getMetadata()).thenReturn(meta1);
		when(volume1.getStatus()).thenReturn(Volume.Status.CREATING)
				.thenReturn(Volume.Status.AVAILABLE)
				.thenReturn(Volume.Status.ATTACHING)
				.thenReturn(Volume.Status.IN_USE)
				.thenReturn(Volume.Status.AVAILABLE)
				.thenReturn(Volume.Status.DELETING);
		// At last the volume is deleted, so return null.
		when(volumeApi.get(volumeId1)).thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(volume1)
				.thenReturn(null);
		when(volumeApi.delete(volumeId1)).thenReturn(true);
		
		Volume volume2 = mock(Volume.class);
		// volume2 will fail.
		when(volume2.getId()).thenReturn(volumeId2);
		when(volume2.getMetadata()).thenReturn(meta2);
		when(volume2.getStatus()).thenReturn(Volume.Status.CREATING)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.ERROR)
				.thenReturn(Volume.Status.DELETING);
		// At last the volume is deleted, so return null.
		when(volumeApi.get(volumeId2)).thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(volume2)
				.thenReturn(null);
		when(volumeApi.delete(volumeId2)).thenReturn(true);
		
		when(volumeApi.create(anyInt(), any(CreateVolumeOptions.class)))
			.thenReturn(volume1)
			.thenReturn(volume2);

		// The first thenReturn is at the beginning of allocate->releaseResources.
		// So return an empty list.
		FluentIterable<? extends Volume> emptyVolumes = FluentIterable.from(Lists.newArrayList());
		FluentIterable<? extends Volume> volumes1 = FluentIterable.from(Lists.newArrayList(volume1));
		OngoingStubbing<FluentIterable<? extends Volume>> stub = when(volumeApi.listInDetail());
		stub.thenReturn(emptyVolumes)
			.thenReturn(volumes1);
		
		VolumeAttachment volumeAttachment = mock(VolumeAttachment.class);
		when(volumeAttachmentApi.attachVolumeToServerAsDevice(volumeId1, instanceId1, "")).thenReturn(volumeAttachment);

		//Create the two Instances.		
		try {
			novaProvider.allocate(template, instanceIds, 2);
			fail("An exception should have been thrown when we failed to provision at least minCount instances.");
		} catch (UnrecoverableProviderException e) {
			LOG.info("Caught: " + e.getMessage());
			assertThat(e.getMessage()).isEqualTo("Problem allocating 2 instances: Can only get 1 instances with volumes while we want 2.");
		}
		
		//Verify instance creatings was called.
		verify(serverApi).create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));
		verify(serverApi).create(eq(decoratedInstanceName2), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));

		// Verify instances were deleted.
		verify(serverApi).delete(eq(novaInstanceId1));
		verify(serverApi).delete(eq(novaInstanceId2));

		// Verify instance name and private IP.
		assertThat(serverApi.get(novaInstanceId1).getName()).isEqualTo(decoratedInstanceName1);
		Multimap<String, Address> adds1 = serverApi.get(novaInstanceId1).getAddresses();
		boolean ipInAdds1 = false;
		for (Address pAddr : adds1.values()) {
			if (pAddr.getAddr().equals(DEFAULT_PRIVATE_IP1)) {
				ipInAdds1 = true;
			}
		}
		assertThat(ipInAdds1).isEqualTo(true);

		assertThat(serverApi.get(novaInstanceId2).getName()).isEqualTo(decoratedInstanceName2);
		Multimap<String, Address> adds2 = serverApi.get(novaInstanceId2).getAddresses();
		boolean ipInAdds2 = false;
		for (Address pAddr : adds2.values()) {
			if (pAddr.getAddr().equals(DEFAULT_PRIVATE_IP2)) {
				ipInAdds2 = true;
			}
		}
		assertThat(ipInAdds2).isEqualTo(true);

		//Verify volume creatings and deletings was called.
		verify(volumeApi, times(2)).create(eq(VOLUMESIZE), any(CreateVolumeOptions.class));
		verify(volumeApi).delete(eq(volumeId2));
		verify(volumeApi).delete(eq(volumeId1));
	}

	@Test
	public void testAllocate_Instance_FloatingIP() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		FloatingIPPool floatingIpPool = mock(FloatingIPPool.class);
		when(floatingIpPool.getName()).thenReturn(FLOATING_IP_POOL_VALUE);
		FluentIterable<? extends FloatingIPPool> fltIpPools = FluentIterable.from(Lists.newArrayList(floatingIpPool));
		// We cannto directly thenReturn fltIpPools, for it is extends type. 
		OngoingStubbing<FluentIterable<? extends FloatingIPPool>> stub = when(floatingIpPoolApi.list());
		stub.thenReturn(fltIpPools);

		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);

		String decoratedInstanceName1 = template.getInstanceNamePrefix() + "-" + instanceId1;
		
		ServerCreated servercreated1 = mock(ServerCreated.class);
		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);
		when(servercreated1.getId()).thenReturn(novaInstanceId1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
		when(server1.getName()).thenReturn(decoratedInstanceName1);
		when(server1.getStatus()).thenReturn(Status.BUILD)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.ACTIVE)
				.thenReturn(Status.DELETED);
		
		FloatingIP floatingIp1 = mock(FloatingIP.class);
		when(floatingIp1.getId()).thenReturn(floatingIpId1);
		when(floatingIp1.getIp()).thenReturn(DEFAULT_FLOATING_IP1);
		when(floatingIp1.getInstanceId()).thenReturn(null).thenReturn(novaInstanceId1);
		when(floatingIpApi.allocateFromPool(FLOATING_IP_POOL_VALUE))
				.thenReturn(floatingIp1);
		when(floatingIpApi.list())
				.thenReturn(FluentIterable.from(Lists.newArrayList(floatingIp1)));
		when(floatingIpApi.get(floatingIpId1)).thenReturn(floatingIp1);
		doNothing().when(floatingIpApi).addToServer(anyString(), anyString());
		doNothing().when(floatingIpApi).delete(floatingIpId1);

		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		
		PagedIterable<Server> emptyServers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1)));
		// The first thenReturn is at the beginning of allocate->releaseResources.
		when(serverApi.listInDetail())
			.thenReturn(emptyServers).thenReturn(servers);

		when(serverApi.create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class)))
				.thenReturn(servercreated1);
		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		when(serverApi.delete(novaInstanceId1)).thenReturn(true);

		//Create the Instance.
		novaProvider.allocate(template, instanceIds, 1);
		
		//Verify instance creatings was called.
		verify(serverApi).create(eq(decoratedInstanceName1), eq(IMAGE_ALIAS_RHEL), eq(DEFAULT_FLAVOR_ID), any(CreateServerOptions.class));

		// Verify instance name and private IP.
		assertThat(serverApi.get(novaInstanceId1).getName()).isEqualTo(decoratedInstanceName1);
		Multimap<String, Address> adds1 = serverApi.get(novaInstanceId1).getAddresses();
		boolean ipInAdds1 = false;
		for (Address pAddr : adds1.values()) {
			if (pAddr.getAddr().equals(DEFAULT_PRIVATE_IP1)) {
				ipInAdds1 = true;
			}
		}
		assertThat(ipInAdds1).isEqualTo(true);
		
		//Verify floating IP.
		verify(floatingIpApi).allocateFromPool(eq(FLOATING_IP_POOL_VALUE));
		verify(floatingIpApi).addToServer(eq(DEFAULT_FLOATING_IP1), eq(novaInstanceId1));
	}
	
	@Test
	public void testAllocate_Instance_FloatingIP_NoPool() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		FluentIterable<? extends FloatingIPPool> fltIpPools = FluentIterable.from(Lists.newArrayList());
		// We cannto directly thenReturn fltIpPools, for it is extends type. 
		OngoingStubbing<FluentIterable<? extends FloatingIPPool>> stub = when(floatingIpPoolApi.list());
		stub.thenReturn(fltIpPools);

		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);

		//Create the Instance.		
		try {
			novaProvider.allocate(template, instanceIds, 1);
			fail("An exception should have been thrown when we failed to provision at least minCount instances.");
		} catch (UnrecoverableProviderException e) {
			LOG.info("Caught: " + e.getMessage());
			// Check the Error Message.
			assertThat(e.getMessage()).isEqualTo("FloatingIpPool does not exist.");
		}
	}
	
	@Test
	public void testFind_TwoInstances() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
		
		Address address2 = mock(Address.class);
		when(address2.getAddr()).thenReturn(DEFAULT_PRIVATE_IP2);
		when(address2.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses2 = ArrayListMultimap.create();
		addresses2.put("1", address2);

		Server server2 = mock(Server.class);
		when(server2.getId()).thenReturn(novaInstanceId2);
		when(server2.getAddresses()).thenReturn(addresses2);

		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		Map<String, String> meta2 = Maps.newHashMap();
		meta2.put("DIRECTOR_ID", instanceId2);
		when(server2.getMetadata()).thenReturn(meta2);
		
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1, server2)));
		when(serverApi.listInDetail()).thenReturn(servers);

		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		when(serverApi.get(novaInstanceId2)).thenReturn(server2);
		
		Collection<NovaInstance> novaInstances = novaProvider.find(template, instanceIds);
		
		// Verify both instances are found.
		assertThat(novaInstances.size()).isEqualTo(2);
		
		// Verify the properties of the first returned instance.
		Iterator<NovaInstance> instanceIterator = novaInstances.iterator();
		NovaInstance novaInstance1 = instanceIterator.next();
		assertThat(novaInstance1.getId()).isEqualTo(instanceId1);
		assertThat(novaInstance1.getPrivateIpAddress().getHostAddress()).isEqualTo(DEFAULT_PRIVATE_IP1);

		// Verify the properties of the second returned instance.
		NovaInstance novaInstance2 = instanceIterator.next();
		assertThat(novaInstance2.getId()).isEqualTo(instanceId2);
		assertThat(novaInstance2.getPrivateIpAddress().getHostAddress()).isEqualTo(DEFAULT_PRIVATE_IP2);

	}
	
	@Test
	public void testFind_TwoInstances_OneFail() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
		
		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1)));
		when(serverApi.listInDetail()).thenReturn(servers);

		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		
		Collection<NovaInstance> novaInstances = novaProvider.find(template, instanceIds);
		
		// Verify only one instance is found.
		assertThat(novaInstances.size()).isEqualTo(1);
		
		// Verify the properties of the first returned instance.
		Iterator<NovaInstance> instanceIterator = novaInstances.iterator();
		NovaInstance novaInstance1 = instanceIterator.next();
		assertThat(novaInstance1.getId()).isEqualTo(instanceId1);
		assertThat(novaInstance1.getPrivateIpAddress().getHostAddress()).isEqualTo(DEFAULT_PRIVATE_IP1);
	}
	
	@Test
	public void testFind_TwoInstances_TwoFail() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		when(serverApi.listInDetail()).thenReturn(servers);

		Collection<NovaInstance> novaInstances = novaProvider.find(template, instanceIds);
		
		// Verify no instance is found.
		assertThat(novaInstances.size()).isEqualTo(0);
	}
	
	@Test
	public void testCheck_TwoInstances_States() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getStatus()).thenReturn(Status.BUILD);
		
		Server server2 = mock(Server.class);
		when(server2.getId()).thenReturn(novaInstanceId2);
		when(server2.getStatus()).thenReturn(Status.ACTIVE);
		
		when(serverApi.get(novaInstanceId1)).thenReturn(server1);
		when(serverApi.get(novaInstanceId2)).thenReturn(server2);
		
		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		Map<String, String> meta2 = Maps.newHashMap();
		meta2.put("DIRECTOR_ID", instanceId2);
		when(server2.getMetadata()).thenReturn(meta2);
		
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1, server2)));
		when(serverApi.listInDetail()).thenReturn(servers);

		Map<String, InstanceState> instanceStates = novaProvider.getInstanceState(template, instanceIds);
		
		// Verify both instances are found.
		assertThat(instanceStates.size()).isEqualTo(2);
		
		// Verify the state of the first instance.
		InstanceState instanceState1 = instanceStates.get(instanceId1);
		assertThat(instanceState1.getInstanceStatus()).isEqualTo(InstanceStatus.PENDING);

		// Verify the state of the second instance.
		InstanceState instanceState2 = instanceStates.get(instanceId2);
		assertThat(instanceState2.getInstanceStatus()).isEqualTo(InstanceStatus.RUNNING);
	}

	@Test
	public void testCheck_TwoInstances_States_PartialSuccess() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getStatus()).thenReturn(Status.BUILD);
				
		when(serverApi.get(novaInstanceId1)).thenReturn(server1);

		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1)));
		when(serverApi.listInDetail()).thenReturn(servers);
		
		Map<String, InstanceState> instanceStates = novaProvider.getInstanceState(template, instanceIds);
		
		// Verify both instances are found (the not existing one will be DELELTED).
		assertThat(instanceStates.size()).isEqualTo(2);
		
		// Verify the state of the first instance.
		InstanceState instanceState1 = instanceStates.get(instanceId1);
	    assertThat(instanceState1.getInstanceStatus()).isEqualTo(InstanceStatus.PENDING);

	    // Verify the state of the first instance.
	    InstanceState instanceState2 = instanceStates.get(instanceId2);
	    assertThat(instanceState2.getInstanceStatus()).isEqualTo(InstanceStatus.DELETED);
	}

	@Test
	public void testCheck_TwoInstances_States_BothFail() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		when(serverApi.listInDetail()).thenReturn(servers);
		
		Map<String, InstanceState> instanceStates = novaProvider.getInstanceState(template, instanceIds);
		
		// Verify both instances are found (the not existing one will be DELELTED).
		assertThat(instanceStates.size()).isEqualTo(2);
		
		// Verify the state of the first instance.
		InstanceState instanceState1 = instanceStates.get(instanceId1);
	    assertThat(instanceState1.getInstanceStatus()).isEqualTo(InstanceStatus.DELETED);

	    // Verify the state of the first instance.
	    InstanceState instanceState2 = instanceStates.get(instanceId2);
	    assertThat(instanceState2.getInstanceStatus()).isEqualTo(InstanceStatus.DELETED);
	}

	@Test
	public void testDelete_TwoInstances() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);
		
		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getStatus()).thenReturn(Status.DELETED);
		
		Server server2 = mock(Server.class);
		when(server2.getId()).thenReturn(novaInstanceId2);
		when(server2.getStatus()).thenReturn(Status.DELETED);
		
		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		Map<String, String> meta2 = Maps.newHashMap();
		meta2.put("DIRECTOR_ID", instanceId2);
		when(server2.getMetadata()).thenReturn(meta2);
		
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1, server2)));
		when(serverApi.listInDetail()).thenReturn(servers);
		
		FloatingIP floatingIp1 = mock(FloatingIP.class);
		when(floatingIp1.getIp()).thenReturn(DEFAULT_FLOATING_IP1);
		when(floatingIp1.getId()).thenReturn(floatingIpId1);
		when(floatingIp1.getInstanceId()).thenReturn(novaInstanceId1);
		
		FloatingIP floatingIp2 = mock(FloatingIP.class);
		when(floatingIp2.getIp()).thenReturn(DEFAULT_FLOATING_IP2);
		when(floatingIp2.getId()).thenReturn(floatingIpId2);
		when(floatingIp2.getInstanceId()).thenReturn(novaInstanceId2);
		
		FluentIterable<FloatingIP> floatingIps = FluentIterable
				.from(Lists.newArrayList(floatingIp1, floatingIp2));
		when(floatingIpApi.list()).thenReturn(floatingIps);
		doNothing().when(floatingIpApi).delete(floatingIpId1);
		doNothing().when(floatingIpApi).delete(floatingIpId2);
		
		when(serverApi.delete(novaInstanceId1)).thenReturn(true);
		when(serverApi.delete(novaInstanceId2)).thenReturn(true);
		
		novaProvider.delete(template, instanceIds);
		
		//Verify instance deletings were called.
		verify(serverApi).delete(eq(novaInstanceId1));
		verify(serverApi).delete(eq(novaInstanceId2));
		
		//Verify floating IP deletings were called.
		verify(floatingIpApi).delete(eq(floatingIpId1));
		verify(floatingIpApi).delete(eq(floatingIpId2));
	}
	
	@Test
	public void testDelete_TwoInstances_PartialSuccess() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		Address address1 = mock(Address.class);
		when(address1.getAddr()).thenReturn(DEFAULT_PRIVATE_IP1);
		when(address1.getVersion()).thenReturn(4);
		Address floatingAddress1 = mock(Address.class);
		when(floatingAddress1.getAddr()).thenReturn(DEFAULT_FLOATING_IP1);
		when(floatingAddress1.getVersion()).thenReturn(4);
		Multimap<String, Address> addresses1 = ArrayListMultimap.create();
		addresses1.put("1", address1);
		addresses1.put("2", floatingAddress1);

		Server server1 = mock(Server.class);
		when(server1.getId()).thenReturn(novaInstanceId1);
		when(server1.getAddresses()).thenReturn(addresses1);
				
		Map<String, String> meta1 = Maps.newHashMap();
		meta1.put("DIRECTOR_ID", instanceId1);
		when(server1.getMetadata()).thenReturn(meta1);
		
		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList(server1)));
		when(serverApi.listInDetail()).thenReturn(servers);
		
		FloatingIP floatingIp1 = mock(FloatingIP.class);
		when(floatingIp1.getIp()).thenReturn(DEFAULT_FLOATING_IP1);
		when(floatingIp1.getId()).thenReturn(floatingIpId1);
		when(floatingIp1.getInstanceId()).thenReturn(novaInstanceId1);
		
		FloatingIP floatingIp2 = mock(FloatingIP.class);
		when(floatingIp2.getIp()).thenReturn(DEFAULT_FLOATING_IP2);
		when(floatingIp2.getId()).thenReturn(floatingIpId2);
		when(floatingIp2.getInstanceId()).thenReturn(novaInstanceId2);
		
		FluentIterable<FloatingIP> floatingIps = FluentIterable
				.from(Lists.newArrayList(floatingIp1, floatingIp2));
		when(floatingIpApi.list()).thenReturn(floatingIps);
		doNothing().when(floatingIpApi).delete(floatingIpId1);
		
		when(serverApi.delete(novaInstanceId1)).thenReturn(true);
		
		novaProvider.delete(template, instanceIds);
		
		//Verify only instance1 deleting was called.
		verify(serverApi).delete(eq(novaInstanceId1));
		verify(serverApi, times(0)).delete(eq(novaInstanceId2));
		
		//Verify floating IP of instance1 deleting was called.
		verify(floatingIpApi).delete(floatingIpId1);
		verify(floatingIpApi, times(0)).delete(floatingIpId2);
	}
	
	@Test
	public void testDelete_TwoInstances_NotExisting() throws InterruptedException, IOException {
		// Create the resource template.
		NovaInstanceTemplate template = novaProvider.createResourceTemplate("template-1",
				new SimpleConfiguration(templateConfig), new HashMap<String, String>());

		// Configure stub for successful server create method.
		Collection<String> instanceIds = Lists.newArrayList();
		instanceIds.add(instanceId1);
		instanceIds.add(instanceId2);

		PagedIterable<Server> servers = PagedIterables
				.onlyPage(IterableWithMarkers.from(Lists.newArrayList()));
		when(serverApi.listInDetail()).thenReturn(servers);
		
		FloatingIP floatingIp1 = mock(FloatingIP.class);
		when(floatingIp1.getIp()).thenReturn(DEFAULT_FLOATING_IP1);
		when(floatingIp1.getId()).thenReturn(floatingIpId1);
		when(floatingIp1.getInstanceId()).thenReturn(novaInstanceId1);
		
		FloatingIP floatingIp2 = mock(FloatingIP.class);
		when(floatingIp2.getIp()).thenReturn(DEFAULT_FLOATING_IP2);
		when(floatingIp2.getId()).thenReturn(floatingIpId2);
		when(floatingIp2.getInstanceId()).thenReturn(novaInstanceId2);
		
		FluentIterable<FloatingIP> floatingIps = FluentIterable
				.from(Lists.newArrayList(floatingIp1, floatingIp2));
		when(floatingIpApi.list()).thenReturn(floatingIps);
		doNothing().when(floatingIpApi).delete(floatingIpId1);
		
		when(serverApi.delete(novaInstanceId1)).thenReturn(true);
		
		novaProvider.delete(template, instanceIds);
				
		//Verify no instance deleting was called.
		verify(serverApi, times(0)).delete(eq(novaInstanceId1));
		verify(serverApi, times(0)).delete(eq(novaInstanceId2));
		
		//Verify no floating IP deleting was called.
		verify(floatingIpApi, times(0)).delete(floatingIpId1);
		verify(floatingIpApi, times(0)).delete(floatingIpId2);
	}

}

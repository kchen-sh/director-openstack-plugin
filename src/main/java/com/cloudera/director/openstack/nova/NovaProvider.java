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
package com.cloudera.director.openstack.nova;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jclouds.ContextBuilder;
import org.jclouds.apis.ApiMetadata;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.cinder.v1.CinderApi;
import org.jclouds.openstack.cinder.v1.CinderApiMetadata;
import org.jclouds.openstack.cinder.v1.domain.Volume;
import org.jclouds.openstack.cinder.v1.features.VolumeApi;
import org.jclouds.openstack.cinder.v1.options.CreateVolumeOptions;
import org.jclouds.openstack.cinder.v1.predicates.VolumePredicates;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.domain.Address;
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
import org.jclouds.openstack.nova.v2_0.predicates.ServerPredicates;
import org.jclouds.openstack.v2_0.domain.PaginatedCollection;
import org.jclouds.openstack.v2_0.domain.Resource;
import org.jclouds.openstack.v2_0.options.PaginationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import com.cloudera.director.openstack.OpenStackCredentials;
import com.cloudera.director.spi.v1.compute.util.AbstractComputeProvider;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.InstanceState;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.Resource.Type;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionDetails;
import com.cloudera.director.spi.v1.model.exception.UnrecoverableProviderException;
import com.cloudera.director.spi.v1.model.util.SimpleResourceTemplate;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.cloudera.director.spi.v1.provider.util.SimpleResourceProviderMetadata;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Module;
import com.typesafe.config.Config;

public class NovaProvider extends AbstractComputeProvider<NovaInstance, NovaInstanceTemplate> {

	private static final Logger LOG = LoggerFactory.getLogger(NovaProvider.class);
	
	private static final ApiMetadata NOVA_API_METADATA = new NovaApiMetadata();
	private static final ApiMetadata CINDER_API_METADATA = new CinderApiMetadata();

	private static final String VOLUME_DESCRIPTION = "SSD";
	/**
	 * The provider configuration properties.
	 */	
	protected static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES =
		ConfigurationPropertiesUtil.asConfigurationPropertyList(
			NovaProviderConfigurationProperty.values());
	
	/**
	 * The resource provider ID.
	 */
	public static final String ID = NovaProvider.class.getCanonicalName();
	
	/**
	 * The resource provider metadata.
	 */
	public static final ResourceProviderMetadata METADATA = SimpleResourceProviderMetadata.builder()
		.id(ID)
		.name("Nova")
		.description("OpenStack Nova compute provider")
		.providerClass(NovaProvider.class)
		.providerConfigurationProperties(CONFIGURATION_PROPERTIES)
		.resourceTemplateConfigurationProperties(NovaInstanceTemplate.getConfigurationProperties())
		.resourceDisplayProperties(NovaInstance.getDisplayProperties())
		.build();
	
	/*
	 * The credentials of the OpenStack environment
	 */
	private OpenStackCredentials credentials;
	
	/*
	 * The configuration of the OpenStack environment
	 */
	@SuppressWarnings("unused")
	private Config openstackConfig;
	
	/*
	 * The nova api for OpenStack Nova service
	 */
	private final NovaApi novaApi;
	
	/*
	 * The cinder api for OpenStack Cinder service
	 */
	private final CinderApi cinderApi;

	/*
	 * Region of the provider
	 */
	private String region;
	
	public NovaProvider(Configured configuration, OpenStackCredentials credentials,
			Config openstackConfig, LocalizationContext localizationContext) {
		super(configuration, METADATA, localizationContext);
		this.credentials = credentials;
		this.openstackConfig = openstackConfig;
		this.novaApi = buildNovaAPI();
		this.cinderApi = buildCinderAPI();
		this.region = configuration.getConfigurationValue(REGION, localizationContext);
	}
	
	public NovaApi getNovaApi() {
		return novaApi;
	}
	
	public CinderApi getCinderApi() {
		return cinderApi;
	}

	public String getRegion() {
		return region;
	}
	
	private NovaApi buildNovaAPI() {
		Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
		String endpoint = credentials.getEndpoint();
		String identity = credentials.getIdentity();
		String credential = credentials.getCredential();

		return ContextBuilder.newBuilder(NOVA_API_METADATA)
			  .endpoint(endpoint)
			  .credentials(identity, credential)
			  .modules(modules)
			  .buildApi(NovaApi.class);
	}

	private CinderApi buildCinderAPI() {
		Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
		String endpoint = credentials.getEndpoint();
		String identity = credentials.getIdentity();
		String credential = credentials.getCredential();
		
		return ContextBuilder.newBuilder(CINDER_API_METADATA)
			  .endpoint(endpoint)
			  .credentials(identity, credential)
			  .modules(modules)
			  .buildApi(CinderApi.class);
	}
	
	public NovaInstanceTemplate createResourceTemplate(String name,
			Configured configuration, Map<String, String> tags) {
		return new NovaInstanceTemplate(name, configuration, tags, this.getLocalizationContext());
	}
	
	private FloatingIP createAndAssignFloatingIP(FloatingIPApi floatingIpApi,
			String floatingIpPool, String instanceId) {
		PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();
		try {
			FloatingIP floatingIp = floatingIpApi.allocateFromPool(floatingIpPool);
			String fltip = floatingIp.getIp();
			int retryNum = 10;
			// We need to check whether floating IP was created.
			while (fltip.isEmpty() && retryNum > 0) {
				TimeUnit.SECONDS.sleep(5);
				fltip = floatingIp.getIp();
				retryNum--;
			}
			floatingIpApi.addToServer(fltip, instanceId);
			// AddToServer does not have return value, so we have to check whether 
			// floating IP was successfully associated.
			while (floatingIp.getInstanceId() == null || floatingIp.getInstanceId().isEmpty()) {
				TimeUnit.SECONDS.sleep(5);
				floatingIpApi.addToServer(fltip, instanceId);
				retryNum--;
			}
			if (fltip.isEmpty() || floatingIp.getInstanceId() == null ||
					floatingIp.getInstanceId().isEmpty() ||
					!floatingIp.getInstanceId().equals(instanceId) ) {
				floatingIpApi.delete(floatingIp.getId());
				return null;
			}
			return floatingIp;
		} catch (Exception e) {
			accumulator.addError(null, e.getMessage());
			return null;
		}
	}

	public void allocate(NovaInstanceTemplate template, Collection<String> instanceIds,
			int minCount) throws InterruptedException {
		LocalizationContext providerLocalizationContext = getLocalizationContext();
		LocalizationContext templateLocalizationContext =
				SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);
		
		// Provisioning the cluster
		NovaApi novaApi = getNovaApi();
		CinderApi cinderApi = getCinderApi();
		String region = getRegion();
		ServerApi serverApi = novaApi.getServerApi(region);

		final Set<String> instancesWithNoPrivateIp = Sets.newHashSet();
		final Set<String> instancesWithPrivateIp = Sets.newHashSet();
		final Set<String> floatingIps = Sets.newHashSet();
		
		String image = template.getConfigurationValue(IMAGE, templateLocalizationContext);
		String flavorName = template.getConfigurationValue(TYPE, templateLocalizationContext);
		String network = template.getConfigurationValue(NETWORK_ID, templateLocalizationContext);
		String azone = template.getConfigurationValue(AVAILABILITY_ZONE, templateLocalizationContext);
		String securityGroups = template.getConfigurationValue(SECURITY_GROUP_NAMES, templateLocalizationContext);
		String keyName = template.getConfigurationValue(KEY_NAME, templateLocalizationContext);
		String floatingIpPool = template.getConfigurationValue(FLOATING_IP_POOL, templateLocalizationContext);
		List<String> securityGroupNames = NovaInstanceTemplate.CSV_SPLITTER.splitToList(securityGroups);
		int volumeNumber = Integer.parseInt(template.getConfigurationValue(VOLUME_NUMBER, templateLocalizationContext));
		int volumeSize = Integer.parseInt(template.getConfigurationValue(VOLUME_SIZE, templateLocalizationContext));
		String flavorId = getFlavorIDByName(flavorName);

		if (volumeNumber > 0 && volumeSize > 0) {
			// If volume number and volume size are > 0, we will verify whether
			// VolumeAttachmentApi presents. If not we will not continue.
			Optional<VolumeAttachmentApi> volumeAttApi = novaApi.getVolumeAttachmentApi(region);
			if (!volumeAttApi.isPresent()) {
				throw new UnrecoverableProviderException("Volume Attachment API does not exist.");
			}			
		}
		if (floatingIpPool != null && !floatingIpPool.isEmpty()) {
			// If floatingIpPool is not empty, verify whether
			// floatingIpApi and flotingipPool present. If not we will not continue.
			Optional<FloatingIPApi> floatingIpApi = novaApi.getFloatingIPApi(region);
			if (!floatingIpApi.isPresent()) {
				throw new UnrecoverableProviderException("FloatingIp API does not exist.");
			}
			Optional<FloatingIPPoolApi> floatingIpPoolApi = novaApi.getFloatingIPPoolApi(region);
			if (!floatingIpPoolApi.isPresent()) {
				throw new UnrecoverableProviderException("FloatingIpPool API does not exist.");
			}
			FluentIterable<? extends FloatingIPPool> fltIpPool = floatingIpPoolApi.get().list();
			boolean poolExists = false;
			for (FloatingIPPool pool : fltIpPool) {
				if (pool.getName().equals(floatingIpPool)) {
					poolExists = true;
					break;
				}
			}
			if (!poolExists) {
				throw new UnrecoverableProviderException("FloatingIpPool does not exist.");
			}
		}

		for (String currentId : instanceIds) {
			String decoratedInstanceName = decorateInstanceName(template, currentId, templateLocalizationContext);

			// Tag all the new instances so that we can easily find them later on
			Map<String, String> tags = new HashMap<String, String>();
			tags.put("DIRECTOR_ID", currentId);
			tags.put("INSTANCE_NAME", decoratedInstanceName);
			tags.put("VOLUME_NUMBER", Integer.toString(volumeNumber));
			tags.put("VOLUME_SIZE", Integer.toString(volumeSize));

			CreateServerOptions createServerOps = new CreateServerOptions()
								.keyPairName(keyName)
								.networks(network)
								.availabilityZone(azone)
								.securityGroupNames(securityGroupNames)
								.metadata(tags);
			
			ServerCreated currentServer = serverApi.create(decoratedInstanceName, image, flavorId, createServerOps);
			
			String novaInstanceId = currentServer.getId();
			while (novaInstanceId.isEmpty()) {
				TimeUnit.SECONDS.sleep(5);
				novaInstanceId = currentServer.getId();
			}
			
			if (serverApi.get(novaInstanceId).getAddresses() == null) {
				instancesWithNoPrivateIp.add(novaInstanceId);
			} else {
				if (floatingIpPool != null && !floatingIpPool.isEmpty()) {
					// We already check floatingIpApi exists above. So the get will not fail.
					FloatingIPApi floatingIpApi = novaApi.getFloatingIPApi(region).get();
					FloatingIP fltip = createAndAssignFloatingIP(floatingIpApi, floatingIpPool, novaInstanceId);
					if (fltip != null) {
						floatingIps.add(fltip.getId());
					}
				}
				LOG.info("<< Instance {} got IP {}", novaInstanceId, serverApi.get(novaInstanceId).getAccessIPv4());
				instancesWithPrivateIp.add(novaInstanceId);
			}
		}
		
		// Wait until all of them to have a private IP
		int totalTimePollingSeconds = 0;
		int pollingTimeoutSeconds = 180;
		boolean timeoutExceeded = false;
		while (!instancesWithNoPrivateIp.isEmpty() && !timeoutExceeded) {
			LOG.info(">> Waiting for {} instance(s) to be active",
					instancesWithNoPrivateIp.size());
			
			for (String novaInstanceId : instancesWithNoPrivateIp) {
				if (serverApi.get(novaInstanceId).getAddresses() != null) {
					instancesWithNoPrivateIp.remove(novaInstanceId);
					instancesWithPrivateIp.add(novaInstanceId);
					if (floatingIpPool != null && !floatingIpPool.isEmpty()) {
						FloatingIPApi floatingIpApi = novaApi.getFloatingIPApi(region).get();
						FloatingIP fltip = createAndAssignFloatingIP(floatingIpApi, floatingIpPool, novaInstanceId);
						if (fltip != null) {
							floatingIps.add(fltip.getId());
						}
					}
				}
			}
			
			if (!instancesWithNoPrivateIp.isEmpty()) {
				LOG.info("Waiting 5 seconds until next check, {} instance(s) still don't have an IP",
						instancesWithNoPrivateIp.size());
				
				if (totalTimePollingSeconds > pollingTimeoutSeconds) {
					timeoutExceeded = true;
				}
				TimeUnit.SECONDS.sleep(5);
				totalTimePollingSeconds += 5;
			}
		}
		
		int successfulOperationCount = instanceIds.size() - instancesWithNoPrivateIp.size();
		if (floatingIpPool != null && !floatingIpPool.isEmpty()) {
			successfulOperationCount = Math.min(successfulOperationCount, floatingIps.size());
		}
		if (successfulOperationCount < minCount) {
			// Instance number does not meet the requirement. Delete instances
			// and floating IPs if existing.
			PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();
			BiMap<String, String> virtualInstanceIdsByNovaInstanceId = 
					getNovaInstanceIdsByVirtualInstanceId(instanceIds);
			
			for (String currentId : instanceIds) {
				try{
					String novaInstanceId = virtualInstanceIdsByNovaInstanceId.get(currentId);
					serverApi.delete(novaInstanceId);
				} catch (Exception e) {
					accumulator.addError(null, e.getMessage());
				}
			}
			if (floatingIpPool != null && !floatingIpPool.isEmpty()) {
				FloatingIPApi floatingIpApi = novaApi.getFloatingIPApi(region).get();
				for (String fltId : floatingIps) {
					try{
						floatingIpApi.delete(fltId);
					} catch (Exception e) {
						accumulator.addError(null, e.getMessage());
					}
				}
			}
			PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
			throw new UnrecoverableProviderException("Problem allocating instances.", pluginExceptionDetails);
		}
		if (volumeNumber > 0 && volumeSize > 0 && instancesWithPrivateIp.size() > 0) {
			// Need to allocate volumes for instances.
			// Wait all instances to be in "ACTIVE" status.
			for (String insId : instancesWithPrivateIp) {
				ServerPredicates.awaitStatus(serverApi, Status.ACTIVE, 120, 5).apply(insId);
			}
			
			LOG.info("Need to allocate {} volumes for each instances.", volumeNumber);
			
			VolumeApi volumeApi = cinderApi.getVolumeApi(region);
			// We have already confirmed volumeAttApi exists, so get will not fail.
			VolumeAttachmentApi volumeAttachApi = novaApi.getVolumeAttachmentApi(region).get();
			final List<String> volumeIds = new ArrayList<String>();
			
			//Create all volumes before attaching them to save time.
			int vol_to_create = volumeNumber * instancesWithPrivateIp.size();
			LOG.info(">> Start to create {} volumes for the instances.", vol_to_create);
			for (int i = 0; i < vol_to_create; i++) {
				CreateVolumeOptions createVolOps = CreateVolumeOptions.Builder
						.description(VOLUME_DESCRIPTION)
						.availabilityZone(azone);
				Volume currentVolume = volumeApi.create(volumeSize, createVolOps);
				volumeIds.add(currentVolume.getId());
			}
			
			LOG.info(">> Waiting for {} instance(s) to be attached by volumes.",
					instancesWithPrivateIp.size());
			final List<String> activeVolIds = new ArrayList<String>();
			for (String insId: instancesWithPrivateIp) {				
				for (int i = 0; i < volumeNumber; i++) {
					// Get the first volume, and shift the list.
					String volId = volumeIds.get(0);
					volumeIds.remove(volId);
					// We do not set the device so that the devices could be set automatically.
					String device = "";
					Volume currentVolume = volumeApi.get(volId);
					// Wait until Available. The default awaitAvailable wait time is too long (10min).
					// If not success, we delete it, and regenerate a new volId.
					boolean createdSuccess = false;
					if (VolumePredicates.awaitStatus(volumeApi, Volume.Status.AVAILABLE, 60, 5).apply(currentVolume)) {
						activeVolIds.add(volId);
						createdSuccess = true;
					}
					else{
						boolean volDeleted = volumeApi.delete(volId);
						if (volDeleted) {
							CreateVolumeOptions createVolOps = CreateVolumeOptions.Builder
									.description(VOLUME_DESCRIPTION)
									.availabilityZone(azone);
							currentVolume = volumeApi.create(volumeSize, createVolOps);
							volId = currentVolume.getId();
							if (VolumePredicates.awaitStatus(volumeApi, Volume.Status.AVAILABLE, 60, 5).apply(currentVolume)) {
								activeVolIds.add(volId);
								createdSuccess = true;
							}
						}
					}
					if (!createdSuccess) {
						// Delete the volume. Instance will be deleted later.
						volumeApi.delete(volId);
						instancesWithPrivateIp.remove(insId);
						LOG.info("Time out on Volume: " + volId);
					}
					else {
						volumeAttachApi.attachVolumeToServerAsDevice(volId, insId, device);
						// Wait until In-use.
						if (!VolumePredicates.awaitStatus(volumeApi, Volume.Status.IN_USE, 60, 5).apply(currentVolume)) {
							// Attach fail. Delete the volume. Instance will be deleted later.
							boolean volDeleted = volumeApi.delete(volId);
							if (volDeleted) {
								activeVolIds.remove(volId);
							}
							instancesWithPrivateIp.remove(insId);
							LOG.info("Time out on Volume: " + volId);
						}
					}
				}
			}
			//if instances with private IP and volumes do not meet the minCount, delete all of them.
			if (instancesWithPrivateIp.size() < minCount) {
				PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();
				BiMap<String, String> virtualInstanceIdsByNovaInstanceId =
						getNovaInstanceIdsByVirtualInstanceId(instanceIds);

				for (String currentId : instanceIds) {
					try{
						String novaInstanceId = virtualInstanceIdsByNovaInstanceId.get(currentId);
						serverApi.delete(novaInstanceId);
					} catch (Exception e) {
						accumulator.addError(null, e.getMessage());
					}
				}
				for (String volId: activeVolIds) {
					try{
						volumeApi.delete(volId);
					} catch (Exception e) {
						accumulator.addError(null, e.getMessage());
					}
				}
				PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
				throw new UnrecoverableProviderException("Problem allocating instances and volumes.", pluginExceptionDetails);
			}
		}
	}
	
	private String findFloatingIPByAddress(FloatingIPApi floatingIpApi, String floatingIp) {
		FluentIterable<FloatingIP> floatingipList = floatingIpApi.list();
		for ( FloatingIP ip : floatingipList) {
			if (ip.getIp().compareTo(floatingIp) == 0) {
				return ip.getId();
			}
		}
		return null;
	}

	private String getFlavorIDByName(String flavorName) {
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		FlavorApi flavorApi = novaApi.getFlavorApi(region);
		FluentIterable<Resource> flavorList = flavorApi.list().concat();
		for (Resource flavor : flavorList) {
			if (flavor.getName().compareTo(flavorName) == 0 ) {
				return flavor.getId();
			}
		}
		return null; 
	}

	public void delete(NovaInstanceTemplate template, Collection<String> virtualInstanceIds)
			throws InterruptedException {
		if (virtualInstanceIds.isEmpty()) {
			return;
		}
		
		LocalizationContext providerLocalizationContext = getLocalizationContext();
		LocalizationContext templateLocalizationContext =
			SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);
		int volumeNumber = Integer.parseInt(template.getConfigurationValue(VOLUME_NUMBER, templateLocalizationContext));

		NovaApi novaApi = getNovaApi();
		CinderApi cinderApi = getCinderApi();
		String region = getRegion();
		BiMap<String, String> virtualInstanceIdsByNovaInstanceId = 
				getNovaInstanceIdsByVirtualInstanceId(virtualInstanceIds);

		Set<String> novaIds = Sets.newHashSet();
		for (String currentId : virtualInstanceIds) {
			String novaInstanceId = virtualInstanceIdsByNovaInstanceId.get(currentId);
			novaIds.add(novaInstanceId);
		}

		// Get all volumes attached to the instances.
		Set<String> volumeIds = Sets.newHashSet();
		if (volumeNumber > 0) {
			Optional<VolumeAttachmentApi> volumeAttApi = novaApi.getVolumeAttachmentApi(region);
			if (!volumeAttApi.isPresent()) {
				throw new UnrecoverableProviderException("Volume Attachment APIs do not exist.");
			}
			VolumeAttachmentApi volumeAttachApi = volumeAttApi.get();
			for (String novaId : novaIds) {
				FluentIterable<VolumeAttachment> vol_attachs = volumeAttachApi.listAttachmentsOnServer(novaId);
				for (VolumeAttachment vol_attach : vol_attachs) {
					volumeIds.add(vol_attach.getVolumeId());
				}
			}
		}

		ServerApi serverApi = novaApi.getServerApi(region);
		Optional<FloatingIPApi> floatingIpApi = novaApi.getFloatingIPApi(region);

		for (String currentId : virtualInstanceIds) {
			String novaInstanceId = virtualInstanceIdsByNovaInstanceId.get(currentId);
			//find the floating IP address if it exists
			String floatingIp = null;
			Iterator<Address> iterator = serverApi.get(novaInstanceId).getAddresses().values().iterator();
			if (iterator.hasNext()) {
				//discard the first one (the fixed IP)
				iterator.next();
				if (iterator.hasNext()) {
					floatingIp = iterator.next().getAddr();
				}
			}

			// Disassociate and delete the floating IP.
			if (floatingIp != null && !floatingIp.isEmpty()) {
				if (floatingIpApi.isPresent()) {
					String floatingipID = findFloatingIPByAddress(floatingIpApi.get(), floatingIp);
					floatingIpApi.get().removeFromServer(floatingIp, novaInstanceId);
					floatingIpApi.get().delete(floatingipID);
				}
			}
			
			// Delete the server
			boolean deleted = serverApi.delete(novaInstanceId);
			if (!deleted) {
				LOG.info("Unable to terminate instance {}", novaInstanceId);
			}
		}
		
		// Delete the volumes.
		if (volumeIds.size() > 0) {
			Set<Volume> volumes = Sets.newHashSet();
			VolumeApi  volumeApi = cinderApi.getVolumeApi(region);
			for (String volId : volumeIds) {
				// Wait until volumes available.
				Volume currentVolume = volumeApi.get(volId);
				if (!VolumePredicates.awaitStatus(volumeApi, Volume.Status.AVAILABLE, 60, 5).apply(currentVolume)) {
					LOG.info("Volume {} is not ready for delete.", currentVolume.getId());
				}
				else {
					volumes.add(currentVolume);
					// Delete the volume.
					boolean deleted = volumeApi.delete(volId);
					if (!deleted) {
						LOG.info("Unable to delete volume {}.", currentVolume.getId());
					}
				}
			}
			for (Volume currentVolume : volumes) {
				// Wait until the volume deleted.
				if (!VolumePredicates.awaitDeleted(volumeApi).apply(currentVolume)) {
					LOG.info("Unable to delete volume {}.", currentVolume.getId());
				}
			}
		}
	}

	public Collection<NovaInstance> find(NovaInstanceTemplate template,
			Collection<String> virtualInstanceIds) throws InterruptedException {
		
		NovaApi novaApi = getNovaApi();
		String region = getRegion();

		final Collection<NovaInstance> novaInstances =
				Lists.newArrayListWithExpectedSize(virtualInstanceIds.size());
		BiMap<String, String> virtualInstanceIdsByNovaInstanceId = 
				getNovaInstanceIdsByVirtualInstanceId(virtualInstanceIds);
		
		ServerApi serverApi = novaApi.getServerApi(region);
		
		for (String currentId : virtualInstanceIds) {
			String novaInstanceId = virtualInstanceIdsByNovaInstanceId.get(currentId);
			novaInstances.add(new NovaInstance(template, currentId, serverApi.get(novaInstanceId)));
		}
		
		return novaInstances;
	}

	public Map<String, InstanceState> getInstanceState(NovaInstanceTemplate template, 
			Collection<String> virtualInstanceIds) {

		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		
		Map<String, InstanceState> instanceStateByInstanceId = new HashMap<String, InstanceState >();
		
		BiMap<String, String> virtualInstanceIdsByNovaInstanceId = 
				getNovaInstanceIdsByVirtualInstanceId(virtualInstanceIds);
		  
		for (String currentId : virtualInstanceIds) {
			String novaInstanceId = virtualInstanceIdsByNovaInstanceId.get(currentId);
			if (novaInstanceId == null) {
				InstanceState instanceStateDel = NovaInstanceState.fromInstanceStateName(Status.DELETED);
				instanceStateByInstanceId.put(currentId, instanceStateDel);
				continue;	
			}
			Status instance_state =  novaApi.getServerApi(region).get(novaInstanceId).getStatus();
			InstanceState instanceState = NovaInstanceState.fromInstanceStateName(instance_state);
			instanceStateByInstanceId.put(currentId, instanceState);
		}
		
		return instanceStateByInstanceId;
	}	

	public Type getResourceType() {
		return NovaInstance.TYPE;
	}
	
	private static String decorateInstanceName(NovaInstanceTemplate template, String currentId,
			  LocalizationContext templateLocalizationContext){
		return template.getInstanceNamePrefix() + "-" + currentId;
	}
	
	/**
	 * Returns a map from virtual instance ID to corresponding instance ID for the specified
	 * virtual instance IDs.
	 *
	 * @param virtualInstanceIds the virtual instance IDs
	 * @return the map from virtual instance ID to corresponding Nova instance ID
	 */
	private BiMap<String, String> getNovaInstanceIdsByVirtualInstanceId(
		  Collection<String> virtualInstanceIds) {

		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		final BiMap<String, String> novaInstanceIdsByVirtualInstanceId = HashBiMap.create();
		for (String instanceName : virtualInstanceIds) {
			ListMultimap<String, String> multimap = ArrayListMultimap.create();
			multimap.put("name", instanceName) ;
			ServerApi serverApi = novaApi.getServerApi(region);
			PaginatedCollection<Server> servers = serverApi.listInDetail(PaginationOptions.Builder.queryParameters(multimap));
			if (servers.isEmpty()) {
				continue;
			}
			novaInstanceIdsByVirtualInstanceId.put(instanceName, servers.get(0).getId());	
		}
		
		return novaInstanceIdsByVirtualInstanceId;
	}
}


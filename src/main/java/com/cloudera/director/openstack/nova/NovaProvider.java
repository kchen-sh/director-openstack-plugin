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
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.NovaApiMetadata;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIP;
import org.jclouds.openstack.nova.v2_0.domain.FloatingIPPool;
import org.jclouds.openstack.nova.v2_0.domain.Server;
import org.jclouds.openstack.nova.v2_0.domain.Server.Status;
import org.jclouds.openstack.nova.v2_0.domain.ServerCreated;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPApi;
import org.jclouds.openstack.nova.v2_0.extensions.FloatingIPPoolApi;
import org.jclouds.openstack.nova.v2_0.extensions.VolumeAttachmentApi;
import org.jclouds.openstack.nova.v2_0.features.FlavorApi;
import org.jclouds.openstack.nova.v2_0.features.ServerApi;
import org.jclouds.openstack.nova.v2_0.options.CreateServerOptions;
import org.jclouds.openstack.v2_0.domain.Resource;
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
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
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
	
	private void VerifyVolumeAttachementApi() {
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		Optional<VolumeAttachmentApi> volumeAttApi = novaApi.getVolumeAttachmentApi(region);
		if (!volumeAttApi.isPresent()) {
			throw new UnrecoverableProviderException("Volume Attachment API does not exist.");
		}			
	}
	
	// We do not use ServerPredicates and VolumePredicates for
	// we feel it is not easy to use.
	private Boolean pollServerStatus(String novaInstanceId, Status status,
			long maxWaitInSec, long preiodInSec,
			PluginExceptionConditionAccumulator accumulator) {
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		ServerApi serverApi = novaApi.getServerApi(region);
		try {
			while (maxWaitInSec > 0) {
				Server server = serverApi.get(novaInstanceId); 
				if (server == null) {
					if (status.equals(Status.DELETED)) {
						return true;
					}
					else {
						return false;
					}
				}
				if (server.getStatus().equals(status)) {
					return true;
				}
				maxWaitInSec -= preiodInSec;
				TimeUnit.SECONDS.sleep(preiodInSec);
			}
		} catch (Exception e) {
			accumulator.addError(null, e.getMessage());
		}
		return false;
	}

	private Boolean pollVolumeStatus(String volumeId, Volume.Status status,
			long maxWaitInSec, long preiodInSec,
			PluginExceptionConditionAccumulator accumulator) {
		CinderApi cinderApi = getCinderApi();
		String region = getRegion();
		VolumeApi volumeApi = cinderApi.getVolumeApi(region);
		try {
			while (maxWaitInSec > 0) {
				// There is not Volume.Status.DELELTED.
				Volume volume = volumeApi.get(volumeId);
				if (volume!=null && volume.getStatus().equals(status)) {
					return true;
				}
				maxWaitInSec -= preiodInSec;
				TimeUnit.SECONDS.sleep(preiodInSec);
			}
		} catch (Exception e) {
			accumulator.addError(null, e.getMessage());
		}
		return false;
	}

	private Boolean pollVolumeDeleted(String volumeId,
			long maxWaitInSec, long preiodInSec,
			PluginExceptionConditionAccumulator accumulator) {
		CinderApi cinderApi = getCinderApi();
		String region = getRegion();
		VolumeApi volumeApi = cinderApi.getVolumeApi(region);
		try {
			while (maxWaitInSec > 0) {
				if (volumeApi.get(volumeId) == null) {
					return true;
				}
				maxWaitInSec -= preiodInSec;
				TimeUnit.SECONDS.sleep(preiodInSec);
			}
		} catch (Exception e) {
			accumulator.addError(null, e.getMessage());
		}
		return false;
	}

	private void VerifyFloatingIPApis(String floatingIpPool) {
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
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
	
	private FloatingIP createAndAssignFloatingIP(String floatingIpPool, String instanceId,
			PluginExceptionConditionAccumulator accumulator) {
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		try {
			FloatingIPApi floatingIpApi = novaApi.getFloatingIPApi(region).get();
			FloatingIP floatingIp = floatingIpApi.allocateFromPool(floatingIpPool);
			String fltip = floatingIp.getIp();
			String floatingIpId = floatingIp.getId();
			int retryNum = 10;
			// We need to check whether floating IP was created.
			while (fltip.isEmpty() && retryNum > 0) {
				TimeUnit.SECONDS.sleep(5);
				fltip = floatingIp.getIp();
				retryNum--;
			}
			// Wait some time here, or it might fail for the instance network info is not ready.
			TimeUnit.SECONDS.sleep(10);
			floatingIpApi.addToServer(fltip, instanceId);
			// AddToServer does not have return value, so we have to check whether 
			// floating IP was successfully associated.
			floatingIp = floatingIpApi.get(floatingIpId); 
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
	
	private void releaseResources(int volumeNumber, int volumeSize,
			String floatingIpPool,
			Collection<String> instanceIds,
			Collection<String> fltIpIds,
			PluginExceptionConditionAccumulator accumulator) {
		// This method releases all instances and volumes/floatingIPs related to the instanceIds.
		// And delete all other floating IPs in fltIpIds.
		NovaApi novaApi = getNovaApi();
		CinderApi cinderApi = getCinderApi();
		String region = getRegion();
		ServerApi serverApi = novaApi.getServerApi(region);
		Optional<FloatingIPApi> floatingIpApi = novaApi.getFloatingIPApi(region);

		BiMap<String, String> novaInstanceIdsByInstanceIds =
				getNovaInstanceIdsByInstanceIds(instanceIds);

		// Delete the floating IPs associated to the instances and in fltIpIds.
		if (floatingIpApi.isPresent() && floatingIpPool != null && !floatingIpPool.isEmpty()) {
			Set<String> floatingIpIds = getFloatingIPIdsByInstanceIds(instanceIds);
			if (fltIpIds != null && !fltIpIds.isEmpty()) {
				floatingIpIds.addAll(fltIpIds);
			}
			for (String floatingIpId : floatingIpIds) {
				floatingIpApi.get().delete(floatingIpId);
			}
		}
		
		if (instanceIds == null || instanceIds.isEmpty()) {
			// If instanceIds is empty, no need to do following things.
			return;
		}
		
		// Delete the instances.
		for (String currentId : instanceIds) {
			try{
				String novaInstanceId = novaInstanceIdsByInstanceIds.get(currentId);
				if (novaInstanceId != null) {
					serverApi.delete(novaInstanceId);
				}
			} catch (Exception e) {
				accumulator.addError(null, e.getMessage());
			}
		}
		
		//Make sure all instances deleted.
		for (String currentId : instanceIds) {
			try{
				String novaInstanceId = novaInstanceIdsByInstanceIds.get(currentId);
				if (novaInstanceId != null && !pollServerStatus(novaInstanceId, Status.DELETED, 120, 5, accumulator)) {
					LOG.info("Instance {} can not be deleted.", novaInstanceId);
				}
			} catch (Exception e) {
				accumulator.addError(null, e.getMessage());
			}
		}

		// Delete the volumes.
		if (volumeNumber > 0 && volumeSize > 0) {
			// Get all volumes by instanceIds.
			Collection<String> volumeIds = getVolumeIdsByInstanceIds(instanceIds);
			Set<String> volumeToDeleteIds = Sets.newHashSet();
			Set<String> deletingVolumeIds = Sets.newHashSet();
			Set<String> errorDeletingVolumeIds = Sets.newHashSet();
			VolumeApi  volumeApi = cinderApi.getVolumeApi(region);
			for (String volId : volumeIds) {
				// Wait until volumes available or error (ready for delete).
				Volume currentVolume = volumeApi.get(volId);
				long pollingTimeoutSeconds = 10;
				long checkInterval = 5;
				try {
					while (pollingTimeoutSeconds > 0) {
						Volume.Status volumeStatus = volumeApi.get(volId).getStatus();
						// For available or error, delete them.
						// For error_deleting, leave them.
						// For deleting, wait them deleted.
						if (volumeStatus == Volume.Status.AVAILABLE ||
								volumeStatus == Volume.Status.ERROR) {
							volumeToDeleteIds.add(currentVolume.getId());
							break;
						}
						if (volumeStatus == Volume.Status.DELETING) {
							deletingVolumeIds.add(currentVolume.getId());
							break;
						}
						if (volumeStatus == Volume.Status.ERROR_DELETING) {
							errorDeletingVolumeIds.add(currentVolume.getId());
							break;
						}
						// If not above cases, poll again.
						TimeUnit.SECONDS.sleep(checkInterval);
						pollingTimeoutSeconds -= checkInterval;
					}
				}
				catch (Exception e) {
					accumulator.addError(null, e.getMessage());
				}
			}

			for (String volumeId : volumeToDeleteIds) {
				// Delete the volume.
				boolean deleted = volumeApi.delete(volumeId);
				if (!deleted) {
					LOG.info("Unable to delete volume {}.", volumeId);
				}
			}
			for (String volumeId : volumeToDeleteIds) {
				// Wait until the volume deleted.
				if (!pollVolumeDeleted(volumeId, 600, 5, accumulator)) {
					LOG.info("Unable to delete volume {}.", volumeId);
				}
			}
			for (String volumeId : errorDeletingVolumeIds) {
				// Also log all undeletable volumes.
				LOG.info("Unable to delete volume {}.", volumeId);
			}
		}
	}
	
	public void allocate(NovaInstanceTemplate template, Collection<String> instanceIds,
			int minCount) throws InterruptedException {

		PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();
		// If we are not given enough instanceIds. Throw exception.
		if (instanceIds == null || instanceIds.isEmpty() || instanceIds.size() < minCount) {
			PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
			throw new UnrecoverableProviderException("No enough instanceIds.", pluginExceptionDetails);
		}

		LocalizationContext providerLocalizationContext = getLocalizationContext();
		LocalizationContext templateLocalizationContext =
				SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

		// Provisioning the cluster
		NovaApi novaApi = getNovaApi();
		CinderApi cinderApi = getCinderApi();
		String region = getRegion();
		ServerApi serverApi = novaApi.getServerApi(region);

		final Set<String> novaInstancesNotReady = Sets.newHashSet();
		final Set<String> novaInstancesReady = Sets.newHashSet();
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
			VerifyVolumeAttachementApi();
		}
		if (floatingIpPool != null && !floatingIpPool.isEmpty()) {
			// If floatingIpPool is not empty, verify whether
			// floatingIpApi and flotingipPool present. If not we will not continue.
			VerifyFloatingIPApis(floatingIpPool);
		}

		// For idempotency, we need to release resources first.
		releaseResources(volumeNumber, volumeSize, floatingIpPool, instanceIds, floatingIps, accumulator);
		
		for (String currentId : instanceIds) {
			// Create instance for each IntanceId (which is not the nova instance ID, but will be transferred to
			// Instance name).
			String decoratedInstanceName = decorateInstanceName(template, currentId);

			// Tag all the new instances so that we can easily find them later on
			Map<String, String> tags = new HashMap<String, String>();
			tags.put("DIRECTOR_ID", currentId);
			tags.put("VOLUME_NUMBER", Integer.toString(volumeNumber));
			tags.put("VOLUME_SIZE", Integer.toString(volumeSize));

			CreateServerOptions createServerOps = new CreateServerOptions()
								.keyPairName(keyName)
								.networks(network)
								.availabilityZone(azone)
								.securityGroupNames(securityGroupNames)
								.metadata(tags);
			
			ServerCreated currentServer = serverApi.create(decoratedInstanceName, image, flavorId, createServerOps);
			novaInstancesNotReady.add(currentServer.getId());
		}
		
		// Wait until all of them to have a private IP
		int totalTimePollingSeconds = 0;
		int pollingTimeoutSeconds = 10;
		boolean timeoutExceeded = false;
		while (!novaInstancesNotReady.isEmpty() && !timeoutExceeded) {
			LOG.info(">> Waiting for {} instance(s) to get Private IP",
					novaInstancesNotReady.size());
			List<String> tempList = Lists.newArrayList();
			for (String novaInstanceId : novaInstancesNotReady) {
				Server novaInstance = serverApi.get(novaInstanceId);
				if (novaInstance.getAddresses() != null) {
					tempList.add(novaInstanceId);
					LOG.info("<< Instance {} got IP {}", novaInstanceId, novaInstance.getAccessIPv4());
				}
			}
			
			for (String novaInstanceId : tempList) {
				novaInstancesReady.add(novaInstanceId);
				novaInstancesNotReady.remove(novaInstanceId);
			}
			
			if (!novaInstancesNotReady.isEmpty()) {
				LOG.info("Waiting 5 seconds until next check, {} instance(s) still don't have an IP",
						novaInstancesNotReady.size());
				
				if (totalTimePollingSeconds > pollingTimeoutSeconds) {
					timeoutExceeded = true;
				}
				TimeUnit.SECONDS.sleep(5);
				totalTimePollingSeconds += 5;
			}
		}

		if (floatingIpPool != null && !floatingIpPool.isEmpty()) {
			LOG.info(">> Waiting for {} instance(s) to get Floating IP",
					novaInstancesReady.size());
			List<String> tempList = Lists.newArrayList();
			for (String novaInstanceId : novaInstancesReady) {
				FloatingIP fltip = createAndAssignFloatingIP(floatingIpPool, novaInstanceId, accumulator);
				if (fltip != null) {
					floatingIps.add(fltip.getId());
				}
				else {
					tempList.add(novaInstanceId);
				}
			}
			for (String novaInstanceId : tempList) {
				novaInstancesReady.remove(novaInstanceId);
				novaInstancesNotReady.add(novaInstanceId);
			}			
		}
		
		int successfulOperationCount = instanceIds.size() - novaInstancesNotReady.size();
		if (successfulOperationCount < minCount) {
			// Instance number does not meet the requirement. Delete instances
			// and floating IPs if existing.
			// Release all resources already allocated.
			releaseResources(volumeNumber, volumeSize, floatingIpPool, instanceIds, floatingIps, accumulator);
			PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
			throw new UnrecoverableProviderException("Problem allocating instances.", pluginExceptionDetails);
		}
		else {
			// Just delete the fail ones.
			Collection<String> failInstances = Lists.newArrayList();
			for (String instanceId : novaInstancesNotReady) {
				failInstances.add(serverApi.get(instanceId).getMetadata().get("DIRECTOR_ID"));
			}
			releaseResources(volumeNumber, volumeSize, floatingIpPool, failInstances, null, accumulator);
		}
		
		if (volumeNumber > 0 && volumeSize > 0 && novaInstancesReady.size() > 0) {
			// Need to allocate volumes for instances.
			// Wait all instances to be in "ACTIVE" status.
			List<String> tempList = Lists.newArrayList();
			for (String novaInstanceId : novaInstancesReady) {
				if (!pollServerStatus(novaInstanceId, Status.ACTIVE, 120, 5, accumulator)) {
					tempList.add(novaInstanceId);
				}
			}
			for (String novaInstanceId : tempList) {
				// Add instance which cannot be ACTIVE to novaInstancesNotReady. 
				novaInstancesReady.remove(novaInstanceId);
				novaInstancesNotReady.add(novaInstanceId);
			}
			
			LOG.info("Need to allocate {} volumes for each instances.", volumeNumber);
			
			VolumeApi volumeApi = cinderApi.getVolumeApi(region);
			// We have already confirmed volumeAttApi exists, so get will not fail.
			VolumeAttachmentApi volumeAttachmentApi = novaApi.getVolumeAttachmentApi(region).get();
			
			//Create all volumes before attaching them to save time.
			Map<String, Collection<String>> volumeIdsByNovaInstanceIds = Maps.newHashMap();
			for (String novaInstanceId: novaInstancesReady) {
				LOG.info(">> Start to create {} volumes for the instances {}.", volumeNumber, novaInstanceId);
				Map<String, String> tags = new HashMap<String, String>();
				final List<String> volumeIds = new ArrayList<String>();
				String instanceId = serverApi.get(novaInstanceId).getMetadata().get("DIRECTOR_ID");
				tags.put("DIRECTOR_ID", instanceId);
				CreateVolumeOptions createVolOps = CreateVolumeOptions.Builder
						.description(VOLUME_DESCRIPTION)
						.availabilityZone(azone)
						.metadata(tags);
				for (int i = 0; i < volumeNumber; i++) {
					Volume currentVolume = volumeApi.create(volumeSize, createVolOps);
					volumeIds.add(currentVolume.getId());
				}
				volumeIdsByNovaInstanceIds.put(novaInstanceId, volumeIds);
			}
			
			LOG.info(">> Waiting for {} instance(s) to be attached by volumes.",
					novaInstancesReady.size());
			final List<String> activeVolIds = new ArrayList<String>();
			List<String> tempList1 = Lists.newArrayList();
			for (String novaInstanceId: novaInstancesReady) {
				Collection<String> involvedVolumeIds = volumeIdsByNovaInstanceIds.get(novaInstanceId);
				for (String volId : involvedVolumeIds) {
					// We do not set the device so that the devices could be set automatically.
					String device = "";
					// Wait until Available. The default awaitAvailable wait time is too long (10min).
					// If not success, we delete it, and regenerate a new volId.
					boolean createdSuccess = false;
					if (pollVolumeStatus(volId, Volume.Status.AVAILABLE, 30, 5, accumulator)) {
						activeVolIds.add(volId);
						createdSuccess = true;
					}
					if (!createdSuccess) {
						// Delete the volume. Instance will be deleted later.
						volumeApi.delete(volId);
						tempList1.add(novaInstanceId);
						LOG.info("Time out on Volume: " + volId);
					}
					else {
						// Attach the volume to the instance. 
						volumeAttachmentApi.attachVolumeToServerAsDevice(volId, novaInstanceId, device);
						// Wait until In-use.
						if (!pollVolumeStatus(volId, Volume.Status.IN_USE, 30, 5, accumulator)) {
							// Attach fail. Delete the volume. Instance will be deleted later.
							boolean volDeleted = volumeApi.delete(volId);
							if (volDeleted) {
								activeVolIds.remove(volId);
							}
							tempList1.add(novaInstanceId);
							LOG.info("Time out on Volume: " + volId);
						}
					}
				}
			}
			for (String novaInstanceId : tempList1) {
				novaInstancesReady.remove(novaInstanceId);
				novaInstancesNotReady.add(novaInstanceId);
			}

			if (novaInstancesReady.size() < minCount) {
				// If instances with private IP and volumes do not meet the minCount, delete all of them.
				releaseResources(volumeNumber, volumeSize, floatingIpPool, instanceIds, floatingIps, accumulator);
				PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
				throw new UnrecoverableProviderException("Problem allocating instances and volumes.", pluginExceptionDetails);
			}
			else {
				// Just delete the fail ones.
				Collection<String> failInstances = Lists.newArrayList();
				for (String novaInstanceId : novaInstancesNotReady) {
					failInstances.add(serverApi.get(novaInstanceId).getMetadata().get("DIRECTOR_ID"));
				}
				releaseResources(volumeNumber, volumeSize, floatingIpPool, failInstances, null, accumulator);
			}
		}
		if (accumulator.hasError()) {
			PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
			throw new UnrecoverableProviderException("Problem allocating instances and volumes.", pluginExceptionDetails);
		}
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

	private Set<String> getFloatingIPIdsByInstanceIds(Collection<String> instanceIds) {
		Set<String> floatingIpIds = Sets.newHashSet();
		if (instanceIds == null || instanceIds.isEmpty()) {
			return floatingIpIds;
		}
		
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		FloatingIPApi floatingIpApi = novaApi.getFloatingIPApi(region).get();
		Set<String> novaInstanceIds = getNovaInstanceIdsByInstanceIds(instanceIds).values();
		FluentIterable<FloatingIP> floatingIps = floatingIpApi.list();
		for (FloatingIP floatingIp : floatingIps) {
			if (novaInstanceIds.contains(floatingIp.getInstanceId())) {
				floatingIpIds.add(floatingIp.getId());
			}
		}
		return floatingIpIds;
	}
	
	private Set<String> getVolumeIdsByInstanceIds(Collection<String> instanceIds) {
		final Set<String> volumeIds = Sets.newHashSet();
		if (instanceIds == null || instanceIds.isEmpty()) {
			return volumeIds;
		}
		
		CinderApi cinderApi = getCinderApi();
		String region = getRegion();
		VolumeApi volumeApi = cinderApi.getVolumeApi(region);
		FluentIterable<? extends Volume> volumes= volumeApi.listInDetail();
		for (String instanceId: instanceIds) {
			for (Volume volume: volumes) {
				if (volume.getMetadata() != null && volume.getMetadata().get("DIRECTOR_ID") != null
					&& volume.getMetadata().get("DIRECTOR_ID").equals(instanceId)) {
					volumeIds.add(volume.getId());
				}
			}
		}
		return volumeIds;
	}
	
	public void delete(NovaInstanceTemplate template, Collection<String> instanceIds)
			throws InterruptedException {
		
		if (instanceIds == null || instanceIds.isEmpty()) {
			return;
		}
		
		LocalizationContext providerLocalizationContext = getLocalizationContext();
		LocalizationContext templateLocalizationContext =
			SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);
		int volumeNumber = Integer.parseInt(template.getConfigurationValue(VOLUME_NUMBER, templateLocalizationContext));
		int volumeSize = Integer.parseInt(template.getConfigurationValue(VOLUME_SIZE, templateLocalizationContext));
		String floatingIpPool = template.getConfigurationValue(FLOATING_IP_POOL, templateLocalizationContext);
		PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();

		releaseResources(volumeNumber, volumeSize, floatingIpPool, instanceIds, null, accumulator);
		if (accumulator.hasError()) {
			PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
			throw new UnrecoverableProviderException("Problem allocating instances and volumes.", pluginExceptionDetails);
		}
	}

	public Collection<NovaInstance> find(NovaInstanceTemplate template,
			Collection<String> instanceIds) throws InterruptedException {
		
		final Collection<NovaInstance> novaInstances =
				Lists.newArrayListWithExpectedSize(instanceIds.size());
		if (instanceIds == null || instanceIds.isEmpty()) {
			return novaInstances;
		}
		
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		BiMap<String, String> instanceIdsByNovaInstanceId = 
				getNovaInstanceIdsByInstanceIds(instanceIds);
		
		ServerApi serverApi = novaApi.getServerApi(region);
		
		for (String currentId : instanceIds) {
			String novaInstanceId = instanceIdsByNovaInstanceId.get(currentId);
			if (novaInstanceId != null) {
				novaInstances.add(new NovaInstance(template, currentId, serverApi.get(novaInstanceId)));
			}
		}
		
		return novaInstances;
	}

	public Map<String, InstanceState> getInstanceState(NovaInstanceTemplate template, 
			Collection<String> instanceIds) {
		
		Map<String, InstanceState> instanceStatesByInstanceIds = new HashMap<String, InstanceState >();
		if (instanceIds == null || instanceIds.isEmpty()) {
			return instanceStatesByInstanceIds;
		}
		
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		ServerApi serverApi = novaApi.getServerApi(region);
		BiMap<String, String> instanceIdsByNovaInstanceIds =
				getNovaInstanceIdsByInstanceIds(instanceIds);

		for (String currentId : instanceIds) {
			String novaInstanceId = instanceIdsByNovaInstanceIds.get(currentId);
			if (novaInstanceId == null) {
				InstanceState instanceStateDel = NovaInstanceState.fromInstanceStateName(Status.DELETED);
				instanceStatesByInstanceIds.put(currentId, instanceStateDel);
			}
			else {
				Status instance_state =  serverApi.get(novaInstanceId).getStatus();
				InstanceState instanceState = NovaInstanceState.fromInstanceStateName(instance_state);
				instanceStatesByInstanceIds.put(currentId, instanceState);
			}
		}
		
		return instanceStatesByInstanceIds;
	}	

	public Type getResourceType() {
		return NovaInstance.TYPE;
	}
	
	private static String decorateInstanceName(NovaInstanceTemplate template, String currentId){
		return template.getInstanceNamePrefix() + "-" + currentId;
	}
	
	/**
	 * Returns a map from instance ID to corresponding Nova instance ID for the specified
	 * instance IDs.
	 *
	 * @param instanceIds the given instance IDs
	 * @return the map from instance ID to corresponding Nova instance ID
	 */
	private BiMap<String, String> getNovaInstanceIdsByInstanceIds(
		  Collection<String> instanceIds) {
		// Traverse the server metadata to get the instances.
		
		final BiMap<String, String> novaInstanceIdsByInstanceId = HashBiMap.create();
		if (instanceIds == null || instanceIds.isEmpty()) {
			return novaInstanceIdsByInstanceId;
		}
		NovaApi novaApi = getNovaApi();
		String region = getRegion();
		ServerApi serverApi = novaApi.getServerApi(region);
		// Transfer to List, for FluentIterable can only be parsed once.
		List<Server> servers = serverApi.listInDetail().concat().toList();
		for (String instanceId : instanceIds) {
			for (Server server: servers) {
				if (server.getMetadata() != null && server.getMetadata().get("DIRECTOR_ID") != null
					&& server.getMetadata().get("DIRECTOR_ID").equals(instanceId)) {
					novaInstanceIdsByInstanceId.put(instanceId, server.getId());
				}
			}
		}
		
		return novaInstanceIdsByInstanceId;
	}
}

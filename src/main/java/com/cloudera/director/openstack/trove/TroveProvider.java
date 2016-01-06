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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jclouds.openstack.trove.v1.TroveApi;
import org.jclouds.openstack.trove.v1.domain.Instance;
import org.jclouds.openstack.trove.v1.domain.Instance.Status;
import org.jclouds.openstack.trove.v1.features.InstanceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.director.openstack.OpenStackCredentials;
import com.cloudera.director.spi.v1.database.DatabaseType;
import com.cloudera.director.spi.v1.database.util.AbstractDatabaseServerProvider;
import com.cloudera.director.spi.v1.database.util.SimpleDatabaseServerProviderMetadata;
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
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import static com.cloudera.director.openstack.trove.TroveProviderConfigurationProperty.REGION;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.FLAVOR_ID;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.VOLUME_SIZE;

public class TroveProvider extends AbstractDatabaseServerProvider<TroveInstance, TroveInstanceTemplate> {

	private static final Logger LOG = LoggerFactory.getLogger(TroveProvider.class);
	
	/**
	 * The provider configuration properties.
	 */	
	protected static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES = ConfigurationPropertiesUtil.asConfigurationPropertyList(TroveProviderConfigurationProperty.values());
	
	/**
	 * The resource provider ID.
	 */
	public static final String ID = TroveProvider.class.getCanonicalName();
	
	/**
	 * The resource provider metadata.
	 */
	public static final ResourceProviderMetadata METADATA = SimpleDatabaseServerProviderMetadata.databaseServerProviderMetadataBuilder()
		.id(ID)
		.name("Trove")
		.description("OpenStack Trove database server provider")
		.providerClass(TroveProvider.class)
		.providerConfigurationProperties(CONFIGURATION_PROPERTIES)
		.resourceTemplateConfigurationProperties(TroveInstanceTemplate.getConfigurationProperties())
		.resourceDisplayProperties(TroveInstance.getDisplayProperties())
		.supportedDatabaseTypes(new HashSet<DatabaseType>(Arrays.asList(DatabaseType.MYSQL)))
		.build();
	
	private OpenStackCredentials credentials;
	private final TroveApi troveApi;
	private final InstanceApi instanceApi;
	private String region;
	
	/**
	 * Construct a new provider instance and validate all configurations.
	 *
	 * @param configuration            the configuration
	 * @param credentials              the openstack credentials
	 * @param openstackConfig          the openstack config
	 * @param localizationContext the parent cloud localization context
	 */
	public TroveProvider(Configured configuration, OpenStackCredentials credentials, LocalizationContext localizationContext) {
		this(TroveApiProvider.buildTroveApi(credentials), configuration, credentials, localizationContext);
	}
	
	public TroveProvider(TroveApi troveApi, Configured configuration, OpenStackCredentials credentials, LocalizationContext localizationContext) {
		super(configuration,METADATA,localizationContext);
		this.credentials = credentials;
		this.troveApi = troveApi;
		this.region = configuration.getConfigurationValue(REGION, localizationContext);
		this.instanceApi = troveApi.getInstanceApi(region);
	}

	public TroveApi getTroveApi() {
		return troveApi;
	}
	
	public String getRegion() {
		return region;
	}
	
	@Override
	public Map<String, InstanceState> getInstanceState(TroveInstanceTemplate template, Collection<String> virtualInstanceIds) {
		Map<String, InstanceState> instanceStateByInstanceId = new HashMap<String, InstanceState>();
		
		BiMap<String, String> troveInstanceIdByVirtualInstanceId = getTroveInstanceIdByVirtualInstanceId(virtualInstanceIds);
		
		for(String virtualInstanceId : virtualInstanceIds) {
			String troveInstanceId = troveInstanceIdByVirtualInstanceId.get(virtualInstanceId);
			if(troveInstanceId == null) {
				InstanceState instanceStateDel = TroveInstanceState.fromTroveStatus(Status.UNRECOGNIZED);
				instanceStateByInstanceId.put(troveInstanceId, instanceStateDel);
				continue;
			}
			Status instanceStatus = instanceApi.get(troveInstanceId).getStatus();
			InstanceState instanceState = TroveInstanceState.fromTroveStatus(instanceStatus);
			instanceStateByInstanceId.put(troveInstanceId, instanceState);
		}
		return instanceStateByInstanceId;
	}

	@Override
	public Type getResourceType() {
		return TroveInstance.TYPE;
	}

	@Override
	public TroveInstanceTemplate createResourceTemplate(String name, Configured configuration, Map<String, String> tags) {
		return new TroveInstanceTemplate(name, configuration, tags, this.getLocalizationContext());
	}

	@Override
	public void allocate(TroveInstanceTemplate template, Collection<String> instanceIds, int minCount) throws InterruptedException {
		LocalizationContext providerLocalizationContext = getLocalizationContext();
		LocalizationContext templateLocalizationContext = SimpleResourceTemplate.getTemplateLocalizationContext(providerLocalizationContext);

		Set<String> instancesBooting = Sets.newHashSet();
		
		BiMap<String, String> troveInstanceIdByVirtualInstanceId = getTroveInstanceIdByVirtualInstanceId(instanceIds);
		int preCallCount = troveInstanceIdByVirtualInstanceId.size();
		int currentCallCount = instanceIds.size();
		if(preCallCount == currentCallCount) {
			return;
		}
		
		for (String currentVirtualId : instanceIds) {
			BiMap<String, String> currentInstanceIdMap = getTroveInstanceIdByVirtualInstanceId(Lists.newArrayList(currentVirtualId));
			String currentInstanceId = currentInstanceIdMap.get(currentVirtualId);
			if(currentInstanceId == null) {
				String instanceName = decorateInstanceName(template, currentVirtualId);
				String flavorId = template.getConfigurationValue(FLAVOR_ID, templateLocalizationContext);
				String volumeSize = template.getConfigurationValue(VOLUME_SIZE, templateLocalizationContext);
	
				//create db instance
				Instance createdInstance = instanceApi.create(flavorId, Integer.parseInt(volumeSize), instanceName);
				String createdInstanceId = createdInstance.getId();
							
				if (createdInstanceId != null) {
					instancesBooting.add(createdInstanceId);
				}
			}
			
		}
		
		int totalTimePollingSeconds = 0;
		int pollingTimeoutSeconds = 600;
		boolean timeoutExceeded = false; 
		Set<String> instancesBooted = Sets.newHashSet();
		
		while (!instancesBooting.isEmpty() && !timeoutExceeded) {
			
			for (String troveInstanceId : instancesBooting) {
				
				Status instanceStatus = instanceApi.get(troveInstanceId).getStatus();
				
				if(instanceStatus == Status.ACTIVE){
					//enable the root permission
					instanceApi.enableRoot(troveInstanceId);
					instancesBooted.add(troveInstanceId);
					instancesBooting.remove(troveInstanceId);
				}
			}
			
			if (!instancesBooting.isEmpty()) {
				if (totalTimePollingSeconds > pollingTimeoutSeconds) {
					timeoutExceeded = true;
				}
				TimeUnit.SECONDS.sleep(5);
				totalTimePollingSeconds += 5;
			}
		}
		
		int successfulOperationCount = instancesBooted.size();
		if (successfulOperationCount < minCount){
			PluginExceptionConditionAccumulator accumulator = new PluginExceptionConditionAccumulator();
			for (String currentId : instanceIds) {
				try{
					instanceApi.delete(currentId);
				} catch (Exception e) {
					accumulator.addError(null, e.getMessage());
				}
			}
			PluginExceptionDetails pluginExceptionDetails = new PluginExceptionDetails(accumulator.getConditionsByKey());
			throw new UnrecoverableProviderException("Problem allocating instances.", pluginExceptionDetails);
		}
	}

	@Override
	public Collection<TroveInstance> find(TroveInstanceTemplate template, Collection<String> virtualInstanceIds) throws InterruptedException {	
		final Collection<TroveInstance> troveInstances = Lists.newArrayListWithExpectedSize(virtualInstanceIds.size());
		
		BiMap<String, String> troveInstanceIdByVirtualInstanceId = getTroveInstanceIdByVirtualInstanceId(virtualInstanceIds);
		for (String virtualInstanceId : virtualInstanceIds) {
			String troveInstanceId = troveInstanceIdByVirtualInstanceId.get(virtualInstanceId);
			Instance instance = instanceApi.get(troveInstanceId);
			if(instance.getStatus() == Status.ACTIVE){
				troveInstances.add(new TroveInstance(template, virtualInstanceId, instanceApi.get(troveInstanceId)));	
			}
		}
		return troveInstances;
	}

	@Override
	public void delete(TroveInstanceTemplate template, Collection<String> virtualInstanceIds) throws InterruptedException {
		if (virtualInstanceIds.isEmpty()){
			return;
		}
		
		BiMap<String, String> troveInstanceIdByVirtualInstanceId = getTroveInstanceIdByVirtualInstanceId(virtualInstanceIds);
		for (String virtualInstanceId : virtualInstanceIds) {
			String troveInstanceId = troveInstanceIdByVirtualInstanceId.get(virtualInstanceId);
			boolean deleted = instanceApi.delete(troveInstanceId);
			if (!deleted) {
				LOG.info("Unable to terminate instance {}", troveInstanceId);
			}
		}
	}
		
	private static String decorateInstanceName(TroveInstanceTemplate template, String currentId){
		return template.getInstanceNamePrefix() + "-" + currentId;
	}
	
	/**
	 * Returns a map from virtual instance ID to corresponding instance ID for the specified
	 * virtual instance IDs.
	 *
	 * @param virtualInstanceIds 	the virtual instance IDs
	 * @return the map from virtual instance ID to corresponding Trove instance ID
	 */
	private BiMap<String, String> getTroveInstanceIdByVirtualInstanceId(Collection<String> virtualInstanceIds){
		final BiMap<String, String> troveInstanceIdsByVirtualInstanceId = HashBiMap.create();
		FluentIterable<Instance> instances = instanceApi.list();

		for (String virtualInstanceId : virtualInstanceIds) {
			if(instances != null) {
				for(Instance instance : instances) {
					String instanceName = instance.getName();
					if(instanceName.contains(virtualInstanceId)){
						troveInstanceIdsByVirtualInstanceId.put(virtualInstanceId, instance.getId());
					}
				}
			}
		}
		return troveInstanceIdsByVirtualInstanceId;
	}

}

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

import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.AVAILABILITY_ZONE;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.KEY_NAME;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.NETWORK_ID;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.SECURITY_GROUP_NAMES;
import static org.jclouds.openstack.nova.v2_0.domain.Image.Status.ACTIVE;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static com.cloudera.director.spi.v1.model.util.Validations.addError;

import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.extensions.KeyPairApi;
import org.jclouds.openstack.nova.v2_0.extensions.SecurityGroupApi;
import org.jclouds.openstack.nova.v2_0.features.ImageApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.exception.TransientProviderException;
import com.cloudera.director.spi.v1.util.Preconditions;
import com.google.common.annotations.VisibleForTesting;

/**
 * Validates Nova instance template configuration.
 */
public class NovaInstanceTemplateConfigurationValidator implements ConfigurationValidator{
    private static final Logger LOG = 
    		LoggerFactory.getLogger(NovaInstanceTemplateConfigurationValidator.class);
    
    @VisibleForTesting
    static final String INVALID_AVAILABILITY_ZONE_MSG = "Invalid availability zone" + " : %s";
    
    @VisibleForTesting
    static final String INVALID_IMAGE_ID = "Invalid image id" + " : %s";
    
    @VisibleForTesting
    static final String PREFIX_MISSING_MSG = "Instance name prefix must be provided.";
    
    @VisibleForTesting
    static final String INVALID_PREFIX_LENGTH_MSG = "Instance name prefix must between 1 and 26 characters.";
    
    @VisibleForTesting
    static final String INVALID_KEY_NAME_MSG = "Invalid key name: %s";
    
    @VisibleForTesting
    static final String INVALID_SECURITY_GROUP_NAME_MSG = "Invalid security group names";
    /**
     * The Nova provider
     */
    private final NovaProvider provider;
    
    /**
     *  Create an Nova instance template configuration validator with the specified parameters.
     * @param provider the Nova provider
     */
    public NovaInstanceTemplateConfigurationValidator(NovaProvider provider) {
    	this.provider = Preconditions.checkNotNull(provider,"provider");
    }
    
    @Override
	public void validate(String name, Configured configuration,
			PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {
	     
    	NovaApi novaApi = provider.getNovaApi();
    	String region = provider.getRegion();
    	
    	checkAvailabilityZone(novaApi, region,configuration, accumulator, localizationContext);
    	checkImage(novaApi, region,configuration, accumulator, localizationContext);
    	checkKeyName(novaApi, region, configuration, accumulator, localizationContext);
    	checkSecurityGroupNames(novaApi, region, configuration, accumulator, localizationContext);
    	checkPrefix(configuration, accumulator, localizationContext);
	}
    
    /**
     * Validate the configured availability zone.
     * 
     * @param novaApi  the novaApi
     * @param region   the region
     * @param configuration the configuration to be validated
     * @param accumulator   the exception condition accumulator
     * @param localizationContext	the localization context
     */
    @VisibleForTesting
    void checkAvailabilityZone(NovaApi novaApi,
    		String region,
    		Configured configuration,
    		PluginExceptionConditionAccumulator accumulator,
    		LocalizationContext localizationContext) {
    	String zoneName = configuration.getConfigurationValue(AVAILABILITY_ZONE, localizationContext);
    	if(zoneName != null) {
    		LOG.info(">> Describing zone '{}",zoneName);
    		
    		try {
    			if (!novaApi.getAvailabilityZoneApi(region).get().listAvailabilityZones().contains(zoneName)){
    				addError(accumulator, AVAILABILITY_ZONE, localizationContext, null, INVALID_AVAILABILITY_ZONE_MSG, zoneName);
    			}
    		}
    		catch (Exception e) {
    			throw new TransientProviderException(e);
    		}
    	} 	
    }
    /**
     * Validates the configured Image.
     * @param novaApi	the novaApi 
     * @param region	the region
     * @param configuration	the configuration to be validated
     * @param accumulator	the exception condition accumulator
     * @param localizationContext	the localization context
     */
    @VisibleForTesting
    void checkImage(NovaApi novaApi,
    		String region,
    		Configured configuration,
    		PluginExceptionConditionAccumulator accumulator,
    		LocalizationContext localizationContext) {
    	String imageID = configuration.getConfigurationValue(IMAGE, localizationContext);
    	if (imageID!=null) {
			LOG.info(">> Querying IMAGE '{}'", imageID);
			try {
				ImageApi imageApi = novaApi.getImageApi(region);
				if (imageApi.get(imageID).getStatus() != ACTIVE) {
					addError(accumulator, IMAGE, localizationContext, null, INVALID_IMAGE_ID, imageID);
				}
			}
			catch (Exception e) {
				throw new TransientProviderException(e);

			}
		}
    	
    }
    
    /**
     * Validates the Nova key pair.
     * @param novaApi	the novaApi 
     * @param region	the region
     * @param configuration	the configuration to be validated
     * @param accumulator	the exception condition accumulator
     * @param localizationContext	the localization context
     */
    @VisibleForTesting
    void checkKeyName(NovaApi novaApi,
    		String region,
    		Configured configuration,
    		PluginExceptionConditionAccumulator accumulator,
    		LocalizationContext localizationContext) {
    	String keyName = configuration.getConfigurationValue(KEY_NAME, localizationContext);
    	
    	if (keyName != null) {
    		LOG.info(">> Query key pair");
    		try {
    			KeyPairApi keyPairApi = novaApi.getKeyPairApi(region).get();
    			if (!keyPairApi.list().contains(keyName)) {
    				addError(accumulator, KEY_NAME, localizationContext, null, INVALID_KEY_NAME_MSG, keyName);
    			}
    		}
    		catch (Exception e) {
    			throw new TransientProviderException(e);
    		}
    	}
    	
    }
   
    /**
     * Validates the configured security group names.
     * @param novaApi	the novaApi 
     * @param region	the region
     * @param configuration	the configuration to be validated
     * @param accumulator	the exception condition accumulator
     * @param localizationContext	the localization context
     */
    @VisibleForTesting
    void checkSecurityGroupNames(NovaApi novaApi,
    		String region,
    		Configured configuration,
    		PluginExceptionConditionAccumulator accumulator,
    		LocalizationContext localizationContext) {
    	String secgroupName = configuration.getConfigurationValue(SECURITY_GROUP_NAMES, localizationContext);
    	
    	if (secgroupName != null) {
    		LOG.info(">> Query security names");
    		try {
    			SecurityGroupApi secgroupApi = novaApi.getSecurityGroupApi(region).get();
    			if (!secgroupApi.list().contains(secgroupName)) {
    				addError(accumulator, SECURITY_GROUP_NAMES, localizationContext, null, INVALID_SECURITY_GROUP_NAME_MSG, secgroupName);
    			}
    		}
    		catch (Exception e) {
    			throw new TransientProviderException(e);
    		}
    	}
    	
    }
    
    /**
     * Validates the configured prefix.
     * @param configuration		the configuration to be validated.
     * @param accumulator		the exception condition accumulator.
     * @param localizationContext		the localization context.
     */
    static void checkPrefix(Configured configuration,
    		PluginExceptionConditionAccumulator accumulator,
    		LocalizationContext localizationContext) {
    	String instanceNamePrefix = configuration.getConfigurationValue(INSTANCE_NAME_PREFIX, localizationContext);
    	LOG.info(">> Validating prefix '{}'", instanceNamePrefix);
    	if (instanceNamePrefix == null) {
    		addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null, PREFIX_MISSING_MSG);		
    	}
    	else {
    		int length = instanceNamePrefix.length();
    		if (length < 1 || length > 26) {
    			addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null , INVALID_PREFIX_LENGTH_MSG);
    		}
    	}
    }
    

}

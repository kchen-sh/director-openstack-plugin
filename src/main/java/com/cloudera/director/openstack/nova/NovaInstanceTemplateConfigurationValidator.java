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
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.VOLUME_NUMBER;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.VOLUME_SIZE;
import static org.jclouds.openstack.nova.v2_0.domain.Image.Status.ACTIVE;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static com.cloudera.director.spi.v1.model.util.Validations.addError;

import java.util.List;

import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.domain.Image;
import org.jclouds.openstack.nova.v2_0.domain.KeyPair;
import org.jclouds.openstack.nova.v2_0.domain.SecurityGroup;
import org.jclouds.openstack.nova.v2_0.domain.regionscoped.AvailabilityZone;
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
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

/**
 * Validates Nova instance template configuration.
 */
public class NovaInstanceTemplateConfigurationValidator implements ConfigurationValidator{
	private static final Logger LOG =
			LoggerFactory.getLogger(NovaInstanceTemplateConfigurationValidator.class);

	@VisibleForTesting
	static final String INVALID_AVAILABILITY_ZONE_MSG = "Invalid availability zone: %s";

	@VisibleForTesting
	static final String INVALID_IMAGE_MSG = "Invalid image id: %s";

	@VisibleForTesting
	static final String PREFIX_MISSING_MSG = "Instance name prefix must be provided.";

	@VisibleForTesting
	static final String INVALID_PREFIX_LENGTH_MSG = "Instance name prefix must between 1 and 26 characters.";

	@VisibleForTesting
	static final String INVALID_KEY_NAME_MSG = "Invalid key name: %s";

	@VisibleForTesting
	static final String INVALID_SECURITY_GROUP_NAME_MSG = "Invalid security group names";

	@VisibleForTesting
	static final String INVALID_VOLUME_NUMBER_MSG = "Invalid volume number";

	@VisibleForTesting
	static final String INVALID_VOLUME_SIZE_MSG = "Invalid volume size";

	/**
	 * The Nova provider
	 */
	private final NovaProvider provider;

	/**
	 * Create a Nova instance template configuration validator with the specified parameters.
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
				Boolean contains = false; 
				FluentIterable<AvailabilityZone> availabilityZones = novaApi.getAvailabilityZoneApi(region).get().listAvailabilityZones();
				for (AvailabilityZone availabilityZone: availabilityZones) {
					if (availabilityZone.getName().equals(zoneName)) {
						contains = true;
						break;
					}
				}
				if (!contains) {
					addError(accumulator, AVAILABILITY_ZONE, localizationContext, null, INVALID_AVAILABILITY_ZONE_MSG, zoneName);
				}
			}
			catch (Exception e) {
				throw Throwables.propagate(e);
			}
		}
	}

	/**
	 * Validates the configured Image.
	 *
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
		
		LOG.info(">> Querying IMAGE '{}'", imageID);
		try {
			ImageApi imageApi = novaApi.getImageApi(region);
			Image image = imageApi.get(imageID);
			if (image == null || image.getStatus() != ACTIVE) {
				addError(accumulator, IMAGE, localizationContext, null, INVALID_IMAGE_MSG, imageID);
			}
		}
		catch (Exception e) {
			throw Throwables.propagate(e);
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
		LOG.info(">> Query key pair");
		try {
			KeyPairApi keyPairApi = novaApi.getKeyPairApi(region).get();
			if (keyPairApi.get(keyName) == null) {
				addError(accumulator, KEY_NAME, localizationContext, null, INVALID_KEY_NAME_MSG, keyName);
			}
		}
		catch (Exception e) {
			throw Throwables.propagate(e);
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
		List<String> securityGroupsNames = NovaInstanceTemplate.CSV_SPLITTER.splitToList(
				configuration.getConfigurationValue(SECURITY_GROUP_NAMES, localizationContext));
		
		try {
			SecurityGroupApi secgroupApi = novaApi.getSecurityGroupApi(region).get();
			FluentIterable<SecurityGroup> secgroups = secgroupApi.list();
			
			for (String securityGroupName : securityGroupsNames) {
				LOG.info(">> Query security group Name '{}'", securityGroupName);
			
				Boolean contains = false; 
				for (SecurityGroup secgroup: secgroups) {
					if (secgroup.getName().equals(securityGroupName)) {
						contains = true;
						break;
					}
				}
				if (!contains) {
					addError(accumulator, SECURITY_GROUP_NAMES, localizationContext, null, INVALID_SECURITY_GROUP_NAME_MSG, securityGroupName);
				}
			}
		}
		catch (Exception e) {
			throw Throwables.propagate(e);
		}
	}

	/**
	 * Validates the configured prefix.
	 *
	 * @param configuration		the configuration to be validated
	 * @param accumulator		the exception condition accumulator
	 * @param localizationContext	the localization context
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
			if (length > 218) {
				addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null , INVALID_PREFIX_LENGTH_MSG);
			}
		}
	}

	/**
	 * Validates the configured volume number.
	 * @param configuration		the configuration to be validated.
	 * @param accumulator		the exception condition accumulator.
	 * @param localizationContext		the localization context.
	 */
	static void checkVolumeNum(Configured configuration,
			PluginExceptionConditionAccumulator accumulator,
			LocalizationContext localizationContext) {
		String volNum = configuration.getConfigurationValue(VOLUME_NUMBER, localizationContext);
		LOG.info(">> Validating volume number '{}'", volNum);
		if (volNum == null){
			addError(accumulator, VOLUME_NUMBER, localizationContext, null , INVALID_VOLUME_NUMBER_MSG);
		}
		else {
			try {
				int volnum = Integer.parseInt(volNum);
				if (volnum < 0) {
					addError(accumulator, VOLUME_NUMBER, localizationContext, null , INVALID_VOLUME_NUMBER_MSG);
				}
			}
			catch (Exception e) {
				addError(accumulator, VOLUME_NUMBER, localizationContext, null , INVALID_VOLUME_NUMBER_MSG);
			}
		}
	}

	/**
	 * Validates the configured volume size.
	 * @param configuration		the configuration to be validated.
	 * @param accumulator		the exception condition accumulator.
	 * @param localizationContext		the localization context.
	 */
	static void checkVolumeSize(Configured configuration,
			PluginExceptionConditionAccumulator accumulator,
			LocalizationContext localizationContext) {
		String volSize= configuration.getConfigurationValue(VOLUME_SIZE, localizationContext);
		LOG.info(">> Validating volume size '{}'", volSize);
		if (volSize == null){
			addError(accumulator, VOLUME_SIZE, localizationContext, null , INVALID_VOLUME_SIZE_MSG);
		}
		else {
			try {
				int volsize = Integer.parseInt(volSize);
				if (volsize < 1) {
					addError(accumulator, VOLUME_SIZE, localizationContext, null , INVALID_VOLUME_SIZE_MSG);
				}
			}
			catch (Exception e) {
				addError(accumulator, VOLUME_SIZE, localizationContext, null , INVALID_VOLUME_SIZE_MSG);
			}
		}
	}

}


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

import java.util.Iterator;
import java.util.regex.Pattern;

import org.jclouds.openstack.trove.v1.TroveApi;
import org.jclouds.openstack.trove.v1.domain.Flavor;

import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;

import static com.cloudera.director.spi.v1.model.util.Validations.addError;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.FLAVOR_ID;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.VOLUME_SIZE;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.MASTER_USER_PASSWORD;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.MASTER_USER_NAME;

/**
 * Validates Trove instance template configuration.
 */
public class TroveInstanceTemplateConfigurationValidator implements ConfigurationValidator {

	@VisibleForTesting
	static final String INVALID_INSTANCE_NAME_MSG = "Invalid  instance name : %s";
	@VisibleForTesting
	static final String INVALID_NAME_MSG = "Database instance name must follow this pattern: The first character must be a lowercase letter, and all following characters must be a dash, lowercase letter, or digit.";
	@VisibleForTesting
	static final String PREFIX_MISSING_MSG = "Database instance name prefix must be provided.";
	@VisibleForTesting
	static final String INVALID_PREFIX_LENGTH_MSG = "Database instance name prefix must be between 1 and 26 characters.";
	@VisibleForTesting
	static final String INVALID_PREFIX_MSG = "Database instance name prefix must follow this pattern: The first character must be a lowercase letter, and all following characters must be a dash, lowercase letter, or digit.";  
	@VisibleForTesting
	static final String INVALID_FLAVOR_ID_MSG = "Invalid flavor id : %s";
	@VisibleForTesting
	static final String INVALID_VOLUME_SIZE = "volume size must be a digit and can not be zero";
	@VisibleForTesting
	static final String INVALID_NAME_LENGTH_MSG = "Database instance name must be between 1 and 26 characters.";
	@VisibleForTesting
	static final String PASSWORD_MISSING_MSG = "Database instance user password must be provided.";
	@VisibleForTesting
	static final String INVALID_PASSWORD_LENGTH_MSG = "Database instance user password must be between 1 and 16 characters.";
	@VisibleForTesting
	static final String USERNAME_MISSING_MSG = "Database instance username must be provided.";
	@VisibleForTesting
	static final String INVALID_USERNAME_LENGTH_MSG = "Database instance username must be between 1 and 16 characters.";
	 
	private final TroveProvider provider;
	private final static Pattern instanceNamePrefixPattern = Pattern.compile("[a-z][-a-z0-9]*");
	private final static Pattern volumeSizePattern = Pattern.compile("^[1-9]*[1-9][1-9]*$");
	
	/**
	 * Create an Trove instance template configuration validator with the specified parameters.
	 * @param provider the Trove provider
	 */
	public TroveInstanceTemplateConfigurationValidator(TroveProvider provider) {
		this.provider = Preconditions.checkNotNull(provider, "provider");
	}
	
	@Override
	public void validate(String name, Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {
		TroveApi troveApi = provider.getTroveApi();
		String region = provider.getRegion();
		
		checkPrefix(configuration, accumulator, localizationContext);
		checkFlavorId(troveApi, region, configuration, accumulator, localizationContext);
		checkPassword(configuration, accumulator, localizationContext);
		checkUsername(configuration, accumulator, localizationContext);
	}
	
	/**
	 * Validate the instance name prefix
	 * 
	 * @param configuration
	 * @param accumulator
	 * @param localizationContext
	 * */
	@VisibleForTesting
	public void checkPrefix(Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext){
		String instanceNamePrefix = configuration.getConfigurationValue(INSTANCE_NAME_PREFIX, localizationContext);

		if (instanceNamePrefix == null) {
			addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null, PREFIX_MISSING_MSG);
		} else {
			int length = instanceNamePrefix.length();
			if (length < 1 || length > 26) {
				addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null, INVALID_PREFIX_LENGTH_MSG);
			} else if (!instanceNamePrefixPattern.matcher(instanceNamePrefix).matches()) {
				addError(accumulator, INSTANCE_NAME_PREFIX, localizationContext, null, INVALID_PREFIX_MSG);
			}
		}		
	}
	
	/**
	 * Validate the flavor id
	 * 
	 * @param troveApi
	 * @param region
	 * @param configuration
	 * @param accumulator
	 * @param localizationContext
	 */
	@VisibleForTesting
	void checkFlavorId(TroveApi troveApi, String region, Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext){
		String flavorId = configuration.getConfigurationValue(FLAVOR_ID, localizationContext);
		
		if(flavorId != null) {
			FluentIterable<Flavor> flavorIdList = troveApi.getFlavorApi(region).list();	
			Iterator<Flavor> iterator = flavorIdList.iterator();
			while(iterator.hasNext()){
				Flavor flavor = iterator.next();
				String currentId = flavor.getId() + "";
				if(flavorId.equals(currentId)){
					return;
				}
			}
			addError(accumulator, FLAVOR_ID, localizationContext, null, INVALID_FLAVOR_ID_MSG, flavorId);
		}	
	}
	
	@VisibleForTesting
	void checkVolumeSize(Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext){
		String volumeSize = configuration.getConfigurationValue(VOLUME_SIZE, localizationContext);
		if (volumeSize != null && !volumeSizePattern.matcher(volumeSize).matches()) {
			addError(accumulator, VOLUME_SIZE, localizationContext, null, INVALID_VOLUME_SIZE);
		}
	}
	
	/**
	 * Validate the user password
	 * 
	 * @param configuration
	 * @param accumulator
	 * @param localizationContext
	 */
	public void checkPassword(Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {
		String instancePassword = configuration.getConfigurationValue(MASTER_USER_PASSWORD, localizationContext);

		if (instancePassword == null) {
			addError(accumulator, instancePassword, localizationContext, null, PASSWORD_MISSING_MSG);
		} else {
			int length = instancePassword.length();
			if (length < 1 || length > 16) {
				addError(accumulator, MASTER_USER_PASSWORD, localizationContext, null, INVALID_PASSWORD_LENGTH_MSG);
			}
		}
	}

	/**
	 * Validate the user name
	 * 
	 * @param configuration
	 * @param accumulator
	 * @param localizationContext
	 */
	public void checkUsername(Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {
		String instanceUsername = configuration.getConfigurationValue(MASTER_USER_NAME, localizationContext);

		if (instanceUsername == null) {
			addError(accumulator, MASTER_USER_NAME, localizationContext, null, USERNAME_MISSING_MSG);
		} else {
			int length = instanceUsername.length();
			if (length < 1 || length > 16) {
				addError(accumulator, MASTER_USER_NAME, localizationContext, null, INVALID_USERNAME_LENGTH_MSG);
			}
		}
	}
	
}

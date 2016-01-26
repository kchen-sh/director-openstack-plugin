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

import org.jclouds.ContextBuilder;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.trove.v1.TroveApi;
import org.jclouds.openstack.trove.v1.TroveApiMetadata;

import com.cloudera.director.openstack.OpenStackCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

import static com.cloudera.director.openstack.trove.TroveProviderConfigurationProperty.REGION;
import static com.cloudera.director.spi.v1.model.util.Validations.addError;

public class TroveProviderConfigurationValidator implements ConfigurationValidator{

	private OpenStackCredentials credentials;
	
	@VisibleForTesting
	static final String REGION_NOT_FOUND_MSG = "Region '%s' not found.";
	
	public TroveProviderConfigurationValidator(OpenStackCredentials credentials){
		this.credentials = credentials;
	}
	
	@Override
	public void validate(String name, Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext) {
		checkRegion(configuration, accumulator, localizationContext);
	}

	/**
	 * validate the region 
	 *  
	 * @param configuration
	 * @param accumulator
	 * @param localizationContext
	 */
	void checkRegion(Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext){
		TroveApi troveApi = TroveApiProvider.buildTroveApi(credentials);
		checkRegion(troveApi, configuration, accumulator, localizationContext);
	}
	
	void checkRegion(TroveApi troveApi, Configured configuration, PluginExceptionConditionAccumulator accumulator, LocalizationContext localizationContext){
		String regionName = configuration.getConfigurationValue(REGION, localizationContext);
		if (!troveApi.getConfiguredRegions().contains(regionName)) {
			addError(accumulator, REGION, localizationContext, null, REGION_NOT_FOUND_MSG, regionName);
		}
	}	
	
}

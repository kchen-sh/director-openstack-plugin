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

import java.util.List;
import java.util.Map;

import com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;
import com.google.common.base.Splitter;

/**
 * Represents a template for constructing Nova compute instance.
 */
public class NovaInstanceTemplate extends ComputeInstanceTemplate{
	
	/**
	 * A splitter for comma-separated lists.
	 */
	protected static final Splitter CSV_SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();
	  
	/**
	 * The list of configuration properties (including inherited properties).
	 */
	@SuppressWarnings("unchecked")
	private static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES = 
			ConfigurationPropertiesUtil.merge(
					ComputeInstanceTemplate.getConfigurationProperties(),
					ConfigurationPropertiesUtil.asConfigurationPropertyList(
							NovaInstanceTemplateConfigurationProperty.values())
					);
	
	/**
	 * Get the list of configuration properties for creating a Nova instance template,
	 * including inherited properties.
	 */
	public static List<ConfigurationProperty> getConfigurationProperties() {
		return CONFIGURATION_PROPERTIES;
	}
	
	public NovaInstanceTemplate(String name, Configured configuration,
			Map<String, String> tags,
			LocalizationContext providerLocalizationContext) {
		super(name, configuration, tags, providerLocalizationContext);
	}

}


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


import java.util.List;
import java.util.Map;

import com.cloudera.director.spi.v1.database.DatabaseServerInstanceTemplate;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.util.ConfigurationPropertiesUtil;

public class TroveInstanceTemplate extends DatabaseServerInstanceTemplate {
	
	/**
	 * The list of configuration properties (including inherited properties).
	 */
	@SuppressWarnings("unchecked")
	private static final List<ConfigurationProperty> CONFIGURATION_PROPERTIES = ConfigurationPropertiesUtil.merge(DatabaseServerInstanceTemplate.getConfigurationProperties(), ConfigurationPropertiesUtil.asConfigurationPropertyList(TroveInstanceTemplateConfigurationProperty.values()));

	/**
	 * Returns the list of configuration properties for creating an Trove instance template,
	 * including inherited properties.
	 * 
	 * @return the list of configuration properties for creating an Trove instance template,
	 * including inherited properties.
	 */
	public static List<ConfigurationProperty> getConfigurationProperties() {
		return CONFIGURATION_PROPERTIES;
	}
	
	public TroveInstanceTemplate(String name, Configured configuration, Map<String, String> tags, LocalizationContext localizationContext) {
		super(name, configuration, tags, localizationContext);
	}
	
}

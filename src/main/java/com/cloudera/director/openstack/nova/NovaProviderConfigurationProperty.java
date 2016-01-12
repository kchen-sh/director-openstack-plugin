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

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;

/**
 * OpenStack Nova configuration properties.
 */
public enum NovaProviderConfigurationProperty implements ConfigurationPropertyToken{
	 REGION(new SimpleConfigurationPropertyBuilder()
	 .configKey("region")
	 .name("Region")
	 .required(true)
	 .defaultValue("regionOne")
	 .defaultDescription("Region to target for deployment.")
	 .build());

	/**
	 * The configuration property.
	 */
	private final ConfigurationProperty configurationProperty;

	/**
	 * Creates a configuration property token with the specified parameters.
	 *
	 * @param configurationProperty the configuration property
	 */
	private NovaProviderConfigurationProperty(ConfigurationProperty configurationProperty) {
		this.configurationProperty = configurationProperty;
	}
	
	@Override
	public ConfigurationProperty unwrap() {
		return configurationProperty;
	}


}


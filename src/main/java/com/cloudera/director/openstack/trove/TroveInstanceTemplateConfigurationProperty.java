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

import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;

import static com.cloudera.director.spi.v1.database.DatabaseServerInstanceTemplate.DatabaseServerInstanceTemplateConfigurationPropertyToken.ADMIN_PASSWORD;
import static com.cloudera.director.spi.v1.database.DatabaseServerInstanceTemplate.DatabaseServerInstanceTemplateConfigurationPropertyToken.ADMIN_USERNAME;
import static com.cloudera.director.spi.v1.database.DatabaseServerInstanceTemplate.DatabaseServerInstanceTemplateConfigurationPropertyToken.TYPE;

public enum TroveInstanceTemplateConfigurationProperty implements
		ConfigurationPropertyToken {
	/**
	 * Trove database instance configuration properties.
	 */ 
	FLAVOR_ID(new SimpleConfigurationPropertyBuilder()
			.configKey("flavorId")
			.name("flavor id")
			.defaultDescription("the flavor id of instance")
			.required(true)
			.build()), 
	VOLUME_SIZE(new SimpleConfigurationPropertyBuilder()
			.configKey("volumeSize")
			.name("volume size")
			.defaultDescription("the volume size of instance")
			.required(true)
			.build()), 
	ENGINE(new SimpleConfigurationPropertyBuilder()
			.configKey(TYPE.unwrap().getConfigKey())
			.name("DB engine")
			.required(true)
			.defaultDescription("The name of the database engine to be used for this instance.")
			.widget(ConfigurationProperty.Widget.LIST)
			.addValidValues("MYSQL")
			.build()), 
	MASTER_USER_NAME(new SimpleConfigurationPropertyBuilder()
			.configKey(ADMIN_USERNAME.unwrap().getConfigKey())
			.name("Master username")
			.defaultDescription("The name of master user for the client DB instance.")
			.build()), 
	MASTER_USER_PASSWORD(new SimpleConfigurationPropertyBuilder()
			.configKey(ADMIN_PASSWORD.unwrap().getConfigKey())
			.name("Master user password")
			.widget(ConfigurationProperty.Widget.PASSWORD)
			.sensitive(true)
			.defaultDescription("The password for the master database user. Password must contain 8-30 alphanumeric characters.")
			.build());

	/**
	 * The configuration property.
	 */
	private ConfigurationProperty configurationProperty;

	/**
	 * Creates a configuration property token with the specified parameters.
	 *
	 * @param configurationProperty the configuration property
	 */
	private TroveInstanceTemplateConfigurationProperty(ConfigurationProperty configurationProperty) {
		this.configurationProperty = configurationProperty;
	}

	@Override
	public ConfigurationProperty unwrap() {
		return configurationProperty;
	}
}

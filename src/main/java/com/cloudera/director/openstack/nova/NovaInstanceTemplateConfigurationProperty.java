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

import com.cloudera.director.spi.v1.compute.ComputeInstanceTemplate.ComputeInstanceTemplateConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleConfigurationPropertyBuilder;

public enum NovaInstanceTemplateConfigurationProperty implements ConfigurationPropertyToken{
	 
	 /**
	  * The availability zone.
	  */
     AVAILABILITY_ZONE(new SimpleConfigurationPropertyBuilder()
    		 .configKey("availabilityZone")
    		 .name("Availability zone")
    		 .widget(ConfigurationProperty.Widget.OPENLIST)
    		 .defaultDescription("The availability zone")
    		 .build()),	
     
     /**
      * The image ID.
      */
     IMAGE(new SimpleConfigurationPropertyBuilder()
    		 .configKey(ComputeInstanceTemplateConfigurationPropertyToken.IMAGE.unwrap().getConfigKey())
    		 .name("Image ID")
    		 .required(true)
    		 .widget(ConfigurationProperty.Widget.OPENLIST)
    		 .defaultDescription("The image id")
    		 .defaultErrorMessage("Image ID is mandatory")
    		 .build()),
     
     /**
      * The IDs of the security groups (comma separated).
      */
     SECURITY_GROUP_NAMES(new SimpleConfigurationPropertyBuilder()
    		 .configKey("securityGroupNames")
    		 .name("Security group names")
    		 .widget(ConfigurationProperty.Widget.OPENMULTI)
    		 .required(true)
    		 .defaultDescription("Specify the list of security group names.")
    		 .defaultErrorMessage("Security group names are mandatory")
    		 .build()),
     
     /**
      * The ID of the network.
      */
     NETWORK_ID(new SimpleConfigurationPropertyBuilder()
    		 .configKey("networkId")
    		 .name("Network ID")
    		 .required(true)
    		 .defaultDescription("The network ID")
    		 .defaultErrorMessage("Network ID is mandatory")
    		 .build()),
     
     /**
      * The instance type (e.g. m1.medium, m1.large, etc), input must be the name.
      */
     TYPE(new SimpleConfigurationPropertyBuilder()
    		 .configKey(ComputeInstanceTemplateConfigurationPropertyToken.TYPE.unwrap().getConfigKey())
    		 .name("Instance flavor name")
    		 .required(true)
    		 .widget(ConfigurationProperty.Widget.OPENLIST)
    		 .defaultDescription(
    			"Size of image to launch.<br />" +
    			"<a target='_blank' href='http://docs.openstack.org/openstack-ops/content/flavors.html'>More Information</a>")
    		 .defaultErrorMessage("Instance flavor ID is mandatory")
    		 .addValidValues(
    			"m1.tiny",
    			"m1.small",
    			"m1.medium",
    			"m1.large",
    			"m1.xlarge")
    		 .build()),
     
     /**
      * Name of the key pair to use for new instances.
      */
     KEY_NAME(new SimpleConfigurationPropertyBuilder()
    		 .configKey("keyName")
    		 .name("Key name")
    		 .required(true)
    		 .widget(ConfigurationProperty.Widget.TEXT)
    		 .defaultDescription("The name of Nova key pair")
    		 .build()),
     
     /**
      * Name of the floating IP pool to allocate floating IP for new instances.
      */
     FLOATING_IP_POOL(new SimpleConfigurationPropertyBuilder()
    		 .configKey("floatingIpPoolName")
    		 .name("FloatingIP pool name")
    		 .defaultValue(null)
    		 .widget(ConfigurationProperty.Widget.TEXT)
    		 .defaultDescription(
    			"The floating IP pool from which to allocate flaoting IP. "+
    			"And the default value is null, "+
    			"we'll not assign floating IP to the instance.")
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
	private NovaInstanceTemplateConfigurationProperty(ConfigurationProperty configurationProperty) {
		this.configurationProperty = configurationProperty;
	}
	
	@Override
	public ConfigurationProperty unwrap() {
		return configurationProperty;
	}
	
}

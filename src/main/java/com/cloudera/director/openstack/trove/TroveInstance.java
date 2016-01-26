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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.jclouds.openstack.trove.v1.domain.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.director.spi.v1.database.util.AbstractDatabaseServerInstance;
import com.cloudera.director.spi.v1.model.DisplayProperty;
import com.cloudera.director.spi.v1.model.DisplayPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleDisplayProperty;
import com.cloudera.director.spi.v1.model.util.SimpleDisplayPropertyBuilder;
import com.cloudera.director.spi.v1.util.DisplayPropertiesUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * Trove database instance.
 */
public class TroveInstance extends AbstractDatabaseServerInstance<TroveInstanceTemplate, Instance> {

	private static final Logger LOG = LoggerFactory.getLogger(TroveInstance.class);

	private static final int MYSQL_PORT = 3306;

	/**
	 * The list of display properties (including inherited properties).
	 */
	private static final List<DisplayProperty> DISPLAY_PROPERTIES = DisplayPropertiesUtil.asDisplayPropertyList(TroveInstanceDisplayPropertyToken.values());

	/**
	 * Returns the list of display properties for an trove instance, including inherited properties.
	 */
	public static List<DisplayProperty> getDisplayProperties() {
		return DISPLAY_PROPERTIES;
	}
	
	/**
	 * Trove database instance display properties.
	 */
	public static enum TroveInstanceDisplayPropertyToken implements DisplayPropertyToken {

		HOSTNAME(new SimpleDisplayPropertyBuilder()
			.displayKey("hostname")
			.defaultDescription("The hostname")
			.sensitive(false).build()) {
			
			@Override
			protected String getPropertyValue(Instance instance) {
				return instance.getHostname();
			}
		},		
		INSTANCE_ID(new SimpleDisplayPropertyBuilder()
			.displayKey("instanceId")
			.defaultDescription("The intance id")
			.sensitive(false).build()) {
			
			@Override
			protected String getPropertyValue(Instance instance) {
				return instance.getId();
			}
		},
		INSTANCE_NAME(new SimpleDisplayPropertyBuilder()
			.displayKey("instanceName")
			.defaultDescription("The instance name")
			.sensitive(false).build()) {
			
			@Override
			protected String getPropertyValue(Instance instance) {
				return instance.getName();
			}
		},
		FLAVOR_ID(new SimpleDisplayPropertyBuilder()
			.displayKey("flavorId")
			.defaultDescription("The flavor id of the instance")
			.sensitive(false).build()) {
			
			@Override
			protected String getPropertyValue(Instance instance) {
				return String.valueOf(instance.getFlavor().getId());
			}
		},

		VOLUME_SIZE(new SimpleDisplayPropertyBuilder()
			.displayKey("volumeSize")
			.defaultDescription("the volume size for this instance in gigabytes (GB).")
			.sensitive(false).build()) {
			
			@Override
			protected String getPropertyValue(Instance instance) {
				return String.valueOf(instance.getSize());
			}
		},		
		INSTANCE_STATUS(new SimpleDisplayPropertyBuilder()
			.displayKey("instanceStatus")
			.defaultDescription("the status for this instance")
			.sensitive(false).build()) {
			
			@Override
			protected String getPropertyValue(Instance instance) {
				return String.valueOf(instance.getStatus());
			}
		};

		/**
		 * The display property.
		 */
		private final DisplayProperty displayProperty;

		/**
		 * Create an Trove instance display property token with the specified parameters.
		 * @param displayProperty the display property
		 */
		private TroveInstanceDisplayPropertyToken(SimpleDisplayProperty displayProperty) {
			this.displayProperty = displayProperty;
		}

		/**
		 * Returns the value of the property from the specified instance.
		 * 
		 * @param instance 	the instance
		 * @return the value of the property from the specified instance
		 */
		protected abstract String getPropertyValue(Instance instance);

		@Override
		public DisplayProperty unwrap() {
			return displayProperty;
		}

	}

	public static final Type TYPE = new ResourceType("TroveInstance");
	
	/**
	 * Returns the private IP address of the specified Trove instance.
	 *
	 * @param instance 	the instance
	 * @return the private IP address of the specified Trove instance
	 * @throws IllegalArgumentException if the instance does not have a valid private IP address
	 */
	private static InetAddress getPrivateIpAddress(Instance instance) {
		Preconditions.checkNotNull(instance, "instance is null");
		InetAddress privateIpAddress = null;
		try {
			privateIpAddress = InetAddress.getByName(instance.getName());
		} catch(UnknownHostException e) {
			throw new IllegalArgumentException("Invalid private IP address", e);
		}
		return privateIpAddress;
	}
	
	protected TroveInstance(TroveInstanceTemplate template, String instanceId,
			Instance instance) {
		super(template, instanceId, getPrivateIpAddress(instance),MYSQL_PORT);
	}
	
	
	@Override
	public Type getType() {
		return TYPE;
	}

	@Override
	public Map<String, String> getProperties() {
		Map<String, String> properties = Maps.newHashMap();
		Instance instance = unwrap();
		if(instance != null) {
			for(TroveInstanceDisplayPropertyToken propertyToken : TroveInstanceDisplayPropertyToken.values()){
				properties.put(propertyToken.unwrap().getDisplayKey(), propertyToken.getPropertyValue(instance));
			}
		}
		return properties;
	}
	
	  /**
	   * Sets the Trove instance.
	   *
	   * @param instance 	the instance
	   */
	protected void setInstance(Instance instance) {
		super.setDetails(instance);
		InetAddress privateIpAddress = getPrivateIpAddress(instance);
		setPrivateIpAddress(privateIpAddress);
		setPort(MYSQL_PORT);
	}

}

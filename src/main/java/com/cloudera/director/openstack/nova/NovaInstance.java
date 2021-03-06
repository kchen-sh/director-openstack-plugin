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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jclouds.openstack.nova.v2_0.domain.Address;
import org.jclouds.openstack.nova.v2_0.domain.Server;

import com.cloudera.director.spi.v1.compute.util.AbstractComputeInstance;
import com.cloudera.director.spi.v1.util.DisplayPropertiesUtil;
import com.cloudera.director.spi.v1.model.DisplayProperty;
import com.cloudera.director.spi.v1.model.DisplayPropertyToken;
import com.cloudera.director.spi.v1.model.util.SimpleDisplayPropertyBuilder;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;


/**
 * Nova compute instance.
 */
public class NovaInstance 
	extends AbstractComputeInstance<NovaInstanceTemplate, Server>{
	
	/**
	 * The list of display properties (including inherited properties).
	 */
	private static final List<DisplayProperty> DISPLAY_PROPERTIES =
			DisplayPropertiesUtil.asDisplayPropertyList(NovaInstanceDisplayPropertyToken.values());
	
	/**
	 * Returns the list of display properties for an nova instance, including inherited properties.
	 */
	public static List<DisplayProperty> getDisplayProperties() {return DISPLAY_PROPERTIES;}
	
	/**
	 * Nova compute instance display properties.
	 */
	public static enum NovaInstanceDisplayPropertyToken implements DisplayPropertyToken {
		
		/**
		 * The ID of the image used to launch the instance.
		 */
		IMAGE_ID(new SimpleDisplayPropertyBuilder()
				.displayKey("imageID")
				.defaultDescription("The ID of the image used to launch the instance.")
				.sensitive(false)
				.build()) {
			@Override
			protected String getPropertyValue(Server instance) {
				return instance.getImage().getId();
			}	
		},
		
		/**
		 * The ID of the instance.
		 */
		INSTANCE_ID(new SimpleDisplayPropertyBuilder()
				.displayKey("instanceID")
				.defaultDescription("The ID of the instance")
				.sensitive(false)
				.build()) {
			@Override
			protected String getPropertyValue(Server instance) {
				return instance.getId();
			}
		},
		
		/**
		 * The instance type.
		 */
		INSTANCE_TYPE(new SimpleDisplayPropertyBuilder()
				.displayKey("instanceType")
				.defaultDescription("The instance type")
				.sensitive(false)
				.build()) {
			@Override
			protected String getPropertyValue(Server instance) {
				return instance.getFlavor().getName();
			}
		},
		
		/**
		 * The name of the key pair, if this instance was launched with an associated key pair.
		 */
		KEY_PAIR(new SimpleDisplayPropertyBuilder()
				.displayKey("keyName")
				.defaultDescription("The name of the key pair, if this instance was launched with an associated key pair.")
				.sensitive(false)
				.build()) {
			@Override
			protected String getPropertyValue(Server instance) {
				return instance.getKeyName();
			}
		},
		
		LAUNCH_TIME(new SimpleDisplayPropertyBuilder()
				.displayKey("launchTime")
				.defaultDescription("The time the instance was launched.")
				.sensitive(false)
				.build()) {
			@Override 
			protected String getPropertyValue(Server instance) {
				return instance.getCreated().toString();
			}
		},
		
		PRIVATE_IP_ADDRESS(new SimpleDisplayPropertyBuilder()
				.displayKey("privateIpAddress")
				.defaultDescription("The private IP address assigned to the instance.")
				.sensitive(false)
				.build()) {
			@Override
			protected String getPropertyValue(Server instance) {
				return NovaInstance.getPrivateIpAddress(instance).getHostAddress();
			}
		},
		
		PUBLIC_IP_ADDRESS(new SimpleDisplayPropertyBuilder()
				.displayKey("publicIpAddress")
				.defaultDescription("The public IP address assigned to the instance.")
				.sensitive(false)
				.build()) {
			@Override
			protected String getPropertyValue(Server instance) {
				return NovaInstance.getFloatingIpAddress(instance).getHostAddress();
			}
		},
		
		NETWORK_ID(new SimpleDisplayPropertyBuilder()
				.displayKey("networkId")
				.defaultDescription("The ID of the network in which the instance is running.")
				.sensitive(false)
				.build()) {
			@Override
			protected String getPropertyValue(Server instance) {
				return new String("networkID");
			}
		};
		
		/**
		 * The display property.
		 */
		private final DisplayProperty displayProperty;
		
		/**
		 * Create an Nova instance display property token with the specified parameters.
		 * 
		 * @param displayProperty the display property
		 */
		private NovaInstanceDisplayPropertyToken(DisplayProperty displayProperty) {
			this.displayProperty = displayProperty;
		}
		
		/**
		 * Returns the value of the property from the specified instance.
		 * 
		 * @param instance the instance
		 * @return the value of the property from the specified instance
		 */
		protected abstract String getPropertyValue(Server instance);
		
		@Override
		public DisplayProperty unwrap() {
			return displayProperty;
		}
		
	}
	
	public static final Type TYPE = new ResourceType("NovaInstance");	
	
	protected NovaInstance(NovaInstanceTemplate template, String instanceId,
			Server novaInstance) {
		super(template, instanceId, getPrivateIpAddress(novaInstance));
	}

	public Map<String, String> getProperties() {
		Map<String, String> properties = Maps.newHashMap();
		Server instance = unwrap();
		if (instance != null) {
			for (NovaInstanceDisplayPropertyToken propertyToken : NovaInstanceDisplayPropertyToken.values()) {
				properties.put(propertyToken.unwrap().getDisplayKey(), propertyToken.getPropertyValue(instance));
			}
		}
		return properties;
	}
	
	/**
	 * Returns the private IP address of the specified Nova instance.
	 *
	 * @param instance the instance
	 * @return the private IP address of the specified Nova instance
	 * @throws IllegalArgumentException if the instance does not have a valid private IP address
	 */
	private static InetAddress getPrivateIpAddress(Server server) {
		Preconditions.checkNotNull(server, "instance is null");
		InetAddress privateIpAddress = null;
		try {
			Iterator<Address> iterator = server.getAddresses().values().iterator();
			Address address = null;
			while (iterator.hasNext()) {
				// Find the first IPv4 address.
				address = iterator.next();
				if (address.getVersion() == 4) {
					privateIpAddress = InetAddress.getByName(address.getAddr());
					break;
				}
			}
		} catch (UnknownHostException e) {
		  throw new IllegalArgumentException("Invalid private IP address", e);
		}
		return privateIpAddress;
	}

	/**
	 * Returns the Public IP address of the specified Nova instance if it exists.
	 *
	 * @param instance the instance
	 * @return the public IP address of the specified Nova instance
	 */
	private static InetAddress getFloatingIpAddress(Server server) {
		Preconditions.checkNotNull(server, "instance is null");
		InetAddress floatingIpAddress = null;
		try {
			Iterator<Address> iterator = server.getAddresses().values().iterator();
			Address floatingAddress = null;
			// Find the first IPv4 address.
			while (iterator.hasNext()) {
				Address address = iterator.next();
				if (address.getVersion() == 4) {
					break;
				}
			}
			// Find the second IPv4 Address.
			while (iterator.hasNext()) {
				floatingAddress = iterator.next();
				if (floatingAddress.getVersion() == 4) {
					floatingIpAddress = InetAddress.getByName(floatingAddress.getAddr());
					break;
				}
			}
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("Invalid floating IP address", e);
		}
		return floatingIpAddress;
	}

}


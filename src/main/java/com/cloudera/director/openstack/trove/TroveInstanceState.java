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

import java.util.Collections;
import java.util.Map;

import org.jclouds.openstack.trove.v1.domain.Instance.Status;

import com.cloudera.director.spi.v1.model.InstanceStatus;
import com.cloudera.director.spi.v1.model.util.AbstractInstanceState;
import com.google.common.collect.Maps;

/**
 * Trove instance state implementation
 */
public class TroveInstanceState extends AbstractInstanceState<Status> {
	
	/**
	 * The map from Trove instance state to Director instance state.
	 */
	private static final Map<Status, TroveInstanceState> INSTANCE_STATE_MAP;
	
	static {
		Map<Status, TroveInstanceState> map = Maps.newEnumMap(Status.class);

		map.put(Status.ACTIVE, new TroveInstanceState(InstanceStatus.RUNNING,Status.ACTIVE));
		map.put(Status.BLOCKED, new TroveInstanceState(InstanceStatus.FAILED,Status.BLOCKED));
		map.put(Status.BUILD, new TroveInstanceState(InstanceStatus.PENDING,Status.BUILD));
		map.put(Status.REBOOT, new TroveInstanceState(InstanceStatus.PENDING,Status.REBOOT));
		map.put(Status.RESIZE, new TroveInstanceState(InstanceStatus.PENDING,Status.RESIZE));
		map.put(Status.SHUTDOWN, new TroveInstanceState(InstanceStatus.STOPPED,Status.SHUTDOWN));
		map.put(Status.UNRECOGNIZED, new TroveInstanceState(InstanceStatus.UNKNOWN,Status.UNRECOGNIZED));
		
		INSTANCE_STATE_MAP = Collections.unmodifiableMap(map);
	}
	
	/**
	 * Returns the Director instance state for the specified Trove instance name.
	 * @param status 	the Trove instance state name
	 * @return the corresponding Director instance state.
	 */
	public static TroveInstanceState fromTroveStatus(Status status) {
		return (status == null) ? INSTANCE_STATE_MAP.get(Status.UNRECOGNIZED) : INSTANCE_STATE_MAP.get(status);
	}
	
	/**
	 * Create a Trove instance state with the specified parameters.
	 * @param instanceStatus 		the instance status
	 * @param instanceStateDetails 	the provider-specific instance details
	 */
	public TroveInstanceState(InstanceStatus instanceStatus, Status instanceStateDetails) {
		super(instanceStatus, instanceStateDetails);
	}

}

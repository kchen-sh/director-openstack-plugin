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
import org.jclouds.apis.ApiMetadata;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.openstack.trove.v1.TroveApi;
import org.jclouds.openstack.trove.v1.TroveApiMetadata;

import com.cloudera.director.openstack.OpenStackCredentials;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

public class TroveApiProvider {
	
	private static final ApiMetadata TROVE_API_METADATA = new TroveApiMetadata();
	
	static TroveApi buildTroveApi(OpenStackCredentials cre) {
		Iterable<Module> modules = ImmutableSet.<Module>of(new SLF4JLoggingModule());
	
		return ContextBuilder.newBuilder(TROVE_API_METADATA)
				.endpoint(cre.getEndpoint())
				.credentials(cre.getIdentity(), cre.getCredential())
				.modules(modules)
				.buildApi(TroveApi.class);		
	}
}

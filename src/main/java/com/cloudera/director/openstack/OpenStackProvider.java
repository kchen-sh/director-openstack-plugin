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

package com.cloudera.director.openstack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

import com.cloudera.director.openstack.nova.NovaProvider;
import com.cloudera.director.openstack.nova.NovaProviderConfigurationValidator;
import com.cloudera.director.openstack.trove.TroveProvider;
import com.cloudera.director.openstack.trove.TroveProviderConfigurationValidator;
import com.cloudera.director.spi.v1.model.ConfigurationProperty;
import com.cloudera.director.spi.v1.model.ConfigurationValidator;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.util.CompositeConfigurationValidator;
import com.cloudera.director.spi.v1.provider.CloudProviderMetadata;
import com.cloudera.director.spi.v1.provider.CredentialsProvider;
import com.cloudera.director.spi.v1.provider.ResourceProvider;
import com.cloudera.director.spi.v1.provider.ResourceProviderMetadata;
import com.cloudera.director.spi.v1.provider.util.AbstractCloudProvider;
import com.cloudera.director.spi.v1.provider.util.SimpleCloudProviderMetadataBuilder;
import com.typesafe.config.Config;

public class OpenStackProvider extends AbstractCloudProvider {

	/**
	 * The cloud provider ID.
	 */
	public static final String ID = "openstack";

	public static boolean featureFlag = Boolean.parseBoolean(System.getenv("TROVE_ENABLE"));

	/**
	 * The resource provider metadata.
	 */
	private static final List<ResourceProviderMetadata> RESOURCE_PROVIDER_METADATA = featureFlag ? 
			Collections.unmodifiableList(Arrays.asList(NovaProvider.METADATA, TroveProvider.METADATA)) : Collections.unmodifiableList(Arrays.asList(NovaProvider.METADATA));

	private OpenStackCredentials credentials;
	private Config openstackConfig;

	protected OpenStackCredentials getOpenStackCredentials(Configured configuration, LocalizationContext localizationContext) {
		CredentialsProvider<OpenStackCredentials> provider = new OpenStackCredentialsProvider();
		OpenStackCredentials credentials = provider.createCredentials(configuration, localizationContext);
		checkNotNull(credentials, "OpenStackCredentials is null!");
		return credentials;
	}

	/**
	 * The cloud provider metadata.
	 */
	protected static final CloudProviderMetadata METADATA = new SimpleCloudProviderMetadataBuilder()
			.id(ID)
			.name("OpenStack")
			.description("OpenStack cloud provider implementation")
			.configurationProperties(Collections.<ConfigurationProperty> emptyList())
			.credentialsProviderMetadata(OpenStackCredentialsProvider.METADATA)
			.resourceProviderMetadata(RESOURCE_PROVIDER_METADATA)
			.build();

	public OpenStackProvider(Configured configuration, Config openstackConfig,LocalizationContext rootLocalizationContext) {
		super(METADATA, rootLocalizationContext);
		this.openstackConfig = openstackConfig;
		this.credentials = getOpenStackCredentials(configuration, rootLocalizationContext);
	}

	protected ConfigurationValidator getResourceProviderConfigurationValidator(ResourceProviderMetadata resourceProviderMetadata) {
		ConfigurationValidator providerSpecificValidator;
		if (resourceProviderMetadata.getId().equals(NovaProvider.METADATA.getId())) {
			providerSpecificValidator = new NovaProviderConfigurationValidator(credentials);
		} else if (resourceProviderMetadata.getId().equals(TroveProvider.METADATA.getId())) {
			providerSpecificValidator = new TroveProviderConfigurationValidator(credentials);
		} else {
			throw new IllegalArgumentException("No such provider: " + resourceProviderMetadata.getId());
		}

		return new CompositeConfigurationValidator(METADATA.getProviderConfigurationValidator(), providerSpecificValidator);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public ResourceProvider createResourceProvider(String resourceProviderId, Configured configuration) {
		ResourceProviderMetadata resourceProviderMetadata = getProviderMetadata().getResourceProviderMetadata(resourceProviderId);
		if (resourceProviderMetadata.getId().equals(NovaProvider.METADATA.getId())) {
			return new NovaProvider(configuration, this.credentials, this.openstackConfig, getLocalizationContext());
		}

		if (resourceProviderMetadata.getId().equals(TroveProvider.METADATA.getId())) {
			return new TroveProvider(configuration, this.credentials, getLocalizationContext());
		}

		throw new IllegalArgumentException("No such provider: " + resourceProviderMetadata.getId());
	}

}


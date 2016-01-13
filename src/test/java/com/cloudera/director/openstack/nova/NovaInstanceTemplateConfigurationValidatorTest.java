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

import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.AVAILABILITY_ZONE;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.IMAGE;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.KEY_NAME;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.SECURITY_GROUP_NAMES;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.VOLUME_NUMBER;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationProperty.VOLUME_SIZE;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationValidator.INVALID_KEY_NAME_MSG;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationValidator.INVALID_IMAGE_MSG;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationValidator.INVALID_SECURITY_GROUP_NAME_MSG;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationValidator.INVALID_AVAILABILITY_ZONE_MSG;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationValidator.INVALID_PREFIX_LENGTH_MSG;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationValidator.INVALID_VOLUME_NUMBER_MSG;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationValidator.INVALID_VOLUME_SIZE_MSG;
import static com.cloudera.director.openstack.nova.NovaInstanceTemplateConfigurationValidator.PREFIX_MISSING_MSG;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.google.common.collect.Maps;
import org.jclouds.openstack.nova.v2_0.NovaApi;
import org.jclouds.openstack.nova.v2_0.extensions.AvailabilityZoneApi;
import org.jclouds.openstack.nova.v2_0.extensions.KeyPairApi;
import org.jclouds.openstack.nova.v2_0.extensions.SecurityGroupApi;
import org.jclouds.openstack.nova.v2_0.features.ImageApi;
import org.jclouds.openstack.nova.v2_0.domain.Image;
import org.jclouds.openstack.nova.v2_0.domain.KeyPair;
import org.jclouds.openstack.nova.v2_0.domain.SecurityGroup;
import org.jclouds.openstack.nova.v2_0.domain.regionscoped.AvailabilityZone;
import org.junit.Before;

import com.cloudera.director.openstack.OpenStackCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.google.common.base.Optional;
import org.junit.Test;


/**
 * Tests {@link NovaInstanceTemplateConfigurationValidator}.
 */
public class NovaInstanceTemplateConfigurationValidatorTest {

	private NovaProvider novaProvider;
	private OpenStackCredentials credentials;
	private NovaApi novaApi;
	private String region = "regionOne";
	private NovaInstanceTemplateConfigurationValidator validator;
	private PluginExceptionConditionAccumulator accumulator;
	private LocalizationContext localizationContext = new DefaultLocalizationContext(Locale.getDefault(),"");
	
	@Before
	public void setUp() {
		novaProvider = mock(NovaProvider.class);
		credentials = mock(OpenStackCredentials.class);
		novaApi = mock(NovaApi.class);
		when(novaProvider.getNovaApi()).thenReturn(novaApi);
		when(novaProvider.getRegion()).thenReturn(region);
		validator = new NovaInstanceTemplateConfigurationValidator(novaProvider);
		accumulator = new PluginExceptionConditionAccumulator();
		
		AvailabilityZoneApi availabilityZoneApi = mock(AvailabilityZoneApi.class);
		Optional<AvailabilityZoneApi> optionalAvailabilityZoneApi = Optional.of(availabilityZoneApi);
		when(novaApi.getAvailabilityZoneApi(region)).thenReturn(optionalAvailabilityZoneApi);		
		AvailabilityZone availabilityZone = mock(AvailabilityZone.class);
		when(availabilityZone.getName()).thenReturn("zone");
		FluentIterable<AvailabilityZone> availabilityZones= FluentIterable.from(Lists.newArrayList(availabilityZone));
		when(availabilityZoneApi.listAvailabilityZones()).thenReturn(availabilityZones);
		
		KeyPairApi keyPairApi = mock(KeyPairApi.class);
		Optional<KeyPairApi> optionalKeyPairApi = Optional.of(keyPairApi);
		when(novaApi.getKeyPairApi(region)).thenReturn(optionalKeyPairApi);
		KeyPair keyPair = mock(KeyPair.class);
		when(keyPairApi.get(eq("keypair"))).thenReturn(keyPair);
		when(keyPairApi.get(not(eq("keypair")))).thenReturn(null);
		
		ImageApi imageApi = mock(ImageApi.class);
		when(novaApi.getImageApi(region)).thenReturn(imageApi);
		Image image = mock(Image.class);
		when(image.getStatus()).thenReturn(Image.Status.ACTIVE);
		when(imageApi.get(eq("myImage"))).thenReturn(image);
		when(imageApi.get(not(eq("myImage")))).thenReturn(null);
		
		SecurityGroupApi securityGroupApi = mock(SecurityGroupApi.class);
		Optional<SecurityGroupApi> optionalSecurityGroupApi = Optional.of(securityGroupApi);
		when(novaApi.getSecurityGroupApi(region)).thenReturn(optionalSecurityGroupApi);		
		SecurityGroup securityGroup = mock(SecurityGroup.class);
		when(securityGroup.getName()).thenReturn("myGroup");
		FluentIterable<SecurityGroup> securityGroups= FluentIterable.from(Lists.newArrayList(securityGroup));
		when(securityGroupApi.list()).thenReturn(securityGroups);
	}


	@Test
	public void testCheckAvailabilityZone() {
		String zoneName = "zone";
		checkAvailabilityZone(zoneName);
		verifyClean();
	}

	@Test
	public void testCheckAvailabilityZone_NotFound() throws IOException {
		String zoneName = "nonZone";
		checkAvailabilityZone(zoneName);
		verifySingleError(AVAILABILITY_ZONE, INVALID_AVAILABILITY_ZONE_MSG, zoneName);
	}

	@Test
	public void testCheckImage() {
		String imageName = "myImage";
		checkImage(imageName);
		verifyClean();
	}

	@Test
	public void testCheckImage_NotFound() {
		String imageName = "noImage";
		checkImage(imageName);
		verifySingleError(IMAGE, INVALID_IMAGE_MSG, imageName);
	}

	@Test
	public void testValidateKeyName() {
		String keyName = "keypair";
		checkKeyName(keyName);
		verifyClean();
	}

	@Test
	public void testvalidateKeyName_NoKeyPair() {
		String keyName = "noKeypair";
		checkKeyName(keyName);
		verifySingleError(KEY_NAME, INVALID_KEY_NAME_MSG, keyName);
	}

	@Test
	public void testSecurityGroup() {
		String securityGroupNames = "myGroup";
		checkSecurityGroupNames(securityGroupNames);
		verifyClean();
	}

	@Test
	public void testSecurityGroup_NoGroup() {
		String securityGroupNames = "nogroup";
		checkSecurityGroupNames(securityGroupNames);
		verifySingleError(SECURITY_GROUP_NAMES, INVALID_SECURITY_GROUP_NAME_MSG);
	}

	@Test
	public void testCheckPrefix() throws IOException {
		checkPrefix("director");
		checkPrefix("some-other-prefix");
		checkPrefix("length-is-eq-26-characters");
		checkPrefix("c");
		checkPrefix("c-d");
		checkPrefix("ends-with-digit-1");
		verifyClean();

	}

	@Test
	public void testCheckPrefix_Missing() throws IOException {
		checkPrefix(null);
		verifySingleError(INSTANCE_NAME_PREFIX, PREFIX_MISSING_MSG);
	}

	@Test
	public void testCheckPrefix_TooLong() throws IOException {
		checkPrefix("the-length-eqs-239----------"
				+ "123456789012345678901234567890"
				+ "123456789012345678901234567890"
				+ "123456789012345678901234567890"
				+ "123456789012345678901234567890"
				+ "123456789012345678901234567890"
				+ "123456789012345678901234567890"
				+ "123456789012345678901234567890");
		verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_LENGTH_MSG);
	}

	@Test
	public void testCheckVolumeNumber() throws IOException {
		checkVolumeNum("1");
		checkVolumeNum("10");
		verifyClean();
	}

	@Test
	public void testCheckVolumeNumber_Null() throws IOException {
		checkVolumeNum("");
		verifySingleError(VOLUME_NUMBER, INVALID_VOLUME_NUMBER_MSG);
	}

	@Test
	public void testCheckVolumeNumber_NotNumber() throws IOException {
		checkVolumeNum("xyz");
		verifySingleError(VOLUME_NUMBER, INVALID_VOLUME_NUMBER_MSG);
	}

	@Test
	public void testCheckVolumeNumber_TooSmall() throws IOException {
		checkVolumeNum("-1");
		verifySingleError(VOLUME_NUMBER, INVALID_VOLUME_NUMBER_MSG);
	}

	@Test
	public void testCheckVolumeSize() throws IOException {
		checkVolumeSize("1");
		checkVolumeSize("100");
		verifyClean();
	}

	@Test
	public void testCheckVolumeSize_Null() throws IOException {
		checkVolumeSize("");
		verifySingleError(VOLUME_SIZE, INVALID_VOLUME_SIZE_MSG);
	}

	@Test
	public void testCheckVolumeSize_NotNumber() throws IOException {
		checkVolumeSize("xyz");
		verifySingleError(VOLUME_SIZE, INVALID_VOLUME_SIZE_MSG);
	}

	@Test
	public void testCheckVolumeSize_TooSmall() throws IOException {
		checkVolumeSize("-1");
		verifySingleError(VOLUME_SIZE, INVALID_VOLUME_SIZE_MSG);
	}

	/**
	 * Invokes checkAvailabilityZone with the specified configuration.
	 *
	 * @param zoneName the availability zone name
	 */
	protected void checkAvailabilityZone(String zoneName) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(AVAILABILITY_ZONE.unwrap().getConfigKey(), zoneName);
		Configured configuration = new SimpleConfiguration(configMap);
		validator.checkAvailabilityZone(novaApi, region, configuration, accumulator, localizationContext);
	}

	/**
	 * Invokes checkImage with the specified configuration.
	 *
	 * @param image the image name
	 */
	protected void checkImage(String image) {
		Map<String, String> configMap = org.assertj.core.util.Maps.newHashMap();
		configMap.put(IMAGE.unwrap().getConfigKey(), image);
		Configured configuration = new SimpleConfiguration(configMap);
		validator.checkImage(novaApi, region, configuration, accumulator, localizationContext);
	}

	/**
	 * Invokes checkKeyName with the specified configuration.
	 *
	 * @param keyName the key name
	 */
	protected void checkKeyName(String keyName) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(KEY_NAME.unwrap().getConfigKey(), keyName);
		Configured configuration = new SimpleConfiguration(configMap);
		validator.checkKeyName(novaApi, region,configuration, accumulator, localizationContext);
	}

	/**
	 * Invokes checkSecurityGroupNames with the specified configuration.
	 *
	 * @param keyName the key name
	 */
	protected void checkSecurityGroupNames(String securityGroupNames) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(SECURITY_GROUP_NAMES.unwrap().getConfigKey(), securityGroupNames);
		Configured configuration = new SimpleConfiguration(configMap);
		validator.checkSecurityGroupNames(novaApi, region,configuration, accumulator, localizationContext);
	}

	/**
	 * Invokes checkPrefix with the specified configuration.
	 *
	 * @param prefix the instance name prefix
	 */
	protected void checkPrefix(String prefix) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(INSTANCE_NAME_PREFIX.unwrap().getConfigKey(), prefix);
		Configured configuration = new SimpleConfiguration(configMap);
		NovaInstanceTemplateConfigurationValidator.checkPrefix(configuration, accumulator, localizationContext);
	}

	/**
	 * Invokes checkVolumeNum with the specified configuration.
	 *
	 * @param volNum the volume number
	 */
	protected void checkVolumeNum(String volNum) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(VOLUME_NUMBER.unwrap().getConfigKey(), volNum);
		Configured configuration = new SimpleConfiguration(configMap);
		NovaInstanceTemplateConfigurationValidator.checkVolumeNum(configuration, accumulator, localizationContext);
	}

	/**
	 * Invokes checkVolumeSize with the specified configuration.
	 *
	 * @param volSize the volume size
	 */
	protected void checkVolumeSize(String volSize) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(VOLUME_SIZE.unwrap().getConfigKey(), volSize);
		Configured configuration = new SimpleConfiguration(configMap);
		NovaInstanceTemplateConfigurationValidator.checkVolumeSize(configuration, accumulator, localizationContext);
	}

	/**
	 * Verifies that the specified plugin exception condition accumulator contains no errors or
	 * warnings.
	 */
	private void verifyClean() {
		Map<String, Collection<PluginExceptionCondition>> conditionsByKey = accumulator.getConditionsByKey();
		assertThat(conditionsByKey).isEmpty();
	}

	/**
	 * Verifies that the specified plugin exception condition accumulator contains exactly
	 * one condition, which must be an error associated with the specified property.
	 *
	 * @param token the configuration property token for the property which should be in error
	 */
	private void verifySingleError(ConfigurationPropertyToken token) {
		verifySingleError(token, Optional.<String>absent());
	}

	/**
	 * Verifies that the specified plugin exception condition accumulator contains exactly
	 * one condition, which must be an error with the specified message and associated with the
	 * specified property.
	 *
	 * @param token	the configuration property token for the property which should be in error
	 * @param errorMsg the expected error message
	 * @param args	 the error message arguments
	 */
	private void verifySingleError(ConfigurationPropertyToken token, String errorMsg, Object... args) {
		verifySingleError(token, Optional.of(errorMsg), args);
	}

	/**
	 * Verifies that the specified plugin exception condition accumulator contains exactly
	 * one condition, which must be an error with the specified message and associated with the
	 * specified property.
	 *
	 * @param token		  the configuration property token for the property which should be in error
	 * @param errorMsgFormat the expected error message
	 * @param args		   the error message arguments
	 */
	private void verifySingleError(ConfigurationPropertyToken token, Optional<String> errorMsgFormat, Object... args) {
		Map<String, Collection<PluginExceptionCondition>> conditionsByKey = accumulator.getConditionsByKey();
		assertThat(conditionsByKey).hasSize(1);
		String configKey = token.unwrap().getConfigKey();
		assertThat(conditionsByKey.containsKey(configKey)).isTrue();
		Collection<PluginExceptionCondition> keyConditions = conditionsByKey.get(configKey);
		assertThat(keyConditions).hasSize(1);
		PluginExceptionCondition condition = keyConditions.iterator().next();
		verifySingleErrorCondition(condition, errorMsgFormat, args);
	}

	/**
	 * Verifies that the specified plugin exception condition is an error with the specified message.
	 *
	 * @param condition	  the plugin exception condition
	 * @param errorMsgFormat the expected error message format
	 * @param args		   the error message arguments
	 */
	private void verifySingleErrorCondition(PluginExceptionCondition condition,
			Optional<String> errorMsgFormat, Object... args) {
		assertThat(condition.isError()).isTrue();
		if (errorMsgFormat.isPresent()) {
			assertThat(condition.getMessage()).isEqualTo(String.format(errorMsgFormat.get(), args));
		}
	}
}


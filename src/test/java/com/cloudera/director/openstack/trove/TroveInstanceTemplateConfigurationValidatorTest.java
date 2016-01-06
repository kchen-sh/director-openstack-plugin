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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.assertj.core.util.Maps;
import org.jclouds.openstack.trove.v1.TroveApi;
import org.jclouds.openstack.trove.v1.domain.Flavor;
import org.jclouds.openstack.trove.v1.features.FlavorApi;
import org.junit.Before;
import org.junit.Test;

import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.cloudera.director.spi.v1.model.InstanceTemplate.InstanceTemplateConfigurationPropertyToken.INSTANCE_NAME_PREFIX;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.MASTER_USER_PASSWORD;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.MASTER_USER_NAME;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.FLAVOR_ID;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationProperty.VOLUME_SIZE;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationValidator.USERNAME_MISSING_MSG;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationValidator.INVALID_PASSWORD_LENGTH_MSG;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationValidator.INVALID_USERNAME_LENGTH_MSG;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationValidator.INVALID_PREFIX_LENGTH_MSG;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationValidator.INVALID_PREFIX_MSG;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationValidator.PREFIX_MISSING_MSG;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationValidator.INVALID_VOLUME_SIZE;
import static com.cloudera.director.openstack.trove.TroveInstanceTemplateConfigurationValidator.INVALID_FLAVOR_ID_MSG;

public class TroveInstanceTemplateConfigurationValidatorTest {

	private static final String region = "RegionOne";
	
	private TroveProvider troveProvider;
	private TroveApi troveApi;
	private FlavorApi flavorApi;
	private FluentIterable<Flavor> flavors;
	private TroveInstanceTemplateConfigurationValidator validator;
	private PluginExceptionConditionAccumulator accumulator;
	private LocalizationContext localizationContext = new DefaultLocalizationContext(Locale.getDefault(), "");

	@SuppressWarnings("unchecked")
	@Before
	public void setUp() {
		troveProvider = mock(TroveProvider.class);
		troveApi = mock(TroveApi.class);
		flavorApi = mock(FlavorApi.class);
		flavors = mock(FluentIterable.class);
		
		Flavor f1 = mock(Flavor.class);
		Flavor f2 = mock(Flavor.class);
		Flavor f3 = mock(Flavor.class);
		when(f1.getId()).thenReturn(1);
		when(f2.getId()).thenReturn(2);
		when(f3.getId()).thenReturn(3);
		mockIterable(flavors, f1, f2, f3);
		
		when(troveProvider.getTroveApi()).thenReturn(troveApi);
		when(troveApi.getFlavorApi(region)).thenReturn(flavorApi);
		when(flavorApi.list()).thenReturn(flavors);
		
		validator = new TroveInstanceTemplateConfigurationValidator(troveProvider);
		accumulator = new PluginExceptionConditionAccumulator();
	
	}
	
	/**
	 * mock FluentIterable<Flavor>
	 * 
	 * @param flavors
	 * @param values
	 */
	@SuppressWarnings({ "hiding", "unchecked" })
	private <Flavor> void mockIterable(FluentIterable<Flavor> flavors, Flavor... values) {
		
		Iterator<Flavor> mockIterator = mock(Iterator.class);
		when(flavors.iterator()).thenReturn(mockIterator);
		
		if (values.length == 0) {
			when(mockIterator.hasNext()).thenReturn(false);
			return;
		} else if (values.length == 1) {
			when(mockIterator.hasNext()).thenReturn(true, false);
			when(mockIterator.next()).thenReturn(values[0]);
		} else {
			Boolean[] hasNextResponses = new Boolean[values.length];
			for (int i = 0; i < hasNextResponses.length -1 ; i++) {
				hasNextResponses[i] = true;
			}
			hasNextResponses[hasNextResponses.length - 1] = false;
			when(mockIterator.hasNext()).thenReturn(true, hasNextResponses);
			Flavor[] valuesMinusTheFirst = Arrays.copyOfRange(values, 1, values.length);
			when(mockIterator.next()).thenReturn(values[0], valuesMinusTheFirst);
		}
	}

	@Test
	public void testCheckPassword() {
		checkPassword("root");
		checkPassword("r");
		checkPassword("root_root__root");
		verifyClean();
	}
	
	@Test
	public void testCheckPassword_TooShort() {
		checkPassword("");
		verifySingleError(MASTER_USER_PASSWORD, INVALID_PASSWORD_LENGTH_MSG);
	}
	
	@Test
	public void testCheckPassword_TooLong() {
		checkPassword("root_rootrootroot");
		verifySingleError(MASTER_USER_PASSWORD, INVALID_PASSWORD_LENGTH_MSG);
	}
	
	@Test
	public void testCheckUserName() {
		checkUsername("root");
		checkUsername("r");
		checkUsername("root_root__root");
		verifyClean();
	}
	
	@Test
	public void testCheckUserName_Missing() {
		checkUsername(null);
		verifySingleError(MASTER_USER_NAME, USERNAME_MISSING_MSG);
	}
	
	@Test
	public void testCheckUserName_TooShort() {
		checkUsername("");
		verifySingleError(MASTER_USER_NAME, INVALID_USERNAME_LENGTH_MSG);
	}
	
	@Test
	public void testCheckUserName_TooLong() {
		checkUsername("root_rootrootroot");
		verifySingleError(MASTER_USER_NAME, INVALID_USERNAME_LENGTH_MSG);
	}
	
	@Test
	public void testCheckPrefix_Missing() {
		checkPrefix(null);
		verifySingleError(INSTANCE_NAME_PREFIX, PREFIX_MISSING_MSG);
	}

	@Test
	public void testCheckPrefix_TooShort() {
		checkPrefix("");
		verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_LENGTH_MSG);
	}

	@Test
	public void testCheckPrefix_TooLong() {
		checkPrefix("the-length-eqs-twenty-seven");
		verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_LENGTH_MSG);
	}

	@Test
	public void testCheckPrefix_StartsWithUppercaseLetter() {
		checkPrefix("Bad-prefix");
		verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
	}

	@Test
	public void testCheckPrefix_StartsWithDash() {
		checkPrefix("-bad-prefix");
		verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
	}

	@Test
	public void testCheckPrefix_StartsWithDigit() {
		checkPrefix("1-bad-prefix");
		verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
	}

	@Test
	public void testCheckPrefix_ContainsUppercaseLetter() {
		checkPrefix("badPrefix");
		verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
	}

	@Test
	public void testCheckPrefix_ContainsUnderscore() {
		checkPrefix("bad_prefix");
		verifySingleError(INSTANCE_NAME_PREFIX, INVALID_PREFIX_MSG);
	}
	
	@Test
	public void testCheckVolumeSize() {
		checkVolumeSize("1");
		checkVolumeSize("12");
		verifyClean();
	}
	
	@Test
	public void testCheckVolumeSize_Negative() {
		checkVolumeSize("-1");
		verifySingleError(VOLUME_SIZE, INVALID_VOLUME_SIZE);
	}
	
	@Test
	public void testCheckVolumeSize_alphabet() {
		checkVolumeSize("a");
		verifySingleError(VOLUME_SIZE, INVALID_VOLUME_SIZE);
	}
	
	@Test
	public void testCheckVolumeSize_SpecialCharacter() {
		checkVolumeSize("#");
		verifySingleError(VOLUME_SIZE, INVALID_VOLUME_SIZE);
	}
	
	@Test
	public void testCheckVolumeSize_Zero() {
		checkVolumeSize("0");
		verifySingleError(VOLUME_SIZE, INVALID_VOLUME_SIZE);
	}
	
	@Test
	public void testCheckFlavorId_Existed() {
		TroveApi testTroveApi = troveProvider.getTroveApi();
		checkFlavorId(testTroveApi, "1");
		verifyClean();
	}	

	@Test
	public void testCheckFlavorId_Not_Existed() {
		TroveApi testTroveApi = troveProvider.getTroveApi();
		String testFlavorId = "4";
		checkFlavorId(testTroveApi, testFlavorId);
		verifySingleError(FLAVOR_ID, INVALID_FLAVOR_ID_MSG, testFlavorId);
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
		validator.checkPrefix(configuration, accumulator, localizationContext);
	}

	/**
	 * Invokes checkFlavorId with the specified configuration.
	 *
	 * @param flavorId	the instance flavor id
	 */
	protected void checkFlavorId(TroveApi troveApi, String flavorId) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(FLAVOR_ID.unwrap().getConfigKey(), flavorId);
		Configured configuration = new SimpleConfiguration(configMap);
		validator.checkFlavorId(troveApi, region, configuration, accumulator, localizationContext);
	}
	
	/**
	 * Invokes checkVolumeSize with the specified configuration.
	 * 
	 * @param volumeSize	the instance volume size
	 */
	protected void checkVolumeSize(String volumeSize) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(VOLUME_SIZE.unwrap().getConfigKey(), volumeSize);
		Configured configuration = new SimpleConfiguration(configMap);		
		validator.checkVolumeSize(configuration, accumulator, localizationContext);
	}
	
	/**
	 * Invokes checkPassword with the specified configuration.
	 *
	 * @param password	the password
	 */
	protected void checkPassword(String password) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(MASTER_USER_PASSWORD.unwrap().getConfigKey(), password);
		Configured configuration = new SimpleConfiguration(configMap);
		validator.checkPassword(configuration, accumulator, localizationContext);
	}

	/**
	 * Invokes checkUsername with the specified configuration.
	 *
	 * @param username	the instance username
	 */
	protected void checkUsername(String username) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(MASTER_USER_NAME.unwrap().getConfigKey(), username);
		Configured configuration = new SimpleConfiguration(configMap);
		validator.checkUsername(configuration, accumulator, localizationContext);
	}

	/**
	 * Verifies that the specified plugin exception condition accumulator
	 * contains no errors or warnings.
	 */
	private void verifyClean() {
		Map<String, Collection<PluginExceptionCondition>> conditionsByKey = accumulator.getConditionsByKey();
		assertThat(conditionsByKey).isEmpty();
	}

	/**
	 * Verifies that the specified plugin exception condition accumulator
	 * contains exactly one condition, which must be an error with the specified
	 * message and associated with the specified property.
	 *
	 * @param token		the configuration property token for the property which should be in error
	 * @param errorMsg	the expected error message
	 * @param args		the error message arguments
	 */
	private void verifySingleError(ConfigurationPropertyToken token,
			String errorMsg, Object... args) {
		verifySingleError(token, Optional.of(errorMsg), args);
	}

	/**
	 * Verifies that the specified plugin exception condition accumulator
	 * contains exactly one condition, which must be an error with the specified
	 * message and associated with the specified property.
	 *
	 * @param token				the configuration property token for the property which should be in error
	 * @param errorMsgFormat	the expected error message
	 * @param args				the error message arguments
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
	 * Verifies that the specified plugin exception condition is an error with
	 * the specified message.
	 *
	 * @param condition			the plugin exception condition
	 * @param errorMsgFormat	the expected error message format
	 * @param args				the error message arguments
	 */
	private void verifySingleErrorCondition(PluginExceptionCondition condition, Optional<String> errorMsgFormat, Object... args) {
		assertThat(condition.isError()).isTrue();
		if (errorMsgFormat.isPresent()) {
			assertThat(condition.getMessage()).isEqualTo(String.format(errorMsgFormat.get(), args));
		}
	}

}

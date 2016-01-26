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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.assertj.core.util.Maps;
import org.jclouds.openstack.trove.v1.TroveApi;
import org.junit.Before;
import org.junit.Test;

import com.cloudera.director.openstack.OpenStackCredentials;
import com.cloudera.director.spi.v1.model.ConfigurationPropertyToken;
import com.cloudera.director.spi.v1.model.Configured;
import com.cloudera.director.spi.v1.model.LocalizationContext;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionCondition;
import com.cloudera.director.spi.v1.model.exception.PluginExceptionConditionAccumulator;
import com.cloudera.director.spi.v1.model.util.DefaultLocalizationContext;
import com.cloudera.director.spi.v1.model.util.SimpleConfiguration;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;

import static com.cloudera.director.openstack.trove.TroveProviderConfigurationProperty.REGION;
import static com.cloudera.director.openstack.trove.TroveProviderConfigurationValidator.REGION_NOT_FOUND_MSG;

public class TroveProviderConfigurationValidatorTest {

	private TroveApi troveApi;
	private OpenStackCredentials credentials;
	private TroveProviderConfigurationValidator validator;
	private PluginExceptionConditionAccumulator accumulator;
	private LocalizationContext localizationContext = new DefaultLocalizationContext(Locale.getDefault(), "");

	@Before
	public void setUp() {
		credentials = mock(OpenStackCredentials.class);
		troveApi = mock(TroveApi.class);
	
		//prepare test regions
		Set<String> regions = Sets.newHashSet();
		regions.add("RegionOne");
		regions.add("RegionTwo");
		regions.add("RegionThree");
		when(troveApi.getConfiguredRegions()).thenReturn(regions);
		
		validator = new TroveProviderConfigurationValidator(credentials);
		accumulator = new PluginExceptionConditionAccumulator();
	}

	@Test
	public void testCheckRegion() {
		checkRegion("RegionOne");
		verifyClean();
	}
	
	@Test
	public void testCheckRegion_Insensitive(){
		String testRegion = "REGIONONE";
		checkRegion(testRegion);
		verifySingleError(REGION, REGION_NOT_FOUND_MSG, testRegion);
	}

	@Test
	public void testCheckRegion_Empty() throws IOException {
		String testRegion = "";
		checkRegion(testRegion);
		verifySingleError(REGION, REGION_NOT_FOUND_MSG, testRegion);
	}
	
	@Test
	public void testCheckRegion_NotFound() throws IOException {
		String testRegion = "RegionFour";
		checkRegion(testRegion);
		verifySingleError(REGION, REGION_NOT_FOUND_MSG, testRegion);
	}	

	/**
	 * Invokes checkRegion with the specified configuration.
	 *
	 * @param region	the region name
	 */
	protected void checkRegion(String region) {
		Map<String, String> configMap = Maps.newHashMap();
		configMap.put(REGION.unwrap().getConfigKey(), region);
		Configured configuration = new SimpleConfiguration(configMap);
		validator.checkRegion(troveApi, configuration, accumulator, localizationContext);
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
	private void verifySingleError(ConfigurationPropertyToken token, String errorMsg, Object... args) {
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

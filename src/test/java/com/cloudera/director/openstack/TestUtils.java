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

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.junit.Ignore;

import com.google.common.io.Files;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@Ignore
public class TestUtils {
	
	public static String readFile(String path, Charset encoding) throws IOException {
		return Files.toString(new File(path), encoding);
	}
	
	public static String readRequiredSystemProperty(String systemPropertyKey) {
		String systemPropertyValue = System.getProperty(systemPropertyKey,"");

		if (systemPropertyValue.isEmpty()) {
			fail("System property '" + systemPropertyKey + "' is required.");
		 }
		return systemPropertyValue;
	}
	
	public static String readFileIfSpecified(String fileName) throws IOException {
		if (fileName != null && !fileName.isEmpty()) {
			return TestUtils.readFile(fileName, Charset.defaultCharset());
		} 
		else {
			return null;
		}
	}
	
	public static Config buildOpenStackConfig(String fileName) throws IOException {
		return ConfigFactory.parseFile(new File(fileName));
	}
}


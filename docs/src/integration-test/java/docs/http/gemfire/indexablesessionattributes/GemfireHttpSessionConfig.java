/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package docs.http.gemfire.indexablesessionattributes;

import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemfireHttpSession;

import docs.AbstractGemfireIntegrationTests;

/**
 * @author Rob Winch
 *
 */
// tag::class-start[]
@EnableGemfireHttpSession(indexableSessionAttributes = { "name1", "name2", "name3" })
public class GemfireHttpSessionConfig {
// end::class-start[]

	@Bean
	Properties gemfireProperties() {
		Properties gemfireProperties = new Properties();

		gemfireProperties.setProperty("name", GemfireHttpSessionConfig.class.getName());
		gemfireProperties.setProperty("mcast-port", "0");
		gemfireProperties.setProperty("log-level", AbstractGemfireIntegrationTests.GEMFIRE_LOG_LEVEL);

		return gemfireProperties;
	}

	@Bean
	CacheFactoryBean gemfireCache() {
		CacheFactoryBean gemfireCache = new CacheFactoryBean();

		gemfireCache.setClose(true);
		gemfireCache.setLazyInitialize(false);
		gemfireCache.setProperties(gemfireProperties());
		gemfireCache.setUseBeanFactoryLocator(false);

		return gemfireCache;
	}
}

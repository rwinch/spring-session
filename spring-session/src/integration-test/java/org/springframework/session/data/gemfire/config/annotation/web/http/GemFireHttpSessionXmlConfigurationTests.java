/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.gemfire.config.annotation.web.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.AbstractGemfireIntegrationTests;
import org.springframework.session.data.gemfire.support.GemfireUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.Index;
import com.gemstone.gemfire.cache.query.QueryService;

/**
 * The GemfireHttpSessionXmlConfigurationTests class is a test suite of test cases testing the configuration of
 * Spring Session backed by GemFire using XML configuration meta-data.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.data.gemfire.AbstractGemfireIntegrationTests
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @since 1.1.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
public class GemfireHttpSessionXmlConfigurationTests extends AbstractGemfireIntegrationTests {

	@Autowired
	private Cache gemfireCache;

	protected <K, V> Region<K, V> assertCacheAndRegion(Cache gemfireCache, String regionName, DataPolicy dataPolicy) {
		assertThat(GemfireUtils.isPeer(gemfireCache)).isTrue();

		Region<K, V> region = gemfireCache.getRegion(regionName);

		assertRegion(region, regionName, dataPolicy);

		return region;
	}

	@Test
	public void gemfireCacheConfigurationIsValid() {
		Region<Object, ExpiringSession> example = assertCacheAndRegion(gemfireCache, "Example", DataPolicy.NORMAL);

		assertEntryIdleTimeout(example, ExpirationAction.INVALIDATE, 3600);
	}

	@Test
	public void verifyGemfireExampleCacheRegionPrincipalNameIndexWasCreatedSuccessfully() {
		Region<Object, ExpiringSession> example = assertCacheAndRegion(gemfireCache, "Example", DataPolicy.NORMAL);

		QueryService queryService = example.getRegionService().getQueryService();

		assertThat(queryService).isNotNull();

		Index principalNameIndex = queryService.getIndex(example, "principalNameIndex");

		assertIndex(principalNameIndex, "principalName", example.getFullPath());
	}

	@Test
	public void verifyGemfireExampleCacheRegionSessionAttributesIndexWasCreatedSuccessfully() {
		Region<Object, ExpiringSession> example = assertCacheAndRegion(gemfireCache, "Example", DataPolicy.NORMAL);

		QueryService queryService = example.getRegionService().getQueryService();

		assertThat(queryService).isNotNull();

		Index sessionAttributesIndex = queryService.getIndex(example, "sessionAttributesIndex");

		assertIndex(sessionAttributesIndex, "s.attributes['one', 'two', 'three']",
			String.format("%1$s s", example.getFullPath()));
	}

}

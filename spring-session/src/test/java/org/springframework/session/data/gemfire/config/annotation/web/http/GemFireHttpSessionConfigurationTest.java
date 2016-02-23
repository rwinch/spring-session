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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.session.data.gemfire.GemfireOperationsSessionRepository;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;

/**
 * The GemfireHttpSessionConfigurationTest class is a test suite of test cases testing the contract and functionality
 * of the {@link GemfireHttpSessionConfiguration} class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.session.data.gemfire.GemfireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemfireHttpSessionConfiguration
 * @see com.gemstone.gemfire.cache.Cache
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.client.ClientCache
 * @since 1.1.0
 */
public class GemfireHttpSessionConfigurationTest {

	private GemfireHttpSessionConfiguration gemfireConfiguration;

	protected <T> T[] toArray(T... array) {
		return array;
	}

	@Before
	public void setup() {
		gemfireConfiguration = new GemfireHttpSessionConfiguration();
	}

	@Test
	public void setAndGetBeanClassLoader() {
		assertThat(gemfireConfiguration.getBeanClassLoader()).isNull();

		gemfireConfiguration.setBeanClassLoader(Thread.currentThread().getContextClassLoader());

		assertThat(gemfireConfiguration.getBeanClassLoader()).isEqualTo(Thread.currentThread().getContextClassLoader());

		gemfireConfiguration.setBeanClassLoader(null);

		assertThat(gemfireConfiguration.getBeanClassLoader()).isNull();
	}

	@Test
	public void setAndGetClientRegionShortcut() {
		assertThat(gemfireConfiguration.getClientRegionShortcut()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT);

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);

		assertThat(gemfireConfiguration.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);

		gemfireConfiguration.setClientRegionShortcut(null);

		assertThat(gemfireConfiguration.getClientRegionShortcut()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	@Test
	public void setAndGetIndexableSessionAttributes() {
		assertThat(gemfireConfiguration.getIndexableSessionAttributes()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);

		gemfireConfiguration.setIndexableSessionAttributes(toArray("one", "two", "three"));

		assertThat(gemfireConfiguration.getIndexableSessionAttributes()).isEqualTo(toArray("one", "two", "three"));
		assertThat(gemfireConfiguration.getIndexableSessionAttributesAsGemfireIndexExpression())
			.isEqualTo("'one', 'two', 'three'");

		gemfireConfiguration.setIndexableSessionAttributes(toArray("one"));

		assertThat(gemfireConfiguration.getIndexableSessionAttributes()).isEqualTo(toArray("one"));
		assertThat(gemfireConfiguration.getIndexableSessionAttributesAsGemfireIndexExpression()).isEqualTo("'one'");

		gemfireConfiguration.setIndexableSessionAttributes(null);

		assertThat(gemfireConfiguration.getIndexableSessionAttributes()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);
		assertThat(gemfireConfiguration.getIndexableSessionAttributesAsGemfireIndexExpression()).isEqualTo("*");
	}

	@Test
	public void setAndGetMaxInactiveIntervalInSeconds() {
		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);

		gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);

		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(300);

		gemfireConfiguration.setMaxInactiveIntervalInSeconds(Integer.MAX_VALUE);

		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MAX_VALUE);

		gemfireConfiguration.setMaxInactiveIntervalInSeconds(-1);

		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(-1);

		gemfireConfiguration.setMaxInactiveIntervalInSeconds(Integer.MIN_VALUE);

		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MIN_VALUE);
	}

	@Test
	public void setAndGetServerRegionShortcut() {
		assertThat(gemfireConfiguration.getServerRegionShortcut()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT);

		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PERSISTENT);

		assertThat(gemfireConfiguration.getServerRegionShortcut()).isEqualTo(RegionShortcut.REPLICATE_PERSISTENT);

		gemfireConfiguration.setServerRegionShortcut(null);

		assertThat(gemfireConfiguration.getServerRegionShortcut()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT);
	}

	@Test
	public void setAndGetSpringSessionGemfireRegionName() {
		assertThat(gemfireConfiguration.getSpringSessionGemfireRegionName()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		gemfireConfiguration.setSpringSessionGemfireRegionName("test");

		assertThat(gemfireConfiguration.getSpringSessionGemfireRegionName()).isEqualTo("test");

		gemfireConfiguration.setSpringSessionGemfireRegionName("  ");

		assertThat(gemfireConfiguration.getSpringSessionGemfireRegionName()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		gemfireConfiguration.setSpringSessionGemfireRegionName("");

		assertThat(gemfireConfiguration.getSpringSessionGemfireRegionName()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		gemfireConfiguration.setSpringSessionGemfireRegionName(null);

		assertThat(gemfireConfiguration.getSpringSessionGemfireRegionName()).isEqualTo(
			GemfireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);
	}

	@Test
	public void setImportMetadata() {
		AnnotationMetadata mockAnnotationMetadata = mock(AnnotationMetadata.class, "testSetImportMetadata");

		Map<String, Object> annotationAttributes = new HashMap<String, Object>(4);

		annotationAttributes.put("clientRegionShortcut", ClientRegionShortcut.CACHING_PROXY);
		annotationAttributes.put("indexableSessionAttributes", toArray("one", "two", "three"));
		annotationAttributes.put("maxInactiveIntervalInSeconds", 600);
		annotationAttributes.put("serverRegionShortcut", RegionShortcut.REPLICATE);
		annotationAttributes.put("regionName", "TEST");

		when(mockAnnotationMetadata.getAnnotationAttributes(eq(EnableGemfireHttpSession.class.getName())))
			.thenReturn(annotationAttributes);

		gemfireConfiguration.setImportMetadata(mockAnnotationMetadata);

		assertThat(gemfireConfiguration.getClientRegionShortcut()).isEqualTo(ClientRegionShortcut.CACHING_PROXY);
		assertThat(gemfireConfiguration.getIndexableSessionAttributes()).isEqualTo(toArray("one", "two", "three"));
		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds()).isEqualTo(600);
		assertThat(gemfireConfiguration.getServerRegionShortcut()).isEqualTo(RegionShortcut.REPLICATE);
		assertThat(gemfireConfiguration.getSpringSessionGemfireRegionName()).isEqualTo("TEST");

		verify(mockAnnotationMetadata, times(1)).getAnnotationAttributes(eq(EnableGemfireHttpSession.class.getName()));
	}

	@Test
	public void createAndInitializeSpringSessionRepositoryBean() {
		GemfireOperations mockGemfireOperations = mock(GemfireOperations.class,
			"testCreateAndInitializeSpringSessionRepositoryBean");

		gemfireConfiguration.setMaxInactiveIntervalInSeconds(120);

		GemfireOperationsSessionRepository sessionRepository = gemfireConfiguration.sessionRepository(
			mockGemfireOperations);

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.getTemplate()).isSameAs(mockGemfireOperations);
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(120);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createAndInitializeSpringSessionGemfireRegionTemplate() {
		GemFireCache mockGemFireCache = mock(GemFireCache.class);
		Region<Object,Object> mockRegion = mock(Region.class);

		when(mockGemFireCache.getRegion(eq("Example"))).thenReturn(mockRegion);

		gemfireConfiguration.setSpringSessionGemfireRegionName("Example");

		GemfireTemplate template = gemfireConfiguration.sessionRegionTemplate(mockGemFireCache);

		assertThat(gemfireConfiguration.getSpringSessionGemfireRegionName()).isEqualTo("Example");
		assertThat(template).isNotNull();
		assertThat(template.getRegion()).isSameAs(mockRegion);

		verify(mockGemFireCache, times(1)).getRegion(eq("Example"));
	}

	@Test
	public void expirationIsAllowed() {
		Cache mockCache = mock(Cache.class, "testExpirationIsAllowed.MockCache");
		ClientCache mockClientCache = mock(ClientCache.class, "testExpirationIsAllowed.MockClientCache");

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockCache)).isTrue();

		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT_OVERFLOW);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockCache)).isTrue();

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockClientCache)).isTrue();

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PROXY);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockClientCache)).isTrue();
	}

	@Test
	public void expirationIsNotAllowed() {
		Cache mockCache = mock(Cache.class, "testExpirationIsAllowed.MockCache");
		ClientCache mockClientCache = mock(ClientCache.class, "testExpirationIsAllowed.MockClientCache");

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockClientCache)).isFalse();

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockCache)).isFalse();
	}

}

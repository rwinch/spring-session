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

package org.springframework.session.data.gemfire.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.Closeable;
import java.io.IOException;

import org.junit.Test;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;

/**
 * The GemfireUtilsTest class is a test suite of test cases testing the contract and functionality of the GemfireUtils
 * utility class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.support.GemfireUtils
 * @since 1.1.0
 */
public class GemfireUtilsTest {

	@Test
	public void closeNonNullCloseableSuccessfullyReturnsTrue() throws IOException {
		Closeable mockCloseable = mock(Closeable.class);
		assertThat(GemfireUtils.close(mockCloseable)).isTrue();
		verify(mockCloseable, times(1)).close();
	}

	@Test
	public void closeNonNullCloseableObjectThrowingIOExceptionReturnsFalse() throws IOException {
		Closeable mockCloseable = mock(Closeable.class);
		doThrow(new IOException("test")).when(mockCloseable).close();
		assertThat(GemfireUtils.close(mockCloseable)).isFalse();
		verify(mockCloseable, times(1)).close();
	}

	@Test
	public void closeNullCloseableObjectReturnsFalse() {
		assertThat(GemfireUtils.close(null)).isFalse();
	}

	@Test
	public void clientCacheIsClient() {
		assertThat(GemfireUtils.isClient(mock(ClientCache.class))).isTrue();
	}

	@Test
	public void genericCacheIsNotClient() {
		assertThat(GemfireUtils.isClient(mock(GemFireCache.class))).isFalse();
	}

	@Test
	public void peerCacheIsNotClient() {
		assertThat(GemfireUtils.isClient(mock(Cache.class))).isFalse();
	}

	@Test
	public void peerCacheIsPeer() {
		assertThat(GemfireUtils.isPeer(mock(Cache.class))).isTrue();
	}

	@Test
	public void genericCacheIsNotPeer() {
		assertThat(GemfireUtils.isPeer(mock(GemFireCache.class))).isFalse();
	}

	@Test
	public void clientCacheIsNotPeer() {
		assertThat(GemfireUtils.isPeer(mock(ClientCache.class))).isFalse();
	}

	@Test
	public void clientRegionShortcutIsLocal() {
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.LOCAL)).isTrue();
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.LOCAL_HEAP_LRU)).isTrue();
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.LOCAL_OVERFLOW)).isTrue();
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.LOCAL_PERSISTENT)).isTrue();
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW)).isTrue();
	}

	@Test
	public void clientRegionShortcutIsNotLocal() {
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY)).isFalse();
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY_HEAP_LRU)).isFalse();
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isLocal(ClientRegionShortcut.PROXY)).isFalse();
	}

	@Test
	public void clientRegionShortcutIsProxy() {
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.PROXY)).isTrue();
	}

	@Test
	public void clientRegionShortcutIsNotProxy() {
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY)).isFalse();
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY_HEAP_LRU)).isFalse();
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.LOCAL)).isFalse();
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.LOCAL_HEAP_LRU)).isFalse();
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.LOCAL_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.LOCAL_PERSISTENT)).isFalse();
		assertThat(GemfireUtils.isProxy(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW)).isFalse();
	}

	@Test
	public void regionShortcutIsProxy() {
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_PROXY)).isTrue();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_PROXY_REDUNDANT)).isTrue();
		assertThat(GemfireUtils.isProxy(RegionShortcut.REPLICATE_PROXY)).isTrue();
	}

	@Test
	public void regionShortcutIsNotProxy() {
		assertThat(GemfireUtils.isProxy(RegionShortcut.LOCAL)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.LOCAL_HEAP_LRU)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.LOCAL_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.LOCAL_PERSISTENT)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.LOCAL_PERSISTENT_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.REPLICATE)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.REPLICATE_HEAP_LRU)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.REPLICATE_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.REPLICATE_PERSISTENT)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.REPLICATE_PERSISTENT_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_HEAP_LRU)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_PERSISTENT)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_PERSISTENT_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_HEAP_LRU)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_OVERFLOW)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT)).isFalse();
		assertThat(GemfireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT_OVERFLOW)).isFalse();
	}

	@Test
	public void toRegionPath() {
		assertThat(GemfireUtils.toRegionPath("A")).isEqualTo("/A");
		assertThat(GemfireUtils.toRegionPath("Example")).isEqualTo("/Example");
		assertThat(GemfireUtils.toRegionPath("/Example")).isEqualTo("//Example");
		assertThat(GemfireUtils.toRegionPath("/")).isEqualTo("//");
		assertThat(GemfireUtils.toRegionPath("")).isEqualTo("/");
	}

}

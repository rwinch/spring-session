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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
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
 * The GemFireUtilsTest class is a test suite of test cases testing the contract and functionality of the GemFireUtils
 * utility class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.support.GemFireUtils
 * @since 1.0.0
 */
public class GemFireUtilsTest {

	@Test
	public void closeNonNullCloseableSuccessfullyReturnsTrue() throws IOException {
		Closeable mockCloseable = mock(Closeable.class);
		assertThat(GemFireUtils.close(mockCloseable), is(true));
		verify(mockCloseable, times(1)).close();
	}

	@Test
	public void closeNonNullCloseableObjectThrowingIOExceptionReturnsFalse() throws IOException {
		Closeable mockCloseable = mock(Closeable.class);
		doThrow(new IOException("test")).when(mockCloseable).close();
		assertThat(GemFireUtils.close(mockCloseable), is(false));
		verify(mockCloseable, times(1)).close();
	}

	@Test
	public void closeNullCloseableObjectReturnsFalse() {
		assertThat(GemFireUtils.close(null), is(false));
	}

	@Test
	public void clientCacheIsClient() {
		assertThat(GemFireUtils.isClient(mock(ClientCache.class)), is(true));
	}

	@Test
	public void genericCacheIsNotClient() {
		assertThat(GemFireUtils.isClient(mock(GemFireCache.class)), is(false));
	}

	@Test
	public void peerCacheIsNotClient() {
		assertThat(GemFireUtils.isClient(mock(Cache.class)), is(false));
	}

	@Test
	public void peerCacheIsPeer() {
		assertThat(GemFireUtils.isPeer(mock(Cache.class)), is(true));
	}

	@Test
	public void genericCacheIsNotPeer() {
		assertThat(GemFireUtils.isPeer(mock(GemFireCache.class)), is(false));
	}

	@Test
	public void clientCacheIsNotPeer() {
		assertThat(GemFireUtils.isPeer(mock(ClientCache.class)), is(false));
	}

	@Test
	public void clientRegionShortcutIsLocal() {
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL), is(true));
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL_HEAP_LRU), is(true));
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL_OVERFLOW), is(true));
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL_PERSISTENT), is(true));
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW), is(true));
	}

	@Test
	public void clientRegionShortcutIsNotLocal() {
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY), is(false));
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY_HEAP_LRU), is(false));
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.CACHING_PROXY_OVERFLOW), is(false));
		assertThat(GemFireUtils.isLocal(ClientRegionShortcut.PROXY), is(false));
	}

	@Test
	public void clientRegionShortcutIsProxy() {
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.PROXY), is(true));
	}

	@Test
	public void clientRegionShortcutIsNotProxy() {
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY), is(false));
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY_HEAP_LRU), is(false));
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.CACHING_PROXY_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL), is(false));
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL_HEAP_LRU), is(false));
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL_PERSISTENT), is(false));
		assertThat(GemFireUtils.isProxy(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW), is(false));
	}

	@Test
	public void regionShortcutIsProxy() {
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_PROXY), is(true));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_PROXY_REDUNDANT), is(true));
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_PROXY), is(true));
	}

	@Test
	public void regionShortcutIsNotProxy() {
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL_HEAP_LRU), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL_PERSISTENT), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.LOCAL_PERSISTENT_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_HEAP_LRU), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_PERSISTENT), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.REPLICATE_PERSISTENT_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_HEAP_LRU), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_PERSISTENT), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_PERSISTENT_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_HEAP_LRU), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_OVERFLOW), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT), is(false));
		assertThat(GemFireUtils.isProxy(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT_OVERFLOW), is(false));
	}

	@Test
	public void toRegionPath() {
		assertThat(GemFireUtils.toRegionPath("A"), is(equalTo("/A")));
		assertThat(GemFireUtils.toRegionPath("Example"), is(equalTo("/Example")));
		assertThat(GemFireUtils.toRegionPath("/Example"), is(equalTo("//Example")));
		assertThat(GemFireUtils.toRegionPath("/"), is(equalTo("//")));
		assertThat(GemFireUtils.toRegionPath(""), is(equalTo("/")));
	}

}

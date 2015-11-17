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

package org.springframework.session.data.gemfire;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.client.ClientCacheFactoryBean;
import org.springframework.data.gemfire.client.PoolFactoryBean;
import org.springframework.data.gemfire.config.GemfireConstants;
import org.springframework.data.gemfire.server.CacheServerFactoryBean;
import org.springframework.data.gemfire.support.ConnectionEndpoint;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.FileSystemUtils;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.Pool;

/**
 * The ClientServerGemFireOperationsSessionRepositoryIntegrationTests class is a test suite of test cases testing
 * the functionality of GemFire-backed Spring Sessions using a GemFire client-server topology.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @see com.gemstone.gemfire.cache.Cache
 * @see com.gemstone.gemfire.cache.client.ClientCache
 * @see com.gemstone.gemfire.cache.client.Pool
 * @see com.gemstone.gemfire.cache.server.CacheServer
 * @since 1.0.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes =
	ClientServerGemFireOperationsSessionRepositoryIntegrationTests.SpringSessionGemFireClientConfiguration.class)
@WebAppConfiguration
@SuppressWarnings("unused")
public class ClientServerGemFireOperationsSessionRepositoryIntegrationTests extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	private static final DateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

	private static File processWorkingDirectory;

	private static Process gemfireServer;

	@Autowired
	private SessionEventListener sessionEventListener;

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		final long t0 = System.currentTimeMillis();

		String processWorkingDirectoryPathname = String.format("client-server-tests-%1$s",
			TIMESTAMP.format(new Date()));

		processWorkingDirectory = createDirectory(processWorkingDirectoryPathname);
		gemfireServer = run(SpringSessionGemFireServerConfiguration.class, processWorkingDirectory);

		assertThat(waitForCacheServerToStart(SpringSessionGemFireServerConfiguration.SERVER_HOSTNAME,
			SpringSessionGemFireServerConfiguration.SERVER_PORT), is(true));
		//assertThat(waitForProcessToStart(gemfireServer, processWorkingDirectory), is(true));

		System.err.printf("GemFire Server [startup time = %1$d ms]%n", System.currentTimeMillis() - t0);
	}

	@AfterClass
	public static void stopGemFireServerAndDeleteArtifacts() {
		if (gemfireServer != null) {
			gemfireServer.destroyForcibly();
			System.err.printf("GemFire Server [exit code = %1$d]%n",
				waitForProcessToStop(gemfireServer, processWorkingDirectory));
		}

		if (Boolean.valueOf(System.getProperty("spring.session.data.gemfire.fork.clean", Boolean.TRUE.toString()))) {
			FileSystemUtils.deleteRecursively(processWorkingDirectory);
		}

		assertThat(waitForClientCacheToClose(DEFAULT_WAIT_DURATION), is(true));
	}

	@Before
	public void setup() {
		assertThat(GemFireUtils.isClient(gemfireCache), is(true));

		Region<Object, ExpiringSession> springSessionGemFireRegion = gemfireCache.getRegion(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertThat(springSessionGemFireRegion, is(notNullValue()));

		RegionAttributes<Object, ExpiringSession> springSessionGemFireRegionAttributes =
			springSessionGemFireRegion.getAttributes();

		assertThat(springSessionGemFireRegionAttributes, is(notNullValue()));
		assertThat(springSessionGemFireRegionAttributes.getDataPolicy(), is(equalTo(DataPolicy.EMPTY)));
	}

	@After
	public void tearDown() {
		sessionEventListener.getSessionEvent();
	}

	@Test
	public void createSessionFiresSessionCreatedEvent() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		ExpiringSession expectedSession = save(createSession());

		AbstractSessionEvent sessionEvent = sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent, is(instanceOf(SessionCreatedEvent.class)));

		ExpiringSession createdSession = sessionEvent.getSession();

		assertThat(createdSession, is(equalTo(expectedSession)));
		assertThat(createdSession.getId(), is(notNullValue()));
		assertThat(createdSession.getCreationTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(createdSession.getLastAccessedTime(), is(equalTo(createdSession.getCreationTime())));
		assertThat(createdSession.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

		gemfireSessionRepository.delete(expectedSession.getId());
	}

	@Test
	public void getExistingNonExpiredSessionBeforeAndAfterExpiration() {
		ExpiringSession expectedSession = save(touch(createSession()));

		AbstractSessionEvent sessionEvent = sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent, is(instanceOf(SessionCreatedEvent.class)));
		assertThat(sessionEvent.<ExpiringSession>getSession(), is(equalTo(expectedSession)));
		assertThat(sessionEventListener.getSessionEvent(), is(nullValue()));

		ExpiringSession savedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(savedSession, is(equalTo(expectedSession)));

		// NOTE for some reason or another, performing a GemFire (Client)Cache Region.get(key)
		// causes a Region CREATE event... o.O
		// calling sessionEventListener.getSessionEvent() here to clear the event
		sessionEventListener.getSessionEvent();

		sessionEvent = sessionEventListener.waitForSessionEvent(TimeUnit.SECONDS.toMillis(
			MAX_INACTIVE_INTERVAL_IN_SECONDS + 1));

		assertThat(sessionEvent, is(instanceOf(SessionExpiredEvent.class)));
		assertThat(sessionEvent.<ExpiringSession>getSessionId(), is(equalTo(expectedSession.getId())));

		ExpiringSession expiredSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(expiredSession, is(nullValue()));
	}

	@Test
	public void deleteExistingNonExpiredSessionFiresSessionDeletedEventAndReturnsNullOnGet() {
		ExpiringSession expectedSession = save(touch(createSession()));

		AbstractSessionEvent sessionEvent = sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent, is(instanceOf(SessionCreatedEvent.class)));
		assertThat(sessionEvent.<ExpiringSession>getSession(), is(equalTo(expectedSession)));

		gemfireSessionRepository.delete(expectedSession.getId());

		sessionEvent = sessionEventListener.waitForSessionEvent(500);

		assertThat(sessionEvent, is(instanceOf(SessionDeletedEvent.class)));
		assertThat(sessionEvent.<ExpiringSession>getSessionId(), is(equalTo(expectedSession.getId())));

		ExpiringSession deletedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(deletedSession, is(nullValue()));
	}

	@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class SpringSessionGemFireClientConfiguration {

		//TODO remove when SGF-458 is closed/released
		static {
			System.setProperty("gemfire.log-level", GEMFIRE_LOG_LEVEL);
		}

		@Bean
		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();
			//TODO uncomment when SGF-458 is closed/released.
			//gemfireProperties.setProperty("name", ClientServerGemFireOperationsSessionRepositoryIntegrationTests.class.getName());
			//gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);
			return gemfireProperties;
		}

		@Bean(name = GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME)
		PoolFactoryBean gemfirePool() {
			PoolFactoryBean poolFactory = new PoolFactoryBean();

			poolFactory.setName(GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME);
			poolFactory.setFreeConnectionTimeout(5000); // 5 seconds
			poolFactory.setKeepAlive(false);
			poolFactory.setMaxConnections(SpringSessionGemFireServerConfiguration.MAX_CONNECTIONS);
			poolFactory.setPingInterval(TimeUnit.SECONDS.toMillis(5));
			poolFactory.setReadTimeout(2000); // 2 seconds
			poolFactory.setRetryAttempts(2);
			poolFactory.setSubscriptionEnabled(true);
			poolFactory.setThreadLocalConnections(false);

			poolFactory.setServerEndpoints(Collections.singletonList(new ConnectionEndpoint(
				SpringSessionGemFireServerConfiguration.SERVER_HOSTNAME,
					SpringSessionGemFireServerConfiguration.SERVER_PORT)));

			return poolFactory;
		}

		@Bean
		ClientCacheFactoryBean gemfireCache(Pool gemfirePool) {
//			TODO replace the following declaration with the declaration below overriding the resolveProperties() method when SGF-458 is closed/released
			ClientCacheFactoryBean clientCacheFactory = new ClientCacheFactoryBean();

/*
			ClientCacheFactoryBean clientCacheFactory = new ClientCacheFactoryBean() {
				protected Properties resolveProperties() {
					return gemfireProperties();
				}
			};
*/

			clientCacheFactory.setClose(true);
			clientCacheFactory.setPool(gemfirePool);
			clientCacheFactory.setProperties(gemfireProperties());
			clientCacheFactory.setUseBeanFactoryLocator(false);

			return clientCacheFactory;
		}

		@Bean
		public SessionEventListener sessionEventListener() {
			return new SessionEventListener();
		}

		// used for debugging purposes
		public static void main(final String[] args) {
			ConfigurableApplicationContext applicationContext = new AnnotationConfigApplicationContext(
				SpringSessionGemFireClientConfiguration.class);

			applicationContext.registerShutdownHook();

			ClientCache clientCache = applicationContext.getBean(ClientCache.class);

			for (InetSocketAddress server : clientCache.getCurrentServers()) {
				System.err.printf("GemFire Server [host: %1$s, port: %2$d]%n",
					server.getHostName(), server.getPort());
			}
		}
	}

	@EnableGemFireHttpSession(maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class SpringSessionGemFireServerConfiguration {

		static final int MAX_CONNECTIONS = 50;
		static final int SERVER_PORT = 42124;

		static final String SERVER_HOSTNAME = "localhost";

		@Bean
		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();

			gemfireProperties.setProperty("name", SpringSessionGemFireServerConfiguration.class.getName());
			gemfireProperties.setProperty("mcast-port", "0");
			gemfireProperties.setProperty("log-file", "server.log");
			gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);

			return gemfireProperties;
		}

		@Bean
		CacheFactoryBean gemfireCache() {
			CacheFactoryBean cacheFactory = new CacheFactoryBean();

			cacheFactory.setProperties(gemfireProperties());
			cacheFactory.setUseBeanFactoryLocator(false);

			return cacheFactory;
		}

		@Bean
		CacheServerFactoryBean gemfireCacheServer(Cache gemfireCache) {
			CacheServerFactoryBean cacheServerFactory = new CacheServerFactoryBean();

			cacheServerFactory.setAutoStartup(true);
			cacheServerFactory.setBindAddress(SERVER_HOSTNAME);
			cacheServerFactory.setCache(gemfireCache);
			cacheServerFactory.setMaxConnections(MAX_CONNECTIONS);
			cacheServerFactory.setPort(SERVER_PORT);

			return cacheServerFactory;
		}

		public static void main(final String[] args) throws IOException {
			new AnnotationConfigApplicationContext(SpringSessionGemFireServerConfiguration.class).registerShutdownHook();
			writeProcessControlFile(WORKING_DIRECTORY);
		}
	}

}

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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;

/**
 * The EnableGemFireHttpSessionEventsIntegrationTests class is a test suite of test cases testing the Session Event
 * functionality and behavior of the GemFireOperationsSessionRepository and GemFire's configuration.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.session.events.SessionCreatedEvent
 * @see org.springframework.session.events.SessionDeletedEvent
 * @see org.springframework.session.events.SessionExpiredEvent
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @see com.gemstone.gemfire.cache.Region
 * @since 1.1.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
@SuppressWarnings("unused")
public class EnableGemFireHttpSessionEventsIntegrationTests extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	private static final String GEMFIRE_LOG_LEVEL = "warning";
	private static final String SPRING_SESSION_GEMFIRE_REGION_NAME = "TestReplicatedSessions";

	@Autowired
	private SessionEventListener sessionEventListener;

	@Before
	public void setup() {
		assertThat(GemFireUtils.isPeer(gemfireCache), is(true));
		assertThat(gemfireSessionRepository, is(notNullValue()));
		assertThat(gemfireSessionRepository.getMaxInactiveIntervalInSeconds(),
			is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(sessionEventListener, is(notNullValue()));

		Region<Object, ExpiringSession> sessionRegion = gemfireCache.getRegion(SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertRegion(sessionRegion, SPRING_SESSION_GEMFIRE_REGION_NAME, DataPolicy.REPLICATE);
		assertEntryIdleTimeout(sessionRegion, ExpirationAction.INVALIDATE, MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	@After
	public void tearDown() {
		sessionEventListener.getSessionEvent();
	}

	@Test
	public void sessionCreatedEvent() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		ExpiringSession expectedSession = save(createSession());

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionCreatedEvent.class)));

		ExpiringSession createdSession = sessionEvent.getSession();

		assertThat(createdSession, is(equalTo(expectedSession)));
		assertThat(createdSession.getId(), is(notNullValue()));
		assertThat(createdSession.getCreationTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(createdSession.getLastAccessedTime(), is(equalTo(createdSession.getCreationTime())));
		assertThat(createdSession.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(createdSession.isExpired(), is(false));
	}

	@Test
	public void getExistingNonExpiredSession() {
		ExpiringSession expectedSession = save(touch(createSession()));

		assertThat(expectedSession.isExpired(), is(false));

		// NOTE though unlikely, a possible race condition exists between save and get...
		ExpiringSession savedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(savedSession, is(equalTo(expectedSession)));
	}

	@Test
	public void getExistingExpiredSession() {
		ExpiringSession expectedSession = save(expire(createSession()));

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionCreatedEvent.class)));

		ExpiringSession createdSession = sessionEvent.getSession();

		assertThat(createdSession, is(equalTo(expectedSession)));
		assertThat(createdSession.isExpired(), is(true));
		assertThat(gemfireSessionRepository.getSession(createdSession.getId()), is(nullValue()));
	}

	@Test
	public void getNonExistingSession() {
		assertThat(gemfireSessionRepository.getSession(UUID.randomUUID().toString()), is(nullValue()));
	}

	@Test
	public void deleteExistingNonExpiredSession() {
		ExpiringSession expectedSession = save(touch(createSession()));
		ExpiringSession savedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(savedSession, is(equalTo(expectedSession)));
		assertThat(savedSession.isExpired(), is(false));

		gemfireSessionRepository.delete(savedSession.getId());

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionDeletedEvent.class)));
		assertThat(sessionEvent.getSessionId(), is(equalTo(savedSession.getId())));

		ExpiringSession deletedSession = sessionEvent.getSession();

		assertThat(deletedSession, is(equalTo(savedSession)));
		assertThat(gemfireSessionRepository.getSession(deletedSession.getId()), is(nullValue()));
	}

	@Test
	public void deleteExistingExpiredSession() {
		ExpiringSession expectedSession = save(createSession());

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionCreatedEvent.class)));

		ExpiringSession createdSession = sessionEvent.getSession();

		assertThat(createdSession, is(equalTo(expectedSession)));

		sessionEvent = sessionEventListener.waitForSessionEvent(TimeUnit.SECONDS.toMillis(
			gemfireSessionRepository.getMaxInactiveIntervalInSeconds() + 1));

		assertThat(sessionEvent, is(instanceOf(SessionExpiredEvent.class)));

		ExpiringSession expiredSession = sessionEvent.getSession();

		assertThat(expiredSession, is(equalTo(createdSession)));
		assertThat(expiredSession.isExpired(), is(true));

		gemfireSessionRepository.delete(expectedSession.getId());

		sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionDeletedEvent.class)));
		assertThat(sessionEvent.getSession(), is(nullValue()));
		assertThat(sessionEvent.getSessionId(), is(equalTo(expiredSession.getId())));
		assertThat(gemfireSessionRepository.getSession(sessionEvent.getSessionId()), is(nullValue()));
	}

	@Test
	public void deleteNonExistingSession() {
		String expectedSessionId = UUID.randomUUID().toString();

		assertThat(gemfireSessionRepository.getSession(expectedSessionId), is(nullValue()));

		gemfireSessionRepository.delete(expectedSessionId);

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionDeletedEvent.class)));
		assertThat(sessionEvent.getSession(), is(nullValue()));
		assertThat(sessionEvent.getSessionId(), is(equalTo(expectedSessionId)));
	}

	@EnableGemFireHttpSession(regionName = SPRING_SESSION_GEMFIRE_REGION_NAME,
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS,
			serverRegionShortcut = RegionShortcut.REPLICATE)
	static class SpringSessionGemFireConfiguration {

		@Bean
		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();

			gemfireProperties.setProperty("name", EnableGemFireHttpSessionEventsIntegrationTests.class.getName());
			gemfireProperties.setProperty("mcast-port", "0");
			gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);

			return gemfireProperties;
		}

		@Bean
		CacheFactoryBean gemfireCache() {
			CacheFactoryBean gemfireCache = new CacheFactoryBean();

			gemfireCache.setLazyInitialize(false);
			gemfireCache.setProperties(gemfireProperties());
			gemfireCache.setUseBeanFactoryLocator(false);

			return gemfireCache;
		}

		@Bean
		SessionEventListener sessionEventListener() {
			return new SessionEventListener();
		}
	}

}

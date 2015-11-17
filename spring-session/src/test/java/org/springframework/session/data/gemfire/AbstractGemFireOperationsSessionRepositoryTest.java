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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;

import com.gemstone.gemfire.cache.AttributesMutator;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.Region;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

/**
 * The AbstractGemFireOperationsSessionRepositoryTest class is a test suite of test cases testing the contract
 * and functionality of the AbstractGemFireOperationsSessionRepository class.
 *
 * @author John Blum
 * @see org.junit.Rule
 * @see org.junit.Test
 * @see org.junit.rules.ExpectedException
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.runners.MockitoJUnitRunner
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @see edu.umd.cs.mtc.MultithreadedTestCase
 * @see edu.umd.cs.mtc.TestFramework
 * @since 1.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractGemFireOperationsSessionRepositoryTest {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Mock
	private GemfireOperations mockGemfireOperations;

	@Mock
	private Log mockLog;

	private AbstractGemFireOperationsSessionRepository sessionRepository;

	@Before
	public void setup() {
		sessionRepository = new TestGemFireOperationsSessionRepository(mockGemfireOperations) {
			@Override Log newLogger() {
				return mockLog;
			}
		};
	}

	protected static <E> Set<E> asSet(E... elements) {
		Set<E> set = new HashSet<E>(elements.length);
		Collections.addAll(set, elements);
		return set;
	}

	protected ExpiringSession mockSession(String sessionId, long creationAndLastAccessedTime,
		int maxInactiveIntervalInSeconds) {

		return mockSession(sessionId, creationAndLastAccessedTime, creationAndLastAccessedTime,
			maxInactiveIntervalInSeconds);
	}

	protected ExpiringSession mockSession(String sessionId, long creationTime, long lastAccessedTime,
		int maxInactiveIntervalInSeconds) {

		ExpiringSession mockSession = mock(ExpiringSession.class, sessionId);

		when(mockSession.getId()).thenReturn(sessionId);
		when(mockSession.getCreationTime()).thenReturn(creationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(lastAccessedTime);
		when(mockSession.getMaxInactiveIntervalInSeconds()).thenReturn(maxInactiveIntervalInSeconds);

		return mockSession;
	}

	@Test
	public void constructGemFireOperationsSessionRepositoryWithNullTemplate() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectCause(is(nullValue(Throwable.class)));
		expectedException.expectMessage("GemfireOperations must not be null");

		new TestGemFireOperationsSessionRepository(null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void gemfireOperationsSessionRepositoryIsProperlyConstructedAndInitialized() throws Exception {
		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);
		AttributesMutator mockAttributesMutator = mock(AttributesMutator.class);
		Region mockRegion = mock(Region.class);

		when(mockRegion.getFullPath()).thenReturn("/Example");
		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);

		GemfireTemplate template = new GemfireTemplate(mockRegion);

		AbstractGemFireOperationsSessionRepository sessionRepository =
			new TestGemFireOperationsSessionRepository(template);

		ApplicationEventPublisher applicationEventPublisher = sessionRepository.getApplicationEventPublisher();

		assertThat(applicationEventPublisher, is(notNullValue()));
		assertThat(sessionRepository.getFullyQualifiedRegionName(), is(nullValue()));
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(sessionRepository.getTemplate(), is(sameInstance((GemfireOperations) template)));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.setMaxInactiveIntervalInSeconds(300);
		sessionRepository.afterPropertiesSet();

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));
		assertThat(sessionRepository.getFullyQualifiedRegionName(), is(equalTo("/Example")));
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(300)));
		assertThat(sessionRepository.getTemplate(), is(sameInstance((GemfireOperations) template)));

		verify(mockRegion, times(1)).getAttributesMutator();
		verify(mockRegion, times(1)).getFullPath();
		verify(mockAttributesMutator, times(1)).addCacheListener(same(sessionRepository));
	}

	@Test
	public void maxInactiveIntervalInSecondsAllowsNegativeValuesAndExtremelyLargeValues() {
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS)));

		sessionRepository.setMaxInactiveIntervalInSeconds(-1);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(-1)));

		sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MIN_VALUE);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(Integer.MIN_VALUE)));

		sessionRepository.setMaxInactiveIntervalInSeconds(3600);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(3600)));

		sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MAX_VALUE);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(Integer.MAX_VALUE)));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateWithSessionPublishesSessionCreatedEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionCreatedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(mockSession);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterCreate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionCreatedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateWithSessionIdPublishesSessionCreatedEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionCreatedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterCreate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionCreatedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithSessionPublishesSessionDestroyedEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDestroyedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(mockSession);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterDestroy(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithSessionIdPublishesSessionDestroyedEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDestroyedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterDestroy(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithSessionPublishesSessionExpiredEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionExpiredEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(mockSession);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterInvalidate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithSessionIdPublishesSessionExpiredEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionExpiredEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterInvalidate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	public void handleDeletedWithSessionPublishesSessionDeletedEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.handleDeleted(sessionId, mockSession);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void handleDeletedWithSessionIdPublishesSessionDeletedEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.handleDeleted(sessionId, null);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void publishEventHandlesThrowable() {
		ApplicationEvent mockApplicationEvent = mock(ApplicationEvent.class);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doThrow(new IllegalStateException("test")).when(mockApplicationEventPublisher)
			.publishEvent(any(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.publishEvent(mockApplicationEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockApplicationEventPublisher, times(1)).publishEvent(eq(mockApplicationEvent));
		verify(mockLog, times(1)).error(eq(String.format("error occurred publishing event (%1$s)", mockApplicationEvent)),
			isA(IllegalStateException.class));
	}

	@Test
	public void constructGemFireSessionWithDefaultInitialization() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		GemFireSession session = new GemFireSession();

		assertThat(session.getId(), is(notNullValue()));
		assertThat(session.getCreationTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(session.getLastAccessedTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(0)));
		assertThat(session.getAttributeNames(), is(notNullValue()));
		assertThat(session.getAttributeNames().isEmpty(), is(true));
	}

	@Test
	public void constructGemFireSessionWithId() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId(), is(equalTo("1")));
		assertThat(session.getCreationTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(session.getLastAccessedTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(0)));
		assertThat(session.getAttributeNames(), is(notNullValue()));
		assertThat(session.getAttributeNames().isEmpty(), is(true));
	}

	@Test
	public void constructGemFireSessionWithSession() {
		final long expectedCreationTime = 1l;
		final long expectedLastAccessTime = 2l;

		ExpiringSession mockSession = mockSession("2", expectedCreationTime, expectedLastAccessTime,
			MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Set<String> expectedAttributedNames = asSet("attrOne", "attrTwo");

		when(mockSession.getAttributeNames()).thenReturn(expectedAttributedNames);
		when(mockSession.getAttribute(eq("attrOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attrTwo"))).thenReturn("testTwo");

		GemFireSession gemfireSession = new GemFireSession(mockSession);

		assertThat(gemfireSession.getId(), is(equalTo("2")));
		assertThat(gemfireSession.getCreationTime(), is(equalTo(expectedCreationTime)));
		assertThat(gemfireSession.getLastAccessedTime(), is(equalTo(expectedLastAccessTime)));
		assertThat(gemfireSession.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(gemfireSession.getAttributeNames(), is(equalTo(expectedAttributedNames)));
		assertThat(String.valueOf(gemfireSession.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(gemfireSession.getAttribute("attrTwo")), is(equalTo("testTwo")));

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
		verify(mockSession, times(1)).getAttribute(eq("attrTwo"));
	}

	@Test
	public void constructGemFireSessionWithNullSession() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectCause(is(nullValue(Throwable.class)));
		expectedException.expectMessage("The ExpiringSession to copy cannot be null");

		new GemFireSession((ExpiringSession) null);
	}

	@Test
	public void constructGemFireSessionWithUnspecifiedId() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectCause(is(nullValue(Throwable.class)));
		expectedException.expectMessage("ID must be specified");

		new GemFireSession(" ");
	}

	@Test
	public void createNewGemFireSession() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		GemFireSession session = GemFireSession.create(120);

		assertThat(session, is(notNullValue()));
		assertThat(session.getId(), is(notNullValue()));
		assertThat(session.getCreationTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(session.getLastAccessedTime(), is(equalTo(session.getCreationTime())));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(120)));
		assertThat(session.getAttributeNames(), is(notNullValue()));
		assertThat(session.getAttributeNames().isEmpty(), is(true));
	}

	@Test
	public void fromExistingSession() {
		final long expectedCreationTime = 1l;
		final long expectedLastAccessedTime = 2l;

		ExpiringSession mockSession = mockSession("4", expectedCreationTime, expectedLastAccessedTime,
			MAX_INACTIVE_INTERVAL_IN_SECONDS);

		when(mockSession.getAttributeNames()).thenReturn(Collections.<String>emptySet());

		GemFireSession gemfireSession = GemFireSession.from(mockSession);

		assertThat(gemfireSession, is(notNullValue()));
		assertThat(gemfireSession.getId(), is(equalTo("4")));
		assertThat(gemfireSession.getCreationTime(), is(equalTo(expectedCreationTime)));
		assertThat(gemfireSession.getLastAccessedTime(), is(not(equalTo(expectedLastAccessedTime))));
		assertThat(gemfireSession.getLastAccessedTime(), is(greaterThanOrEqualTo(expectedCreationTime)));
		assertThat(gemfireSession.getLastAccessedTime(), is(lessThanOrEqualTo(System.currentTimeMillis())));
		assertThat(gemfireSession.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(gemfireSession.getAttributeNames(), is(notNullValue()));
		assertThat(gemfireSession.getAttributeNames().isEmpty(), is(true));

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, never()).getAttribute(anyString());
	}

	@Test
	public void setGetAndRemoveAttribute() {
		GemFireSession session = GemFireSession.create(60);

		assertThat(session, is(notNullValue()));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(60)));
		assertThat(session.getAttributeNames().isEmpty(), is(true));

		session.setAttribute("attrOne", "testOne");

		assertThat(session.getAttributeNames(), is(equalTo(asSet("attrOne"))));
		assertThat(String.valueOf(session.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(session.getAttribute("attrTwo"), is(nullValue()));

		session.setAttribute("attrTwo", "testTwo");

		assertThat(session.getAttributeNames(), is(equalTo(asSet("attrOne", "attrTwo"))));
		assertThat(String.valueOf(session.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(session.getAttribute("attrTwo")), is(equalTo("testTwo")));

		session.setAttribute("attrTwo", null);

		assertThat(session.getAttributeNames(), is(equalTo(asSet("attrOne"))));
		assertThat(String.valueOf(session.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(session.getAttribute("attrTwo"), is(nullValue()));

		session.removeAttribute("attrOne");

		assertThat(session.getAttribute("attrOne"), is(nullValue()));
		assertThat(session.getAttribute("attrTwo"), is(nullValue()));
		assertThat(session.getAttributeNames().isEmpty(), is(true));
	}

	@Test
	public void isExpiredIsFalseWhenMaxInactiveIntervalIsNegative() {
		final int expectedMaxInactiveIntervalInSeconds = -1;

		GemFireSession session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session, is(notNullValue()));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(expectedMaxInactiveIntervalInSeconds)));
		assertThat(session.isExpired(), is(false));
	}

	@Test
	public void isExpiredIsFalseWhenSessionIsActive() {
		final int expectedMaxInactiveIntervalInSeconds = (int) TimeUnit.HOURS.toSeconds(2);

		GemFireSession session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session, is(notNullValue()));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(expectedMaxInactiveIntervalInSeconds)));

		final long now = System.currentTimeMillis();

		session.setLastAccessedTime(now);

		assertThat(session.getLastAccessedTime(), is(equalTo(now)));
		assertThat(session.isExpired(), is(false));
	}

	@Test
	public void isExpiredIsTrueWhenSessionIsInactive() {
		final int expectedMaxInactiveIntervalInSeconds = 60;

		GemFireSession session = GemFireSession.create(expectedMaxInactiveIntervalInSeconds);

		assertThat(session, is(notNullValue()));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(expectedMaxInactiveIntervalInSeconds)));

		final long twoHoursAgo = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));

		session.setLastAccessedTime(twoHoursAgo);

		assertThat(session.getLastAccessedTime(), is(equalTo(twoHoursAgo)));
		assertThat(session.isExpired(), is(true));
	}

	@Test
	public void setAndGetPrincipalName() {
		GemFireSession session = GemFireSession.create(0);

		assertThat(session, is(notNullValue()));
		assertThat(session.getPrincipalName(), is(nullValue()));

		session.setPrincipalName("jblum");

		assertThat(session.getPrincipalName(), is(equalTo("jblum")));
		assertThat(session.getAttributeNames(), is(equalTo(asSet(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME))));
		assertThat(String.valueOf(session.getAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME)), is(equalTo("jblum")));

		session.setAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME, "rwinch");

		assertThat(session.getAttributeNames(), is(equalTo(asSet(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME))));
		assertThat(String.valueOf(session.getAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME)), is(equalTo("rwinch")));
		assertThat(session.getPrincipalName(), is(equalTo("rwinch")));

		session.removeAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME);

		assertThat(session.getPrincipalName(), is(nullValue()));
	}

	@Test
	public void sessionToData() throws Exception {
		GemFireSession session = new GemFireSession("1") {
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(obj, is(instanceOf(GemFireSessionAttributes.class)));
				assertThat(out, is(notNullValue()));
			}
		};

		session.setLastAccessedTime(123l);
		session.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		session.setPrincipalName("jblum");

		DataOutput mockDataOutput = mock(DataOutput.class);

		session.toData(mockDataOutput);

		verify(mockDataOutput, times(1)).writeUTF(eq("1"));
		verify(mockDataOutput, times(1)).writeLong(eq(session.getCreationTime()));
		verify(mockDataOutput, times(1)).writeLong(eq(session.getLastAccessedTime()));
		verify(mockDataOutput, times(1)).writeInt(eq(session.getMaxInactiveIntervalInSeconds()));
		verify(mockDataOutput, times(1)).writeInt(eq("jblum".length()));
		verify(mockDataOutput, times(1)).writeUTF(eq(session.getPrincipalName()));
	}

	@Test
	public void sessionFromData() throws Exception {
		final long expectedCreationTime = 1l;
		final long expectedLastAccessedTime = 2l;

		final int expectedMaxInactiveIntervalInSeconds = (int) TimeUnit.HOURS.toSeconds(6);

		final String expectedPrincipalName = "jblum";

		DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readUTF()).thenReturn("2").thenReturn(expectedPrincipalName);
		when(mockDataInput.readLong()).thenReturn(expectedCreationTime).thenReturn(expectedLastAccessedTime);
		when(mockDataInput.readInt()).thenReturn(expectedMaxInactiveIntervalInSeconds);

		GemFireSession session = new GemFireSession("1") {
			@Override @SuppressWarnings("unchecked")
			<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				assertThat(in, is(notNullValue()));

				GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

				sessionAttributes.setAttribute("attrOne", "testOne");
				sessionAttributes.setAttribute("attrTwo", "testTwo");

				return (T) sessionAttributes;
			}
		};

		session.fromData(mockDataInput);

		Set<String> expectedAttributeNames = asSet("attrOne", "attrTwo", Session.PRINCIPAL_NAME_ATTRIBUTE_NAME);

		assertThat(session.getId(), is(equalTo("2")));
		assertThat(session.getCreationTime(), is(equalTo(expectedCreationTime)));
		assertThat(session.getLastAccessedTime(), is(equalTo(expectedLastAccessedTime)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(expectedMaxInactiveIntervalInSeconds)));
		assertThat(session.getPrincipalName(), is(equalTo(expectedPrincipalName)));
		assertThat(session.getAttributeNames().size(), is(equalTo(3)));
		assertThat(session.getAttributeNames().containsAll(expectedAttributeNames), is(true));
		assertThat(String.valueOf(session.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(session.getAttribute("attrTwo")), is(equalTo("testTwo")));
		assertThat(String.valueOf(session.getAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME)),
			is(equalTo(expectedPrincipalName)));

		verify(mockDataInput, times(2)).readUTF();
		verify(mockDataInput, times(2)).readLong();
		verify(mockDataInput, times(2)).readInt();
	}

	@Test
	public void sessionToDataThenFromDataWhenPrincipalNameIsNullGetsHandledProperly()
			throws ClassNotFoundException, IOException {

		final long beforeOrAtCreationTime = System.currentTimeMillis();

		GemFireSession expectedSession = new GemFireSession("123") {
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(obj, is(instanceOf(GemFireSessionAttributes.class)));
				assertThat(out, is(notNullValue()));
			}
		};

		assertThat(expectedSession.getId(), is(equalTo("123")));
		assertThat(expectedSession.getCreationTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(expectedSession.getLastAccessedTime(), is(greaterThanOrEqualTo(expectedSession.getCreationTime())));
		assertThat(expectedSession.getMaxInactiveIntervalInSeconds(), is(equalTo(0)));
		assertThat(expectedSession.getPrincipalName(), is(nullValue()));

		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

		expectedSession.toData(new DataOutputStream(outBytes));

		GemFireSession deserializedSession = new GemFireSession("0") {
			@SuppressWarnings("unchecked")
			@Override <T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				return (T) new GemFireSessionAttributes();
			}
		};

		deserializedSession.fromData(new DataInputStream(new ByteArrayInputStream(outBytes.toByteArray())));

		assertThat(deserializedSession, is(equalTo(expectedSession)));
		assertThat(deserializedSession.getCreationTime(), is(equalTo(expectedSession.getCreationTime())));
		assertThat(deserializedSession.getLastAccessedTime(), is(equalTo(expectedSession.getLastAccessedTime())));
		assertThat(deserializedSession.getMaxInactiveIntervalInSeconds(),
			is(equalTo(expectedSession.getMaxInactiveIntervalInSeconds())));
		assertThat(deserializedSession.getPrincipalName(), is(nullValue()));
	}

	@Test
	public void hasDeltaWhenNoSessionChangesIsFalse() {
		assertThat(new GemFireSession().hasDelta(), is(false));
	}

	@Test
	public void hasDeltaWhenSessionAttributesChangeIsTrue() {
		GemFireSession session = new GemFireSession();

		assertThat(session.hasDelta(), is(false));

		session.setAttribute("attrOne", "test");

		assertThat(session.hasDelta(), is(true));
	}

	@Test
	public void hasDeltaWhenSessionLastAccessedTimeIsUpdatedIsTrue() {
		final long expectedLastAccessTime = 1l;

		GemFireSession session = new GemFireSession();

		assertThat(session.getLastAccessedTime(), is(not(equalTo(expectedLastAccessTime))));
		assertThat(session.hasDelta(), is(false));

		session.setLastAccessedTime(expectedLastAccessTime);

		assertThat(session.getLastAccessedTime(), is(equalTo(expectedLastAccessTime)));
		assertThat(session.hasDelta(), is(true));

		session.setLastAccessedTime(expectedLastAccessTime);

		assertThat(session.getLastAccessedTime(), is(equalTo(expectedLastAccessTime)));
		assertThat(session.hasDelta(), is(true));
	}

	@Test
	public void hasDeltaWhenSessionMaxInactiveIntervalInSecondsIsUpdatedIsTrue() {
		final int expectedMaxInactiveIntervalInSeconds = 300;

		GemFireSession session = new GemFireSession();

		assertThat(session.getMaxInactiveIntervalInSeconds(), is(not(equalTo(expectedMaxInactiveIntervalInSeconds))));
		assertThat(session.hasDelta(), is(false));

		session.setMaxInactiveIntervalInSeconds(expectedMaxInactiveIntervalInSeconds);

		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(expectedMaxInactiveIntervalInSeconds)));
		assertThat(session.hasDelta(), is(true));

		session.setMaxInactiveIntervalInSeconds(expectedMaxInactiveIntervalInSeconds);

		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(expectedMaxInactiveIntervalInSeconds)));
		assertThat(session.hasDelta(), is(true));
	}

	@Test
	public void sessionToDelta() throws Exception {
		final DataOutput mockDataOutput = mock(DataOutput.class);

		GemFireSession session = new GemFireSession() {
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(String.valueOf(obj), is(equalTo("test")));
				assertThat(out, is(sameInstance(mockDataOutput)));
			}
		};

		session.setLastAccessedTime(1l);
		session.setMaxInactiveIntervalInSeconds(300);
		session.setAttribute("attrOne", "test");

		assertThat(session.hasDelta(), is(true));

		session.toDelta(mockDataOutput);

		assertThat(session.hasDelta(), is(false));

		verify(mockDataOutput, times(1)).writeLong(eq(1l));
		verify(mockDataOutput, times(1)).writeInt(eq(300));
		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrOne"));
	}

	@Test
	public void sessionFromDelta() throws Exception {
		final DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readLong()).thenReturn(1l);
		when(mockDataInput.readInt()).thenReturn(600).thenReturn(0);

		GemFireSession session = new GemFireSession() {
			@Override @SuppressWarnings("unchecked")
			<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				assertThat(in, is(sameInstance(mockDataInput)));
				return (T) "test";
			}
		};

		session.fromDelta(mockDataInput);

		assertThat(session.hasDelta(), is(false));
		assertThat(session.getLastAccessedTime(), is(equalTo(1l)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(600)));
		assertThat(session.getAttributeNames().isEmpty(), is(true));

		verify(mockDataInput, times(1)).readLong();
		verify(mockDataInput, times(2)).readInt();
		verify(mockDataInput, never()).readUTF();
	}

	@Test
	public void sessionComparisons() {
		final long twoHoursAgo = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));

		GemFireSession sessionOne = new GemFireSession(mockSession("1", twoHoursAgo, MAX_INACTIVE_INTERVAL_IN_SECONDS));
		GemFireSession sessionTwo = new GemFireSession("2");

		assertThat(sessionOne.getCreationTime(), is(equalTo(twoHoursAgo)));
		assertThat(sessionTwo.getCreationTime(), is(greaterThan(twoHoursAgo)));
		assertThat(sessionOne.compareTo(sessionTwo), is(lessThan(0)));
		assertThat(sessionOne.compareTo(sessionOne), is(equalTo(0)));
		assertThat(sessionTwo.compareTo(sessionOne), is(greaterThan(0)));
	}

	@Test
	public void sessionEqualsDifferentSessionBasedOnId() {
		GemFireSession sessionOne = new GemFireSession("1");

		sessionOne.setLastAccessedTime(12345l);
		sessionOne.setMaxInactiveIntervalInSeconds(120);
		sessionOne.setPrincipalName("jblum");

		GemFireSession sessionTwo = new GemFireSession("1");

		sessionTwo.setLastAccessedTime(67890l);
		sessionTwo.setMaxInactiveIntervalInSeconds(300);
		sessionTwo.setPrincipalName("rwinch");

		assertThat(sessionOne.getId().equals(sessionTwo.getId()), is(true));
		assertThat(sessionOne.getLastAccessedTime() == sessionTwo.getLastAccessedTime(), is(false));
		assertThat(sessionOne.getMaxInactiveIntervalInSeconds() == sessionTwo.getMaxInactiveIntervalInSeconds(), is(false));
		assertThat(sessionOne.getPrincipalName().equals(sessionTwo.getPrincipalName()), is(false));
		assertThat(sessionOne.equals(sessionTwo), is(true));
	}

	@Test
	public void sessionHashCodeIsNotEqualToStringIdHashCode() {
		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId(), is(equalTo("1")));
		assertThat(session.hashCode(), is(not(equalTo("1".hashCode()))));
	}

	@Test
	public void sessionAttributesFromSession() {
		Session mockSession = mock(Session.class);

		when(mockSession.getAttributeNames()).thenReturn(asSet("attrOne", "attrTwo"));
		when(mockSession.getAttribute(eq("attrOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attrTwo"))).thenReturn("testTwo");

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getAttributeNames().isEmpty(), is(true));

		sessionAttributes.from(mockSession);

		assertThat(sessionAttributes.getAttributeNames().size(), is(equalTo(2)));
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo")), is(true));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo")), is(equalTo("testTwo")));

		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
		verify(mockSession, times(1)).getAttribute(eq("attrTwo"));
	}

	@Test
	public void sessionAttributesFromSessionAttributes() {
		GemFireSessionAttributes source = new GemFireSessionAttributes();

		source.setAttribute("attrOne", "testOne");
		source.setAttribute("attrTwo", "testTwo");

		GemFireSessionAttributes target = new GemFireSessionAttributes();

		assertThat(target.getAttributeNames().isEmpty(), is(true));

		target.from(source);

		assertThat(target.getAttributeNames().size(), is(equalTo(2)));
		assertThat(target.getAttributeNames().containsAll(asSet("attrOne", "attrTwo")), is(true));
		assertThat(String.valueOf(target.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(target.getAttribute("attrTwo")), is(equalTo("testTwo")));
	}

	@Test
	public void sessionAttributesToData() throws Exception {
		final DataOutput mockDataOutput = mock(DataOutput.class);

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes() {
			private int count = 0;
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(Arrays.asList("testOne", "testTwo").get(count++), is(equalTo(obj)));
				assertThat(out, is(sameInstance(mockDataOutput)));
			}
		};

		sessionAttributes.setAttribute("attrOne", "testOne");
		sessionAttributes.setAttribute("attrTwo", "testTwo");

		sessionAttributes.toData(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(2));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrOne"));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrTwo"));
	}

	@Test
	public void sessionAttributesFromData() throws Exception {
		final DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readInt()).thenReturn(2);
		when(mockDataInput.readUTF()).thenReturn("attrOne").thenReturn("attrTwo");

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes() {
			private int count = 0;
			@Override @SuppressWarnings("unchecked")
			<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				assertThat(in, is(sameInstance(mockDataInput)));
				return (T) Arrays.asList("testOne", "testTwo").get(count++);
			}
		};

		assertThat(sessionAttributes.getAttributeNames().isEmpty(), is(true));

		sessionAttributes.fromData(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames().size(), is(equalTo(2)));
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo")), is(true));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo")), is(equalTo("testTwo")));

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(2)).readUTF();
	}

	@Test
	public void sessionAttributesHasDeltaIsFalse() {
		assertThat(new GemFireSessionAttributes().hasDelta(), is(false));
	}

	@Test
	public void sessionAttributesHasDeltaIsTrue() {
		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.hasDelta(), is(false));

		sessionAttributes.setAttribute("attrOne", "testOne");

		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(sessionAttributes.hasDelta(), is(true));
	}

	@Test
	public void sessionAttributesToDelta() throws Exception {
		final DataOutput mockDataOutput = mock(DataOutput.class);

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes() {
			private int count = 0;
			@Override void writeObject(Object obj, DataOutput out) throws IOException {
				assertThat(Arrays.asList("testOne", "testTwo", "testThree").get(count++), is(equalTo(obj)));
				assertThat(out, is(sameInstance(mockDataOutput)));
			}
		};

		sessionAttributes.setAttribute("attrOne", "testOne");
		sessionAttributes.setAttribute("attrTwo", "testTwo");

		assertThat(sessionAttributes.hasDelta(), is(true));

		sessionAttributes.toDelta(mockDataOutput);

		assertThat(sessionAttributes.hasDelta(), is(false));

		verify(mockDataOutput, times(1)).writeInt(eq(2));
		verify(mockDataOutput, times(1)).writeUTF("attrOne");
		verify(mockDataOutput, times(1)).writeUTF("attrTwo");
		reset(mockDataOutput);

		sessionAttributes.setAttribute("attrOne", "testOne");

		assertThat(sessionAttributes.hasDelta(), is(false));

		sessionAttributes.toDelta(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(0));
		verify(mockDataOutput, never()).writeUTF(any(String.class));
		reset(mockDataOutput);

		sessionAttributes.setAttribute("attrTwo", "testThree");

		assertThat(sessionAttributes.hasDelta(), is(true));

		sessionAttributes.toDelta(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrTwo"));
	}

	@Test
	public void sessionAttributesFromDelta() throws Exception {
		final DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readInt()).thenReturn(2);
		when(mockDataInput.readUTF()).thenReturn("attrOne").thenReturn("attrTwo");

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes() {
			private int count = 0;
			@Override @SuppressWarnings("unchecked")
			<T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
				assertThat(in, is(sameInstance(mockDataInput)));
				return (T) Arrays.asList("testOne", "testTwo", "testThree").get(count++);
			}
		};

		sessionAttributes.setAttribute("attrOne", "one");
		sessionAttributes.setAttribute("attrTwo", "two");

		assertThat(sessionAttributes.getAttributeNames().size(), is(equalTo(2)));
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo")), is(true));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne")), is(equalTo("one")));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo")), is(equalTo("two")));
		assertThat(sessionAttributes.hasDelta(), is(true));

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames().size(), is(equalTo(2)));
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo")), is(true));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo")), is(equalTo("testTwo")));
		assertThat(sessionAttributes.hasDelta(), is(false));

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(2)).readUTF();
		reset(mockDataInput);

		when(mockDataInput.readInt()).thenReturn(1);
		when(mockDataInput.readUTF()).thenReturn("attrTwo");

		sessionAttributes.setAttribute("attrOne", "one");
		sessionAttributes.setAttribute("attrTwo", "two");

		assertThat(sessionAttributes.getAttributeNames().size(), is(equalTo(2)));
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo")), is(true));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne")), is(equalTo("one")));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo")), is(equalTo("two")));
		assertThat(sessionAttributes.hasDelta(), is(true));

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames().size(), is(equalTo(2)));
		assertThat(sessionAttributes.getAttributeNames().containsAll(asSet("attrOne", "attrTwo")), is(true));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrOne")), is(equalTo("one")));
		assertThat(String.valueOf(sessionAttributes.getAttribute("attrTwo")), is(equalTo("testThree")));
		assertThat(sessionAttributes.hasDelta(), is(true));

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(1)).readUTF();
	}

	@Test
	public void sessionWithAttributesAreThreadSafe() throws Throwable {
		TestFramework.runOnce(new ThreadSafeSessionTest());
	}

	@SuppressWarnings("unused")
	protected static final class ThreadSafeSessionTest extends MultithreadedTestCase {

		private final long beforeOrAtCreationTime = System.currentTimeMillis();

		private GemFireSession session;

		private volatile long expectedCreationTime;

		@Override
		public void initialize() {
			session = new GemFireSession("1");

			assertThat(session, is(notNullValue()));
			assertThat(session.getId(), is(equalTo("1")));
			assertThat(session.getCreationTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
			assertThat(session.getLastAccessedTime(), is(equalTo(session.getCreationTime())));
			assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(0)));
			assertThat(session.getPrincipalName(), is(nullValue()));
			assertThat(session.getAttributeNames().isEmpty(), is(true));

			expectedCreationTime = session.getCreationTime();

			session.setLastAccessedTime(0l);
			session.setMaxInactiveIntervalInSeconds(60);
			session.setPrincipalName("jblum");
		}

		public void thread1() {
			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 1");

			assertThat(session, is(notNullValue()));
			assertThat(session.getId(), is(equalTo("1")));
			assertThat(session.getCreationTime(), is(equalTo(expectedCreationTime)));
			assertThat(session.getLastAccessedTime(), is(equalTo(0l)));
			assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(60)));
			assertThat(session.getPrincipalName(), is(equalTo("jblum")));
			assertThat(session.getAttributeNames().size(), is(equalTo(1)));
			assertThat(String.valueOf(session.getAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME)), is(equalTo("jblum")));

			session.setAttribute("tennis", "ping");
			session.setAttribute("junk", "test");
			session.setLastAccessedTime(1l);
			session.setMaxInactiveIntervalInSeconds(120);
			session.setPrincipalName("rwinch");

			waitForTick(2);

			assertThat(session, is(notNullValue()));
			assertThat(session.getId(), is(equalTo("1")));
			assertThat(session.getCreationTime(), is(equalTo(expectedCreationTime)));
			assertThat(session.getLastAccessedTime(), is(equalTo(2l)));
			assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(180)));
			assertThat(session.getPrincipalName(), is(equalTo("ogierke")));
			assertThat(session.getAttributeNames().size(), is(equalTo(3)));
			assertThat(session.getAttributeNames().containsAll(asSet("tennis", "greeting")), is(true));
			assertThat(session.getAttributeNames().contains("junk"), is(false));
			assertThat(session.getAttribute("junk"), is(nullValue()));
			assertThat(String.valueOf(session.getAttribute("tennis")), is(equalTo("pong")));
			assertThat(String.valueOf(session.getAttribute("greeting")), is(equalTo("hello")));
		}

		public void thread2() {
			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 2");

			waitForTick(1);
			assertTick(1);

			assertThat(session, is(notNullValue()));
			assertThat(session.getId(), is(equalTo("1")));
			assertThat(session.getCreationTime(), is(equalTo(expectedCreationTime)));
			assertThat(session.getLastAccessedTime(), is(equalTo(1l)));
			assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(120)));
			assertThat(session.getPrincipalName(), is(equalTo("rwinch")));
			assertThat(session.getAttributeNames().size(), is(equalTo(3)));
			assertThat(session.getAttributeNames().containsAll(asSet("tennis", "junk")), is(true));
			assertThat(String.valueOf(session.getAttribute("junk")), is(equalTo("test")));
			assertThat(String.valueOf(session.getAttribute("tennis")), is(equalTo("ping")));

			session.setAttribute("tennis", "pong");
			session.setAttribute("greeting", "hello");
			session.removeAttribute("junk");
			session.setLastAccessedTime(2l);
			session.setMaxInactiveIntervalInSeconds(180);
			session.setPrincipalName("ogierke");
		}

		@Override
		public void finish() {
			session = null;
		}
	}

	protected static class TestGemFireOperationsSessionRepository extends AbstractGemFireOperationsSessionRepository {

		protected TestGemFireOperationsSessionRepository(GemfireOperations gemfireOperations) {
			super(gemfireOperations);
		}

		public Map<String, ExpiringSession> findByPrincipalName(String principalName) {
			throw new UnsupportedOperationException("not implemented");
		}

		public ExpiringSession createSession() {
			throw new UnsupportedOperationException("not implemented");
		}

		public ExpiringSession getSession(String id) {
			throw new UnsupportedOperationException("not implemented");
		}

		public void save(ExpiringSession session) {
			throw new UnsupportedOperationException("not implemented");
		}

		public void delete(String id) {
			throw new UnsupportedOperationException("not implemented");
		}
	}

}

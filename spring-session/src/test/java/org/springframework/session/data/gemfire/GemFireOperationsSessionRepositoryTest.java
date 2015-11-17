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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.session.data.gemfire.GemFireOperationsSessionRepository.GemFireSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireAccessor;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.ExpiringSession;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionDeletedEvent;

import com.gemstone.gemfire.cache.AttributesMutator;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.SelectResults;

/**
 * The GemFireOperationsSessionRepositoryTest class is a test suite of test cases testing the contract and functionality
 * of the GemFireOperationsSessionRepository class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.runners.MockitoJUnitRunner
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class GemFireOperationsSessionRepositoryTest {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	@Mock
	private ApplicationEventPublisher mockApplicationEventPublisher;

	@Mock
	private AttributesMutator<Object, ExpiringSession> mockAttributesMutator;

	@Mock
	private Region<Object, ExpiringSession> mockRegion;

	@Mock
	private GemfireOperationsAccessor mockTemplate;

	private GemFireOperationsSessionRepository sessionRepository;

	@Before
	public void setup() throws Exception {
		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);
		when(mockRegion.getFullPath()).thenReturn("/Example");
		when(mockTemplate.<Object, ExpiringSession>getRegion()).thenReturn(mockRegion);

		sessionRepository = new GemFireOperationsSessionRepository(mockTemplate);
		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		sessionRepository.afterPropertiesSet();

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));
		assertThat(sessionRepository.getFullyQualifiedRegionName(), is(equalTo("/Example")));
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
	}

	@After
	public void tearDown() {
		verify(mockAttributesMutator, times(1)).addCacheListener(same(sessionRepository));
		verify(mockRegion, times(1)).getFullPath();
		verify(mockTemplate, times(1)).getRegion();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameFindsMatchingSessions() throws Exception {
		ExpiringSession mockSessionOne = mock(ExpiringSession.class, "MockSessionOne");
		ExpiringSession mockSessionTwo = mock(ExpiringSession.class, "MockSessionTwo");
		ExpiringSession mockSessionThree = mock(ExpiringSession.class, "MockSessionThree");

		when(mockSessionOne.getId()).thenReturn("1");
		when(mockSessionTwo.getId()).thenReturn("2");
		when(mockSessionThree.getId()).thenReturn("3");

		SelectResults mockSelectResults = mock(SelectResults.class);

		when(mockSelectResults.asList()).thenReturn(Arrays.asList(mockSessionOne, mockSessionTwo, mockSessionThree));

		String principalName = "jblum";

		String expectedOql = String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
			sessionRepository.getFullyQualifiedRegionName());

		when(mockTemplate.find(eq(expectedOql), eq(principalName))).thenReturn(mockSelectResults);

		Map<String, ExpiringSession> sessions = sessionRepository.findByPrincipalName(principalName);

		assertThat(sessions, is(notNullValue()));
		assertThat(sessions.size(), is(equalTo(3)));
		assertThat(sessions.get("1"), is(equalTo(mockSessionOne)));
		assertThat(sessions.get("2"), is(equalTo(mockSessionTwo)));
		assertThat(sessions.get("3"), is(equalTo(mockSessionThree)));

		verify(mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
		verify(mockSessionOne, times(1)).getId();
		verify(mockSessionTwo, times(1)).getId();
		verify(mockSessionThree, times(1)).getId();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameReturnsNoMatchingSessions() {
		SelectResults mockSelectResults = mock(SelectResults.class);

		when(mockSelectResults.asList()).thenReturn(Collections.emptyList());

		String principalName = "jblum";

		String expectedOql = String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
			sessionRepository.getFullyQualifiedRegionName());

		when(mockTemplate.find(eq(expectedOql), eq(principalName))).thenReturn(mockSelectResults);

		Map<String, ExpiringSession> sessions = sessionRepository.findByPrincipalName(principalName);

		assertThat(sessions, is(notNullValue()));
		assertThat(sessions.isEmpty(), is(true));

		verify(mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
	}

	@Test
	public void createProperlyInitializedSession() {
		final long beforeOrAtCreationTime = System.currentTimeMillis();

		ExpiringSession session = sessionRepository.createSession();

		assertThat(session, is(instanceOf(GemFireSession.class)));
		assertThat(session.getId(), is(notNullValue()));
		assertThat(session.getAttributeNames().isEmpty(), is(true));
		assertThat(session.getCreationTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(session.getLastAccessedTime(), is(greaterThanOrEqualTo(beforeOrAtCreationTime)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
	}

	@Test
	public void getSessionDeletesMatchingExpiredSessionById() {
		final String expectedSessionId = "1";

		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.isExpired()).thenReturn(true);
		when(mockSession.getId()).thenReturn(expectedSessionId);
		when(mockTemplate.get(eq(expectedSessionId))).thenReturn(mockSession);
		when(mockTemplate.remove(eq(expectedSessionId))).thenReturn(mockSession);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(sameInstance((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(same(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(expectedSessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(any(ApplicationEvent.class));

		assertThat(sessionRepository.getSession(expectedSessionId), is(nullValue()));

		verify(mockTemplate, times(1)).get(eq(expectedSessionId));
		verify(mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(2)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void getSessionFindsMatchingNonExpiredSessionById() {
		final String expectedId = "1";

		final long expectedCreationTime = System.currentTimeMillis();
		final long currentLastAccessedTime = (expectedCreationTime + TimeUnit.MINUTES.toMillis(5));

		ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.isExpired()).thenReturn(false);
		when(mockSession.getId()).thenReturn(expectedId);
		when(mockSession.getCreationTime()).thenReturn(expectedCreationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(currentLastAccessedTime);
		when(mockSession.getAttributeNames()).thenReturn(Collections.singleton("attrOne"));
		when(mockSession.getAttribute(eq("attrOne"))).thenReturn("test");
		when(mockTemplate.get(eq(expectedId))).thenReturn(mockSession);

		ExpiringSession actualSession = sessionRepository.getSession(expectedId);

		assertThat(actualSession, is(not(sameInstance(mockSession))));
		assertThat(actualSession.getId(), is(equalTo(expectedId)));
		assertThat(actualSession.getCreationTime(), is(equalTo(expectedCreationTime)));
		assertThat(actualSession.getLastAccessedTime(), is(not(equalTo(currentLastAccessedTime))));
		assertThat(actualSession.getLastAccessedTime(), is(greaterThanOrEqualTo(expectedCreationTime)));
		assertThat(actualSession.getAttributeNames(), is(equalTo(Collections.singleton("attrOne"))));
		assertThat(String.valueOf(actualSession.getAttribute("attrOne")), is(equalTo("test")));

		verify(mockTemplate, times(1)).get(eq(expectedId));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
	}

	@Test
	public void getSessionReturnsNull() {
		when(mockTemplate.get(anyString())).thenReturn(null);
		assertThat(sessionRepository.getSession("1"), is(nullValue()));
	}

	@Test
	public void saveStoresSession() {
		final String expectedSessionId = "1";

		final long expectedCreationTime = System.currentTimeMillis();
		final long expectedLastAccessTime = (expectedCreationTime + TimeUnit.MINUTES.toMillis(5));

		ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(expectedSessionId);
		when(mockSession.getCreationTime()).thenReturn(expectedCreationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(expectedLastAccessTime);
		when(mockSession.getMaxInactiveIntervalInSeconds()).thenReturn(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		when(mockSession.getAttributeNames()).thenReturn(Collections.<String>emptySet());

		when(mockTemplate.put(eq(expectedSessionId), isA(GemFireSession.class)))
			.thenAnswer(new Answer<ExpiringSession>() {
				public ExpiringSession answer(final InvocationOnMock invocation) throws Throwable {
					ExpiringSession session = invocation.getArgumentAt(1, ExpiringSession.class);

					assertThat(session, is(notNullValue()));
					assertThat(session.getId(), is(equalTo(expectedSessionId)));
					assertThat(session.getCreationTime(), is(equalTo(expectedCreationTime)));
					assertThat(session.getLastAccessedTime(), is(equalTo(expectedLastAccessTime)));
					assertThat(session.getMaxInactiveIntervalInSeconds(),
						is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
					assertThat(session.getAttributeNames().isEmpty(), is(true));

					return null;
				}
			});

		sessionRepository.save(mockSession);

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockTemplate, times(1)).put(eq(expectedSessionId), isA(GemFireSession.class));
	}

	@Test
	public void deleteRemovesExistingSessionAndHandlesDelete() {
		final String expectedSessionId = "1";

		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(expectedSessionId);
		when(mockTemplate.remove(eq(expectedSessionId))).thenReturn(mockSession);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(sameInstance((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(sameInstance(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(expectedSessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(SessionDeletedEvent.class));

		sessionRepository.delete(expectedSessionId);

		verify(mockSession, times(1)).getId();
		verify(mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void deleteRemovesNonExistingSessionAndHandlesDelete() {
		final String expectedSessionId = "1";

		when(mockTemplate.remove(anyString())).thenReturn(null);

		doAnswer(new Answer<Void>() {
			public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(sameInstance((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(expectedSessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(SessionDeletedEvent.class));

		sessionRepository.delete(expectedSessionId);

		verify(mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	protected abstract class GemfireOperationsAccessor extends GemfireAccessor implements GemfireOperations {
	}

}

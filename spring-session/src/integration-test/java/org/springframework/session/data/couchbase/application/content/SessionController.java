/*
 * Copyright 2014-2016 the original author or authors.
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
package org.springframework.session.data.couchbase.application.content;

import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.couchbase.CouchbaseSession;
import org.springframework.session.data.couchbase.CouchbaseSessionRepository;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sample controller for integration tests.
 *
 * @author Mariusz Kopylec
 * @since 1.2.0
 */
@RestController
@RequestMapping("session")
public class SessionController {

	private static final String PRINCIPAL_NAME = "user";
	private static final String SESSION_ATTRIBUTE_NAME = "attribute";

	@Autowired(required = false)
	private SessionScopedBean sessionBean;
	@Autowired(required = false)
	private CouchbaseSessionRepository sessionRepository;

	@RequestMapping(value = "attribute", method = RequestMethod.POST)
	public void setAttribute(@RequestBody Message dto, HttpSession session) {
		session.setAttribute(SESSION_ATTRIBUTE_NAME, dto);
	}

	@RequestMapping("attribute")
	public Object getAttribute(HttpSession session) {
		return session.getAttribute(SESSION_ATTRIBUTE_NAME);
	}

	@RequestMapping(value = "attribute", method = RequestMethod.DELETE)
	public void deleteAttribute(HttpSession session) {
		session.removeAttribute(SESSION_ATTRIBUTE_NAME);
	}

	@RequestMapping(value = "bean", method = RequestMethod.POST)
	public void setBean(@RequestBody Message dto) {
		this.sessionBean.setText(dto.getText());
		this.sessionBean.setNumber(dto.getNumber());
	}

	@RequestMapping("bean")
	public Message getBean() {
		Message message = new Message();
		message.setText(this.sessionBean.getText());
		message.setNumber(this.sessionBean.getNumber());
		return message;
	}

	@RequestMapping(method = RequestMethod.DELETE)
	public void invalidateSession(HttpSession session) {
		session.invalidate();
	}

	@RequestMapping(value = "principal", method = RequestMethod.POST)
	public String setPrincipalAttribute(HttpSession session) {
		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
				PRINCIPAL_NAME);
		return session.getId();
	}

	@RequestMapping("principal")
	public Set<String> getPrincipalSessions() {
		Map<String, CouchbaseSession> sessionsById = this.sessionRepository
				.findByIndexNameAndIndexValue(
						FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
						PRINCIPAL_NAME);
		return sessionsById.keySet();
	}
}

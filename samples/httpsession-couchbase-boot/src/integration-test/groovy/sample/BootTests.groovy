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

package sample

import geb.spock.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.IntegrationTest
import org.springframework.boot.test.SpringApplicationConfiguration
import org.springframework.boot.test.SpringApplicationContextLoader
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import sample.pages.HomePage
import sample.pages.LoginPage
import spock.lang.Stepwise
import pages.*

/**
 * Tests the demo that supports multiple sessions
 *
 * @author Rob Winch
 */
@Stepwise
@ContextConfiguration(classes = Application, loader = SpringApplicationContextLoader)
@WebAppConfiguration
@IntegrationTest
class BootTests extends GebReportingSpec {

	def 'Unauthenticated user sent to log in page'() {
		when: 'unauthenticated user request protected page'
		via HomePage
		then: 'sent to the log in page'
		at LoginPage
	}

	def 'Log in views home page'() {
		when: 'log in successfully'
		login()
		then: 'sent to original page'
		at HomePage
		and: 'the username is displayed'
		username == 'user'
		and: 'Spring Session Management is being used'
		driver.manage().cookies.find { it.name == 'SESSION' }
		and: 'Standard Session is NOT being used'
		!driver.manage().cookies.find { it.name == 'JSESSIONID' }
	}

	def 'Log out success'() {
		when:
		logout()
		then:
		at LoginPage
	}

	def 'Logged out user sent to log in page'() {
		when: 'logged out user request protected page'
		via HomePage
		then: 'sent to the log in page'
		at LoginPage
	}
}

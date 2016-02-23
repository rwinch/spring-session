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

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.IndexFactoryBean;
import org.springframework.data.gemfire.IndexType;
import org.springframework.data.gemfire.RegionAttributesFactoryBean;
import org.springframework.session.ExpiringSession;
import org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration;
import org.springframework.session.data.gemfire.AbstractGemfireOperationsSessionRepository.GemfireSession;
import org.springframework.session.data.gemfire.GemfireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.GemfireCacheTypeAwareRegionFactoryBean;
import org.springframework.session.data.gemfire.support.GemfireUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.ExpirationAttributes;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;

/**
 * The GemfireHttpSessionConfiguration class is a Spring @Configuration class used to configure and initialize
 * Pivotal GemFire (or Apache Geode) as a clustered, replicated HttpSession provider implementation in Spring Session.
 *
 * @author John Blum
 * @see org.springframework.beans.factory.BeanClassLoaderAware
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.context.annotation.Configuration
 * @see org.springframework.context.annotation.ImportAware
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.data.gemfire.IndexFactoryBean
 * @see org.springframework.data.gemfire.RegionAttributesFactoryBean
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.config.annotation.web.http.SpringHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.GemfireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.GemfireCacheTypeAwareRegionFactoryBean
 * @see com.gemstone.gemfire.cache.ExpirationAttributes
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.RegionAttributes
 * @see com.gemstone.gemfire.cache.RegionShortcut
 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
 * @since 1.1.0
 */
@Configuration
public class GemfireHttpSessionConfiguration extends SpringHttpSessionConfiguration
		implements BeanClassLoaderAware, ImportAware {

	public static final int DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS = (int) TimeUnit.MINUTES.toSeconds(30);

	protected static final Class<Object> SPRING_SESSION_GEMFIRE_REGION_KEY_CONSTRAINT = Object.class;
	protected static final Class<GemfireSession> SPRING_SESSION_GEMFIRE_REGION_VALUE_CONSTRAINT = GemfireSession.class;

	public static final ClientRegionShortcut DEFAULT_CLIENT_REGION_SHORTCUT = ClientRegionShortcut.PROXY;

	public static final RegionShortcut DEFAULT_SERVER_REGION_SHORTCUT = RegionShortcut.PARTITION;

	public static final String DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME = "ClusteredSpringSessions";

	public static final String[] DEFAULT_INDEXABLE_SESSION_ATTRIBUTES = new String[0];

	private int maxInactiveIntervalInSeconds = DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS;

	private ClassLoader beanClassLoader;

	private ClientRegionShortcut clientRegionShortcut = DEFAULT_CLIENT_REGION_SHORTCUT;

	private RegionShortcut serverRegionShortcut = DEFAULT_SERVER_REGION_SHORTCUT;

	private String springSessionGemfireRegionName = DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME;

	private String[] indexableSessionAttributes = DEFAULT_INDEXABLE_SESSION_ATTRIBUTES;

	/**
	 * Sets a reference to the {@link ClassLoader} used to load bean definition class types in a Spring context.
	 *
	 * @param beanClassLoader the ClassLoader used by the Spring container to load bean class types.
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(ClassLoader)
	 * @see java.lang.ClassLoader
	 */
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	/**
	 * Gets a reference to the {@link ClassLoader} used to load bean definition class types in a Spring context.
	 *
	 * @return the ClassLoader used by the Spring container to load bean class types.
	 * @see java.lang.ClassLoader
	 */
	protected ClassLoader getBeanClassLoader() {
		return beanClassLoader;
	}

	/**
	 * Sets the {@link ClientRegionShortcut} used to configure the GemFire ClientCache Region
	 * that will store Spring Sessions.
	 *
	 * @param shortcut the ClientRegionShortcut used to configure the GemFire ClientCache Region.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	public void setClientRegionShortcut(ClientRegionShortcut shortcut) {
		this.clientRegionShortcut = shortcut;
	}

	/**
	 * Gets the {@link ClientRegionShortcut} used to configure the GemFire ClientCache Region
	 * that will store Spring Sessions. Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @return the ClientRegionShortcut used to configure the GemFire ClientCache Region.
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 * @see EnableGemfireHttpSession#clientRegionShortcut()
	 */
	protected ClientRegionShortcut getClientRegionShortcut() {
		return (clientRegionShortcut != null ? clientRegionShortcut : DEFAULT_CLIENT_REGION_SHORTCUT);
	}

	/**
	 * Sets the names of all Session attributes that should be indexed by GemFire.
	 *
	 * @param indexableSessionAttributes an array of Strings indicating the names of all Session attributes
	 * for which an Index will be created by GemFire.
	 */
	public void setIndexableSessionAttributes(String[] indexableSessionAttributes) {
		this.indexableSessionAttributes = indexableSessionAttributes;
	}

	/**
	 * Get the names of all Session attributes that should be indexed by GemFire.
	 *
	 * @return an array of Strings indicating the names of all Session attributes for which an Index
	 * will be created by GemFire. Defaults to an empty String array if unspecified.
	 * @see EnableGemfireHttpSession#indexableSessionAttributes()
	 */
	protected String[] getIndexableSessionAttributes() {
		return (indexableSessionAttributes != null ? indexableSessionAttributes : DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);
	}

	/**
	 * Gets the names of all Session attributes that will be indexed by GemFire as single String value constituting
	 * the Index expression of the Index definition.
	 *
	 * @return a String composed of all the named Session attributes on which a GemFire Index will be created
	 * as an Index definition expression.  If the indexable Session attributes were not specified, then the
	 * wildcard ("*") is returned.
	 * @see com.gemstone.gemfire.cache.query.Index#getIndexedExpression()
	 */
	protected String getIndexableSessionAttributesAsGemfireIndexExpression() {
		StringBuilder builder = new StringBuilder();

		for (String sessionAttribute : getIndexableSessionAttributes()) {
			builder.append(builder.length() > 0 ? ", " : "");
			builder.append(String.format("'%1$s'", sessionAttribute));
		}

		String indexExpression = builder.toString();

		return (indexExpression.isEmpty() ? "*" : indexExpression);
	}

	/**
	 * Sets the maximum interval in seconds in which a Session can remain inactive before it is considered expired.
	 *
	 * @param maxInactiveIntervalInSeconds an integer value specifying the maximum interval in seconds that a Session
	 * can remain inactive before it is considered expired.
	 */
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	/**
	 * Gets the maximum interval in seconds in which a Session can remain inactive before it is considered expired.
	 *
	 * @return an integer value specifying the maximum interval in seconds that a Session can remain inactive
	 * before it is considered expired.
	 * @see EnableGemfireHttpSession#maxInactiveIntervalInSeconds()
	 */
	protected int getMaxInactiveIntervalInSeconds() {
		return maxInactiveIntervalInSeconds;
	}

	/**
	 * Sets the {@link RegionShortcut} used to configure the GemFire Cache Region that will store Spring Sessions.
	 *
	 * @param shortcut the RegionShortcut used to configure the GemFire Cache Region.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 */
	public void setServerRegionShortcut(RegionShortcut shortcut) {
		serverRegionShortcut = shortcut;
	}

	/**
	 * Gets the {@link RegionShortcut} used to configure the GemFire Cache Region that will store Spring Sessions.
	 * Defaults to {@link RegionShortcut#PARTITION}.
	 *
	 * @return the RegionShortcut used to configure the GemFire Cache Region.
	 * @see com.gemstone.gemfire.cache.RegionShortcut
	 * @see EnableGemfireHttpSession#serverRegionShortcut()
	 */
	protected RegionShortcut getServerRegionShortcut() {
		return (serverRegionShortcut != null ? serverRegionShortcut : DEFAULT_SERVER_REGION_SHORTCUT);
	}

	/**
	 * Sets the name of the Gemfire (Client)Cache Region used to store Sessions.
	 *
	 * @param springSessionGemfireRegionName a String specifying the name of the GemFire (Client)Cache Region
	 * used to store the Session.
	 */
	public void setSpringSessionGemfireRegionName(String springSessionGemfireRegionName) {
		this.springSessionGemfireRegionName = springSessionGemfireRegionName;
	}

	/**
	 * Gets the name of the Gemfire (Client)Cache Region used to store Sessions. Defaults to 'ClusteredSpringSessions'.
	 *
	 * @return a String specifying the name of the GemFire (Client)Cache Region
	 * used to store the Session.
	 * @see com.gemstone.gemfire.cache.Region#getName()
	 * @see EnableGemfireHttpSession#regionName()
	 */
	protected String getSpringSessionGemfireRegionName() {
		return (StringUtils.hasText(springSessionGemfireRegionName) ? springSessionGemfireRegionName
			: DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME);
	}

	/**
	 * Callback with the {@link AnnotationMetadata} of the class containing @Import annotation that imported
	 * this @Configuration class.
	 *
	 * @param importMetadata the AnnotationMetadata of the class importing this @Configuration class.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemfireHttpSession
	 * @see org.springframework.core.type.AnnotationMetadata
	 */
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		AnnotationAttributes enableGemfireHttpSessionAnnotationAttributes = AnnotationAttributes.fromMap(
			importMetadata.getAnnotationAttributes(EnableGemfireHttpSession.class.getName()));

		setClientRegionShortcut(ClientRegionShortcut.class.cast(enableGemfireHttpSessionAnnotationAttributes.getEnum(
			"clientRegionShortcut")));

		setIndexableSessionAttributes(enableGemfireHttpSessionAnnotationAttributes.getStringArray(
			"indexableSessionAttributes"));

		setMaxInactiveIntervalInSeconds(enableGemfireHttpSessionAnnotationAttributes.getNumber(
			"maxInactiveIntervalInSeconds").intValue());

		setServerRegionShortcut(RegionShortcut.class.cast(enableGemfireHttpSessionAnnotationAttributes.getEnum(
			"serverRegionShortcut")));

		setSpringSessionGemfireRegionName(enableGemfireHttpSessionAnnotationAttributes.getString("regionName"));
	}

	/**
	 * Defines the Spring SessionRepository bean used to interact with GemFire as a Spring Session provider.
	 *
	 * @param gemfireOperations an instance of {@link GemfireOperations} used to manage Spring Sessions in GemFire.
	 * @return a GemfireOperationsSessionRepository for managing (clustering/replicating) Sessions using GemFire.
	 * @see org.springframework.session.data.gemfire.GemfireOperationsSessionRepository
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	@Bean
	public GemfireOperationsSessionRepository sessionRepository(@Qualifier("sessionRegionTemplate")
			GemfireOperations gemfireOperations) {

		GemfireOperationsSessionRepository sessionRepository = new GemfireOperationsSessionRepository(gemfireOperations);

		sessionRepository.setMaxInactiveIntervalInSeconds(getMaxInactiveIntervalInSeconds());

		return sessionRepository;
	}

	/**
	 * Defines a Spring GemfireTemplate bean used to interact with GemFire's (Client)Cache {@link Region}
	 * storing Sessions.
	 *
	 * @param gemFireCache reference to the single GemFire cache instance used by the {@link GemfireTemplate}
	 * to perform GemFire cache data access operations.
	 * @return a {@link GemfireTemplate} used to interact with GemFire's (Client)Cache {@link Region} storing Sessions.
	 * @see org.springframework.data.gemfire.GemfireTemplate
	 * @see com.gemstone.gemfire.cache.Region
	 */
	@Bean
	@DependsOn(DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public GemfireTemplate sessionRegionTemplate(GemFireCache gemFireCache) {
		return new GemfireTemplate(gemFireCache.getRegion(getSpringSessionGemfireRegionName()));
	}

	/**
	 * Defines a Spring GemFire {@link com.gemstone.gemfire.cache.Cache} {@link Region} bean used to store
	 * and manage Sessions using either a client-server or peer-to-peer (p2p) topology.
	 *
	 * @param gemFireCache a reference to the GemFire {@link com.gemstone.gemfire.cache.Cache}.
	 * @param sessionRegionAttributes the GemFire {@link RegionAttributes} used to configure the {@link Region}.
	 * @return a {@link GemfireCacheTypeAwareRegionFactoryBean} used to configure and initialize a GemFire Cache
	 * {@link Region} for storing and managing Sessions.
	 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.GemfireCacheTypeAwareRegionFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.RegionAttributes
	 * @see #getClientRegionShortcut()
	 * @see #getSpringSessionGemfireRegionName()
	 * @see #getServerRegionShortcut()
	 */
	@Bean(name = DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public GemfireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession> sessionRegion(GemFireCache gemFireCache,
			RegionAttributes<Object, ExpiringSession> sessionRegionAttributes) {

		GemfireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession> serverRegion =
			new GemfireCacheTypeAwareRegionFactoryBean<Object, ExpiringSession>();

		serverRegion.setGemFireCache(gemFireCache);
		serverRegion.setClientRegionShortcut(getClientRegionShortcut());
		serverRegion.setRegionAttributes(sessionRegionAttributes);
		serverRegion.setRegionName(getSpringSessionGemfireRegionName());
		serverRegion.setServerRegionShortcut(getServerRegionShortcut());

		return serverRegion;
	}

	/**
	 * Defines a Spring GemFire {@link RegionAttributes} bean used to configure and initialize the GemFire cache
	 * {@link Region} storing Sessions.  Expiration is also configured for the {@link Region} on the basis that the
	 * GemFire cache {@link Region} is a not a proxy, on either the client or server.
	 *
	 * @param gemFireCache a reference to the GemFire cache.
	 * @return an instance of {@link RegionAttributes} used to configure and initialize the GemFire cache {@link Region}
	 * for storing and managing Sessions.
	 * @see org.springframework.data.gemfire.RegionAttributesFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.PartitionAttributes
	 * @see #isExpirationAllowed(GemFireCache)
	 */
	@Bean
	@SuppressWarnings({ "unchecked", "deprecation" })
	public RegionAttributesFactoryBean sessionRegionAttributes(GemFireCache gemFireCache) {
		RegionAttributesFactoryBean regionAttributes = new RegionAttributesFactoryBean();

		regionAttributes.setKeyConstraint(SPRING_SESSION_GEMFIRE_REGION_KEY_CONSTRAINT);
		regionAttributes.setValueConstraint(SPRING_SESSION_GEMFIRE_REGION_VALUE_CONSTRAINT);

		if (isExpirationAllowed(gemFireCache)) {
			regionAttributes.setStatisticsEnabled(true);
			regionAttributes.setEntryIdleTimeout(new ExpirationAttributes(
				Math.max(getMaxInactiveIntervalInSeconds(), 0), ExpirationAction.INVALIDATE));
		}

		return regionAttributes;
	}

	/**
	 * Determines whether expiration configuration is allowed to be set on the GemFire cache {@link Region}
	 * used to store and manage Sessions.
	 *
	 * @param gemFireCache a reference to the GemFire cache.
	 * @return a boolean indicating if a {@link Region} can be configured for Region entry idle-timeout expiration.
	 * @see GemfireUtils#isClient(GemFireCache)
	 * @see GemfireUtils#isProxy(ClientRegionShortcut)
	 * @see GemfireUtils#isProxy(RegionShortcut)
	 */
	boolean isExpirationAllowed(GemFireCache gemFireCache) {
		return !(GemfireUtils.isClient(gemFireCache) ? GemfireUtils.isProxy(getClientRegionShortcut())
			: GemfireUtils.isProxy(getServerRegionShortcut()));
	}

	/**
	 * Defines a Spring GemFire Index bean on the GemFire cache {@link Region} storing and managing Sessions,
	 * specifically on the 'principalName' property for quick lookup and queries. This index will only be created
	 * on a server @{link Region}.
	 *
	 * @param gemFireCache a reference to the GemFire cache.
	 * @return a IndexFactoryBean creating an GemFire Index on the 'principalName' property of Sessions stored
	 * in the GemFire cache {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public IndexFactoryBean principalNameIndex(final GemFireCache gemFireCache) {
		IndexFactoryBean index = new IndexFactoryBean() {
			@Override public void afterPropertiesSet() throws Exception {
				if (GemfireUtils.isPeer(gemFireCache)) {
					super.afterPropertiesSet();
				}
			}
		};

		index.setCache(gemFireCache);
		index.setName("principalNameIndex");
		index.setExpression("principalName");
		index.setFrom(GemfireUtils.toRegionPath(getSpringSessionGemfireRegionName()));
		index.setOverride(true);
		index.setType(IndexType.HASH);

		return index;
	}

	/**
	 * Defines a Spring GemFire Index bean on the GemFire cache {@link Region} storing and managing Sessions,
	 * specifically on Session attributes for quick lookup and queries on Session attribute names with a given value.
	 * This index will only be created on a server @{link Region}.
	 *
	 * @param gemFireCache a reference to the GemFire cache.
	 * @return a IndexFactoryBean creating an GemFire Index on attributes of Sessions stored in the GemFire cache {@link Region}.
	 * @see org.springframework.data.gemfire.IndexFactoryBean
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 */
	@Bean
	@DependsOn(DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)
	public IndexFactoryBean sessionAttributesIndex(final GemFireCache gemFireCache) {
		IndexFactoryBean index = new IndexFactoryBean() {
			@Override public void afterPropertiesSet() throws Exception {
				if (GemfireUtils.isPeer(gemFireCache) && !ObjectUtils.isEmpty(getIndexableSessionAttributes())) {
					super.afterPropertiesSet();
				}
			}
		};

		index.setCache(gemFireCache);
		index.setName("sessionAttributesIndex");
		index.setExpression(String.format("s.attributes[%1$s]", getIndexableSessionAttributesAsGemfireIndexExpression()));
		index.setFrom(String.format("%1$s s", GemfireUtils.toRegionPath(getSpringSessionGemfireRegionName())));
		index.setOverride(true);

		return index;
	}

}

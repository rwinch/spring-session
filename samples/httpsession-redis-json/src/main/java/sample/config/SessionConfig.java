package sample.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.jackson2.*;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.jackson2.*;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import javax.servlet.http.Cookie;
import java.util.Collections;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.*;

/**
 * @author jitendra on 3/3/16.
 */
@EnableRedisHttpSession
public class SessionConfig {

	@Bean
	public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
		return new GenericJackson2JsonRedisSerializer(objectMapper());
	}

	@Bean
	public JedisConnectionFactory connectionFactory() {
		return new JedisConnectionFactory();
	}

	/**
	 * Customized {@link ObjectMapper} to add mix-in for class that doesn't have
	 * default constructors
	 *
	 * @return
	 */
	ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper()
				.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
		mapper.setVisibility(
				mapper.getVisibilityChecker()
						.withFieldVisibility(ANY)
						.withGetterVisibility(ANY)
		);

		mapper.addMixIn(UsernamePasswordAuthenticationToken.class, UsernamePasswordAuthenticationTokenMixin.class);
		mapper.addMixIn(SimpleGrantedAuthority.class, SimpleGrantedAuthorityMixin.class);
		mapper.addMixIn(Cookie.class, CookieMixin.class);
		mapper.addMixIn(DefaultSavedRequest.class, DefaultSavedRequestMixin.class);
		return mapper;
		// ObjectMapper mapper = new ObjectMapper();
		// mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
		// false);
		// mapper.setVisibility(
		// mapper.getVisibilityChecker()
		// .withFieldVisibility(ANY)
		// .withSetterVisibility(PUBLIC_ONLY)
		// .withGetterVisibility(PUBLIC_ONLY)
		// .withIsGetterVisibility(PUBLIC_ONLY)
		// );
		// mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL,
		// JsonTypeInfo.As.PROPERTY);
		// mapper.addMixIn(DefaultCsrfToken.class, DefaultCsrfTokenMixin.class);
		// mapper.addMixIn(SimpleGrantedAuthority.class,
		// SimpleGrantedAuthorityMixin.class);
		//// mapper.addMixIn(BadCredentialsException.class,
		// BadCredentialsExceptionMixin.class);
		// mapper.addMixIn(Collections.unmodifiableSet(Collections.EMPTY_SET).getClass(),
		// UnmodifiableSetMixin.class);
		//
		// SimpleModule module = new SimpleModule();
		//// module.addDeserializer(DefaultSavedRequest.class, new
		// DefaultSavedRequestDeserializer(DefaultSavedRequest.class));
		// module.addDeserializer(User.class, new UserDeserializer());
		//// module.addDeserializer(WebAuthenticationDetails.class, new
		// WebAuthenticationDetailsDeserializer());
		//// module.addDeserializer(Cookie.class, new HttpCookieDeserializer());
		// module.addDeserializer(UsernamePasswordAuthenticationToken.class, new
		// UsernamePasswordAuthenticationTokenDeserializer());
		//
		// mapper.registerModule(module);
		// return mapper;
	}
}

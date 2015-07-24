package grails3;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.redis.embedded.EnableEmbeddedRedis;

@EnableRedisHttpSession
@EnableEmbeddedRedis
@Configuration
public class SessionConfig {

}

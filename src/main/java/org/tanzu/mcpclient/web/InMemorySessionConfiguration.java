package org.tanzu.mcpclient.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;
import org.tanzu.mcpclient.vectorstore.DatabaseNotAvailableCondition;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableSpringHttpSession
@Order(2)
@Conditional(DatabaseNotAvailableCondition.class)
public class InMemorySessionConfiguration extends AbstractHttpSessionApplicationInitializer {

    @Bean
    public SessionRepository sessionRepository() {
        MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
        repository.setDefaultMaxInactiveInterval(Duration.ofDays(1)); // 24 hours
        return repository;
    }
}
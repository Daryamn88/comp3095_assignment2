package com.academicplanner.resourceservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Resilience4jConfig {

    private static final Logger logger = LoggerFactory.getLogger(Resilience4jConfig.class);

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(40) // Lower threshold for resource service
                .waitDurationInOpenState(Duration.ofSeconds(25))
                .slidingWindowSize(8)
                .minimumNumberOfCalls(4)
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(Exception.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        registry.getEventPublisher().onEntryAdded(entryAddedEvent -> {
            CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
            circuitBreaker.getEventPublisher().onStateTransition(event ->
                    logger.info("Resource Service Circuit Breaker '{}' state transition: {} -> {}",
                            circuitBreaker.getName(), event.getStateTransition().getFromState(), event.getStateTransition().getToState()));

            circuitBreaker.getEventPublisher().onCallNotPermitted(event ->
                    logger.warn("Resource Service Circuit Breaker '{}' call not permitted", circuitBreaker.getName()));

            circuitBreaker.getEventPublisher().onError(event ->
                    logger.error("Resource Service Circuit Breaker '{}' recorded error: {}",
                            circuitBreaker.getName(), event.getThrowable().getMessage()));
        });

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2) // Fewer retries for resource service
                .waitDuration(Duration.ofSeconds(1))
//                .intervalFunction(IntervalFunction.ofExponentialBackoff())
                .retryExceptions(Exception.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(config);

        registry.getEventPublisher().onEntryAdded(entryAddedEvent -> {
            Retry retry = entryAddedEvent.getAddedEntry();
            retry.getEventPublisher().onRetry(event ->
                    logger.warn("Resource Service Retry '{}' attempt #{}: {}",
                            retry.getName(), event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
        });

        return registry;
    }
}
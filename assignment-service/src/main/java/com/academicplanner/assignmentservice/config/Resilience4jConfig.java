package com.academicplanner.assignmentservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
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
                .failureRateThreshold(50) // 50% failure rate threshold
                .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30 seconds in open state
                .slidingWindowSize(10) // Consider last 10 calls
                .minimumNumberOfCalls(5) // Minimum 5 calls before calculating failure rate
                .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 test calls in half-open state
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(Exception.class) // Record all exceptions as failures
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        // Register event listeners for monitoring
        registry.getEventPublisher().onEntryAdded(entryAddedEvent -> {
            CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
            circuitBreaker.getEventPublisher().onStateTransition(event ->
                    logger.info("Circuit Breaker '{}' state transition: {} -> {}",
                            circuitBreaker.getName(), event.getStateTransition().getFromState(), event.getStateTransition().getToState()));

            circuitBreaker.getEventPublisher().onCallNotPermitted(event ->
                    logger.warn("Circuit Breaker '{}' call not permitted", circuitBreaker.getName()));

            circuitBreaker.getEventPublisher().onError(event ->
                    logger.error("Circuit Breaker '{}' recorded error: {}",
                            circuitBreaker.getName(), event.getThrowable().getMessage()));

            circuitBreaker.getEventPublisher().onSuccess(event ->
                    logger.debug("Circuit Breaker '{}' recorded success, duration: {}ms",
                            circuitBreaker.getName(), event.getElapsedDuration().toMillis()));
        });

        return registry;
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
//                .intervalFunction(IntervalFunction.ofExponentialBackoff())
                .retryExceptions(Exception.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(config);

        registry.getEventPublisher().onEntryAdded(entryAddedEvent -> {
            Retry retry = entryAddedEvent.getAddedEntry();
            retry.getEventPublisher().onRetry(event ->
                    logger.warn("Retry '{}' attempt #{}: {}",
                            retry.getName(), event.getNumberOfRetryAttempts(), event.getLastThrowable().getMessage()));
        });

        return registry;
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(10))
                .cancelRunningFuture(true)
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(config);

        registry.getEventPublisher().onEntryAdded(entryAddedEvent -> {
            TimeLimiter timeLimiter = entryAddedEvent.getAddedEntry();
            timeLimiter.getEventPublisher().onTimeout(event ->
                    logger.warn("Time Limiter '{}' timeout after {}",
                            timeLimiter.getName(), event.getCreationTime()));
        });

        return registry;
    }
}
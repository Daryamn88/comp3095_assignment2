package com.academicplanner.assignmentservice.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CircuitBreakerMonitoringService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public Map<String, Object> getCircuitBreakerStatus(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);

        Map<String, Object> status = new HashMap<>();
        status.put("name", name);
        status.put("state", circuitBreaker.getState().toString());
        status.put("failureRate", circuitBreaker.getMetrics().getFailureRate());
        status.put("numberOfBufferedCalls", circuitBreaker.getMetrics().getNumberOfBufferedCalls());
        status.put("numberOfFailedCalls", circuitBreaker.getMetrics().getNumberOfFailedCalls());
        status.put("numberOfSuccessfulCalls", circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());
        status.put("numberOfNotPermittedCalls", circuitBreaker.getMetrics().getNumberOfNotPermittedCalls());

        return status;
    }

    public Map<String, Object> getAllCircuitBreakersStatus() {
        Map<String, Object> allStatus = new HashMap<>();

        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            allStatus.put(cb.getName(), getCircuitBreakerStatus(cb.getName()));
        });

        return allStatus;
    }

    public void resetCircuitBreaker(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        circuitBreaker.reset();
    }

    public void transitionToClosedState(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        circuitBreaker.transitionToClosedState();
    }

    public void transitionToOpenState(String name) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name);
        circuitBreaker.transitionToOpenState();
    }
}
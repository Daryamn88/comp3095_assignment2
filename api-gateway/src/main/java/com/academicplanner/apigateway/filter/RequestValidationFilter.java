package com.academicplanner.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestValidationFilter extends AbstractGatewayFilterFactory<RequestValidationFilter.Config> {

    public RequestValidationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (config.isValidateHeaders()) {
                // Add header validation logic here
                String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
                if (userAgent == null && config.isRequireUserAgent()) {
                    return handleError(exchange, "User-Agent header is required", HttpStatus.BAD_REQUEST);
                }
            }

            if (config.isValidateContentType()) {
                String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
                String method = exchange.getRequest().getMethod().name();
                
                if (("POST".equals(method) || "PUT".equals(method)) && 
                    contentType != null && !contentType.contains("application/json")) {
                    return handleError(exchange, "Content-Type must be application/json for POST/PUT requests", 
                                     HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                }
            }

            return chain.filter(exchange);
        };
    }

    private Mono<Void> handleError(ServerWebExchange exchange, String message, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        
        String body = String.format("{\"error\":\"%s\",\"timestamp\":\"%s\"}", 
                                   message, java.time.LocalDateTime.now());
        
        org.springframework.core.io.buffer.DataBuffer buffer = 
            exchange.getResponse().bufferFactory().wrap(body.getBytes());
        
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    public static class Config {
        private boolean validateHeaders = false;
        private boolean requireUserAgent = false;
        private boolean validateContentType = true;

        // Getters and Setters
        public boolean isValidateHeaders() { return validateHeaders; }
        public void setValidateHeaders(boolean validateHeaders) { this.validateHeaders = validateHeaders; }
        
        public boolean isRequireUserAgent() { return requireUserAgent; }
        public void setRequireUserAgent(boolean requireUserAgent) { this.requireUserAgent = requireUserAgent; }
        
        public boolean isValidateContentType() { return validateContentType; }
        public void setValidateContentType(boolean validateContentType) { this.validateContentType = validateContentType; }
    }
}
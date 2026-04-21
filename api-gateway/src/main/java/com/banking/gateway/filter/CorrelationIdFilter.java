package com.banking.gateway.filter;

import com.banking.common.AppConstants;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdFilter implements WebFilter {

    private static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(AppConstants.Headers.CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String finalCorrelationId = correlationId;
        exchange.getResponse().getHeaders().set(AppConstants.Headers.CORRELATION_ID, finalCorrelationId);
        MDC.put(MDC_KEY, finalCorrelationId);
        return chain.filter(exchange).doFinally(s -> MDC.remove(MDC_KEY));
    }
}

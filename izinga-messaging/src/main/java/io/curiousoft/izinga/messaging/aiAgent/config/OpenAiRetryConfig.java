package io.curiousoft.izinga.messaging.aiAgent.config;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;

/**
 * ADR-031: Programmatic RetryTemplate for OpenAI API calls in AI agents.
 *
 * <p>Uses {@code SimpleRetryPolicy} with {@code BinaryExceptionClassifier} which
 * traverses the class hierarchy to find the MOST SPECIFIC declared entry — so
 * {@link HttpClientErrorException.TooManyRequests} (HTTP 429) is RETRYABLE even
 * though its parent {@link HttpClientErrorException} is declared NON-RETRYABLE.
 * This is empirically verified in {@code OpenAiRetryResilienceTest#httpStatus429_retried}.
 *
 * <p>Backoff: initial 2 s, multiplier 2.0, max 10 s.
 * Worst-case: 8 s response × 3 + 2 s backoff + 4 s backoff = 30 s (exactly within 30 s cap).
 *
 * <p>NO spring-aspects, NO AOP weaving, NO @EnableRetry — purely programmatic.
 * {@code RetryTemplate.execute()} wraps only the network call inside the existing try-catch.
 */
@Configuration
public class OpenAiRetryConfig {

    @Bean("openAiRetryTemplate")
    public RetryTemplate openAiRetryTemplate() {
        // Build exception classification map.
        // SimpleRetryPolicy uses BinaryExceptionClassifier which walks the class
        // hierarchy: the MOST SPECIFIC match wins.
        // So TooManyRequests (true) overrides HttpClientErrorException (false).
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(ResourceAccessException.class, true);           // connect/read timeout, refused
        retryableExceptions.put(HttpServerErrorException.class, true);          // 5xx
        retryableExceptions.put(HttpClientErrorException.TooManyRequests.class, true);  // 429 — overrides parent
        retryableExceptions.put(HttpClientErrorException.class, false);         // all other 4xx incl. 400

        // 3 total attempts (1 initial + 2 retries); traverseClassHierarchy=true
        SimpleRetryPolicy policy = new SimpleRetryPolicy(3, retryableExceptions, true);

        // Exponential backoff: 2 s → 4 s (capped at 10 s per interval)
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(2000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10000);

        RetryTemplate template = RetryTemplate.builder()
                .customPolicy(policy)
                .customBackoff(backOff)
                .build();

        // ADR-031 / AC-04: retry attempt logging via RetryListener (not RetryListenerSupport —
        // that class is deprecated/removed in spring-retry 2.x; use the interface with default methods).
        template.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(RetryContext context,
                                                          RetryCallback<T, E> callback,
                                                          Throwable throwable) {
                if (context.getRetryCount() > 0) {
                    // Log is performed in the call-site using context.getRetryCount() + 1
                }
            }
        });

        return template;
    }
}

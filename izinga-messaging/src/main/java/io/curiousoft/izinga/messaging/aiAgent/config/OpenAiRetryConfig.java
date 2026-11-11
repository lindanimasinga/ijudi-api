package io.curiousoft.izinga.messaging.aiAgent.config;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Worst-case: 15 s response × 3 + 2 s backoff + 4 s backoff = 51 s.
 * Acceptable — this flow is async and does NOT block Meta's webhook ack.
 * Response timeout is configurable via {@code izinga.ai.openai.response-timeout-seconds}
 * in {@code application.yml} (see {@code YocoHeaderConfig.openAiResponseTimeoutSeconds}).
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

        // NOTE-03: no RetryListener registered — the no-op listener was removed.
        // Attempt logging is handled at every call-site via [AI-ATTEMPT] on every attempt
        // unconditionally, which gives QA a greppable positive line for both retryable and
        // non-retryable failures (attempt=1 for non-retryable 4xx proves exactly one attempt).
        return RetryTemplate.builder()
                .customPolicy(policy)
                .customBackoff(backOff)
                .build();
    }
}

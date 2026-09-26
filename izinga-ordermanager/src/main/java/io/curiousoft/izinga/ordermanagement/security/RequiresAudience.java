package io.curiousoft.izinga.ordermanagement.security;

import io.curiousoft.izinga.messaging.whatsapp.lines.Audience;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an MCP {@code @Tool} method that requires a specific audience claim in the scope token.
 *
 * SA-021-15 / NOTE-02: {@code value()} is typed as {@code Audience[]} for compile-time safety,
 * consistent with the ADR-021 Decision 1 Extension specification.
 *
 * SA-021-18: Any future store-modification tool MUST carry
 * {@code @RequiresAudience(Audience.STORE)}.
 * Store-read tools that are already exposed to all audiences do not need this annotation.
 *
 * The {@link StoreScopeValidator} enforces the constraint when {@code value} is non-empty.
 *
 * Example:
 * <pre>
 *   {@literal @}Tool(name = "update_store_hours")
 *   {@literal @}RequiresAudience(Audience.STORE)
 *   public String updateStoreHours(String storeId, ...) {
 *       StoreScopeValidator.validateAudience(Audience.STORE);
 *       StoreScopeValidator.validate(storeId);
 *       ...
 *   }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAudience {

    /**
     * Required audience value(s). If any match the audience in the scope token, the call proceeds.
     * An empty array means no audience restriction.
     * SA-021-15: typed as {@code Audience[]} for compile-time safety.
     */
    Audience[] value() default {};
}

package io.curiousoft.izinga.ordermanagement.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an MCP {@code @Tool} method that requires a specific audience claim in the scope token.
 *
 * SA-021-18: Any future store-modification tool MUST carry {@code @RequiresAudience("STORE")}.
 * Store-read tools that are already exposed to all audiences do not need this annotation.
 *
 * The {@link StoreScopeValidator} enforces the constraint when {@code value} is non-empty.
 *
 * Example:
 * <pre>
 *   {@literal @}Tool(name = "update_store_hours")
 *   {@literal @}RequiresAudience("STORE")
 *   public String updateStoreHours(String storeId, ...) {
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
     */
    String[] value() default {};
}

package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import org.eclipse.microprofile.config.ConfigProvider;

import io.smallrye.common.expression.Expression;

/**
 * Resolves {@code ${config.property.name}} /
 * {@code ${config.property.name:default}}
 * MicroProfile Config property expressions embedded in
 * {@link OnNextcloudEvent#filter()} values.
 *
 */
public final class FilterExpressionResolver {

    private FilterExpressionResolver() {
    }

    /**
     * Expands any {@code ${...}} property expressions in {@code raw} against the
     * current MicroProfile {@link org.eclipse.microprofile.config.Config}.
     *
     * @param raw the raw filter expression, as declared on
     *            {@link OnNextcloudEvent#filter()}
     * @return {@code raw} with all property expressions resolved
     * @throws IllegalStateException if a referenced config property has neither a
     *                               configured value nor a default
     */
    public static String resolve(String raw) {
        if (raw == null || raw.indexOf("${") < 0) {
            return raw;
        }
        return Expression.compile(raw, Expression.Flag.LENIENT_SYNTAX, Expression.Flag.ESCAPES)
                .evaluate((context, builder) -> {
                    String key = context.getKey();
                    String value = ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(null);
                    if (value != null) {
                        builder.append(value);
                    } else if (context.hasDefault()) {
                        context.expandDefault();
                    } else {
                        throw new IllegalStateException(
                                "Missing config property '" + key
                                        + "' referenced in @OnNextcloudEvent filter expression: " + raw);
                    }
                });
    }
}

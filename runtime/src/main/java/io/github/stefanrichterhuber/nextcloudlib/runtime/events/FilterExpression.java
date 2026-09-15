package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterExpression {
    /**
     * A filter expression to e.g.: {@code "{ "user.uid": "admin" }" }. Supports
     * property
     * expressions like
     * {@code  "{ \"user.uid\": \"${nextcloud.filter.admin-uid}\" }" }
     * 
     * @return
     */
    @Nonbinding
    String value();
}

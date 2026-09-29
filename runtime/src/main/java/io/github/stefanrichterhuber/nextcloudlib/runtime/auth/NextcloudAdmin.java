package io.github.stefanrichterhuber.nextcloudlib.runtime.auth;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

/**
 * Qualifier for all credentials / services that require admin privileges on
 * the Nextcloud instance. This is used to distinguish between the "admin"
 * credentials and "normal" credentials, which might have different permissions
 * on the Nextcloud instance. This is especially relevant for the Dev Services
 * support, where the generated credentials have admin privileges, but the user
 * might want to provide their own credentials with lower privileges for the
 * application to use
 */
@Qualifier
@Retention(RUNTIME)
@Target({ METHOD, FIELD, PARAMETER, TYPE })
public @interface NextcloudAdmin {

    /**
     * Annotation literal for programmatic CDI injection
     */
    public static final class Literal extends AnnotationLiteral<NextcloudAdmin> {
        private static final long serialVersionUID = 1L;

        private static final Literal INSTANCE = new Literal();

        private Literal() {
        }

        /**
         * @return the shared literal instance
         */
        public static Literal instance() {
            return INSTANCE;
        }
    }
}

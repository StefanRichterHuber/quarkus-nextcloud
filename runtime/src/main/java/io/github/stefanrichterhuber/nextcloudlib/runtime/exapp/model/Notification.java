package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model;

import java.util.HashMap;
import java.util.Map;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model.NotificationRequest.Params.SubjectParams.Parameter;

/**
 * Builder for constructing a {@link NotificationRequest} to be sent to a
 * Nextcloud user via the AppAPI notifications endpoint.
 *
 * @see <a href="https://cloud-py-api.github.io/app_api/tech_details/api/notifications.html#params">Notifications API</a>
 */
public class Notification {
    /**
     * Required but not used yet, use default
     */
    private final String object = "app_api";
    /**
     * Required but not used yet
     */
    private final String objectId = "app_api_id"; // UUID.randomUUID().toString();
    /**
     * Required but not used yet, set default
     */
    private final String subjectType = "app_api_ex_app";
    private String subject = "";
    private Map<String, Parameter<?>> subjectParameter = new HashMap<>();
    private String message = "";
    private Map<String, Parameter<?>> messageParameter = new HashMap<>();
    private String link = null;

    /**
     * Creates a new empty {@code Notification} builder.
     *
     * @return a new builder instance
     */
    public static Notification create() {
        return new Notification();
    }

    /**
     * Sets an optional deep link shown alongside the notification.
     *
     * @param link the URL to navigate to when the notification is clicked
     * @return this builder for chaining
     */
    public Notification withLink(String link) {
        this.link = link;
        return this;
    }

    /**
     * Sets a plain-text subject with no rich-text parameters.
     *
     * @param subject the notification subject text
     * @return this builder for chaining
     */
    public Notification withSubject(String subject) {
        this.subject = subject;
        this.subjectParameter = new HashMap<>();
        return this;
    }

    /**
     * Sets a rich-text subject with named parameters for inline substitution.
     *
     * @param subject    the rich-text subject template (e.g. {@code "Hello {user}"})
     * @param parameters map of parameter name to {@link Parameter} value
     * @return this builder for chaining
     */
    public Notification withSubject(String subject, Map<String, Parameter<?>> parameters) {
        this.subject = subject;
        this.subjectParameter = parameters;
        return this;
    }

    /**
     * Sets a plain-text message body with no rich-text parameters.
     *
     * @param message the notification message text
     * @return this builder for chaining
     */
    public Notification withMessage(String message) {
        this.message = message;
        this.messageParameter = new HashMap<>();
        return this;
    }

    /**
     * Sets a rich-text message body with named parameters for inline substitution.
     *
     * @param message    the rich-text message template (e.g. {@code "File {file} was shared"})
     * @param parameters map of parameter name to {@link Parameter} value
     * @return this builder for chaining
     */
    public Notification withMessage(String message, Map<String, Parameter<?>> parameters) {
        this.message = message;
        this.messageParameter = parameters;
        return this;
    }

    /**
     * Builds the {@link NotificationRequest} payload ready to be sent to the
     * AppAPI notifications endpoint.
     *
     * @return a new immutable {@link NotificationRequest}
     */
    public NotificationRequest toRequest() {
        return new NotificationRequest(
                new NotificationRequest.Params(object, objectId,
                        subjectType,
                        new NotificationRequest.Params.SubjectParams(
                                subject,
                                subjectParameter,
                                message,
                                messageParameter, link)));
    }

    @Override
    public String toString() {
        return toRequest().params().subject_params().toString();
    }

}

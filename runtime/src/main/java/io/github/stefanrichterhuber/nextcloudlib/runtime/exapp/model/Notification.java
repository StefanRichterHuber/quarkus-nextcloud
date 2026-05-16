package io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model;

import java.util.HashMap;
import java.util.Map;

import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.model.NotificationRequest.Params.SubjectParams.Parameter;

/**
 * @see https://cloud-py-api.github.io/app_api/tech_details/api/notifications.html#params
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

    public static Notification create() {
        return new Notification();
    }

    public Notification withLink(String link) {
        this.link = link;
        return this;
    }

    /**
     * Set a simple subject without parameter
     * 
     * @param subject
     * @return
     */
    public Notification withSubject(String subject) {
        this.subject = subject;
        this.subjectParameter = new HashMap<>();
        return this;
    }

    /**
     * Set a subject with parameter
     * 
     * @param subject
     * @return
     */
    public Notification withSubject(String subject, Map<String, Parameter<?>> parameters) {
        this.subject = subject;
        this.subjectParameter = parameters;
        return this;
    }

    /**
     * Set a simple message without parameter
     * 
     * @param message
     * @return
     */
    public Notification withMessage(String message) {
        this.message = message;
        this.messageParameter = new HashMap<>();
        return this;
    }

    /**
     * Set a message with parameter
     * 
     * @param message
     * @return
     */
    public Notification withMessage(String message, Map<String, Parameter<?>> parameters) {
        this.message = message;
        this.messageParameter = parameters;
        return this;
    }

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

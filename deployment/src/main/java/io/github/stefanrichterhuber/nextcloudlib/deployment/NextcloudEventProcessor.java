package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.HandlerDescriptor;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudEventDispatcher;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudEventInvoker;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudEventRecorder;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudWebhookBuildConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudWebhookRegistrar;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudWebhookSecretHolder;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import jakarta.inject.Singleton;

/**
 * Build steps that enable the Nextcloud event-handling feature.
 *
 * <h2>What happens at build time</h2>
 * <ol>
 *   <li>{@link #discoverEventHandlers} scans the application's Jandex index for
 *       all methods annotated with {@link OnNextcloudEvent}, validates their
 *       signatures, and emits one {@link NextcloudEventHandlerBuildItem} per
 *       handler.</li>
 *   <li>{@link #setupEventHandling} is only executed when at least one handler
 *       was found. It:
 *       <ul>
 *         <li>generates a Gizmo {@link NextcloudEventInvoker} implementation per handler
 *             method so dispatch requires no reflection at runtime;</li>
 *         <li>registers {@link NextcloudWebhookRegistrar} and
 *             {@link NextcloudWebhookSecretHolder} as unremovable CDI beans;</li>
 *         <li>uses {@link NextcloudEventRecorder} to create a
 *             {@link NextcloudEventDispatcher} synthetic {@code @Singleton} CDI
 *             bean pre-loaded with all handler descriptors;</li>
 *         <li>registers the declaring classes for reflection for native builds.</li>
 *       </ul>
 *   </li>
 *   <li>{@link #registerWebhookRoute} registers a Vert.x route at the path
 *       configured via {@code nextcloud.webhook.path} (default {@code /webhook}).
 *   </li>
 * </ol>
 *
 * <p>
 * When no {@link OnNextcloudEvent} methods are present the feature is a
 * complete no-op: no extra beans, no route, no required config.
 * </p>
 */
class NextcloudEventProcessor {

    private static final Logger LOG = Logger.getLogger(NextcloudEventProcessor.class);

    private static final DotName ON_NEXTCLOUD_EVENT =
            DotName.createSimple(OnNextcloudEvent.class.getName());
    private static final DotName NEXTCLOUD_EVENT =
            DotName.createSimple(NextcloudEvent.class.getName());

    /**
     * Scans the combined Jandex index for {@link OnNextcloudEvent}-annotated
     * methods and produces one {@link NextcloudEventHandlerBuildItem} per valid
     * handler.
     *
     * <p>Validation rules (build fails fast if violated):</p>
     * <ul>
     *   <li>Annotation must be on a method.</li>
     *   <li>Method must have exactly one parameter of type {@link NextcloudEvent}.</li>
     *   <li>At least one event class name must be specified in {@code value()}.</li>
     * </ul>
     */
    @BuildStep
    void discoverEventHandlers(
            CombinedIndexBuildItem index,
            BuildProducer<NextcloudEventHandlerBuildItem> handlers) {

        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_NEXTCLOUD_EVENT)) {
            if (ann.target().kind() != Kind.METHOD) {
                continue;
            }

            MethodInfo method = ann.target().asMethod();
            String location = method.declaringClass().name() + "#" + method.name();

            if (method.parametersCount() != 1) {
                throw new IllegalStateException(
                        "@OnNextcloudEvent method " + location
                                + " must have exactly one parameter of type NextcloudEvent");
            }

            Type paramType = method.parameterType(0);
            if (!paramType.name().equals(NEXTCLOUD_EVENT)) {
                throw new IllegalStateException(
                        "@OnNextcloudEvent method " + location
                                + " parameter must be NextcloudEvent, found: " + paramType.name());
            }

            String[] eventClassNames = ann.value().asStringArray();
            if (eventClassNames == null || eventClassNames.length == 0) {
                throw new IllegalStateException(
                        "@OnNextcloudEvent method " + location
                                + " must specify at least one event class name in value()");
            }

            List<String> eventList = Arrays.asList(eventClassNames);
            LOG.debugf("Discovered @OnNextcloudEvent handler: %s -> %s", location, eventList);

            handlers.produce(new NextcloudEventHandlerBuildItem(
                    method.declaringClass().name().toString(),
                    method.name(),
                    eventList));
        }
    }

    /**
     * Conditionally sets up the full event-handling CDI stack when at least one
     * handler was discovered. Generates a Gizmo {@link NextcloudEventInvoker} class
     * per handler so dispatch is direct method invocation, not reflection.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void setupEventHandling(
            List<NextcloudEventHandlerBuildItem> handlers,
            NextcloudEventRecorder recorder,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        if (handlers.isEmpty()) {
            return;
        }

        // Register the startup registrar and secret holder as unremovable CDI beans
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(NextcloudWebhookRegistrar.class)
                .addBeanClass(NextcloudWebhookSecretHolder.class)
                .setUnremovable()
                .build());

        ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClasses, true);

        List<HandlerDescriptor> descriptors = handlers.stream()
                .map(h -> {
                    String invokerClassName = generateInvoker(
                            classOutput, h.getDeclaringClassName(), h.getMethodName());
                    return new HandlerDescriptor(
                            h.getDeclaringClassName(),
                            invokerClassName,
                            h.getEventClassNames());
                })
                .collect(Collectors.toList());

        syntheticBeans.produce(SyntheticBeanBuildItem
                .configure(NextcloudEventDispatcher.class)
                .scope(Singleton.class)
                .unremovable()
                .runtimeValue(recorder.createDispatcher(descriptors))
                .done());

        handlers.stream()
                .map(NextcloudEventHandlerBuildItem::getDeclaringClassName)
                .distinct()
                .forEach(cls -> reflectiveClasses.produce(
                        ReflectiveClassBuildItem.builder(cls)
                                .methods(true)
                                .build()));
    }

    /**
     * Generates a {@link NextcloudEventInvoker} implementation that casts {@code bean}
     * to {@code declaringClassName} and calls {@code methodName(NextcloudEvent)} directly.
     *
     * @return the fully-qualified name of the generated invoker class
     */
    private static String generateInvoker(ClassOutput classOutput, String declaringClassName,
            String methodName) {
        String invokerClassName = declaringClassName + "_" + methodName + "_NCInvoker";

        try (ClassCreator cc = ClassCreator.builder()
                .classOutput(classOutput)
                .className(invokerClassName)
                .interfaces(NextcloudEventInvoker.class)
                .build()) {

            MethodCreator mc = cc.getMethodCreator("invoke", void.class, Object.class, NextcloudEvent.class);
            ResultHandle beanHandle = mc.checkCast(mc.getMethodParam(0), declaringClassName);
            mc.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(declaringClassName, methodName,
                            void.class, NextcloudEvent.class),
                    beanHandle,
                    mc.getMethodParam(1));
            mc.returnVoid();
        }

        LOG.debugf("Generated invoker %s for %s#%s", invokerClassName, declaringClassName, methodName);
        return invokerClassName;
    }

    /**
     * Registers a Vert.x route at {@code nextcloud.webhook.path} (default
     * {@code /webhook}) that forwards POST requests to the
     * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.NextcloudWebhookHandler}.
     * Only produced when at least one {@link OnNextcloudEvent} handler exists.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerWebhookRoute(
            List<NextcloudEventHandlerBuildItem> handlers,
            NextcloudWebhookBuildConfig buildConfig,
            HttpRootPathBuildItem httpRoot,
            NextcloudEventRecorder recorder,
            BuildProducer<RouteBuildItem> routes) {

        if (handlers.isEmpty()) {
            return;
        }

        LOG.debugf("Registering Nextcloud webhook Vert.x route at: %s", buildConfig.path());

        routes.produce(httpRoot.routeBuilder()
                .route(buildConfig.path())
                .handler(recorder.createWebhookHandler())
                .displayOnNotFoundPage("Nextcloud Webhook")
                .build());
    }
}

package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.List;
import java.util.Optional;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.DefaultNextcloudEventDispatcher;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudEventInvoker;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookBuildConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookRecorder;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookStartupRegistrar;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.events.NextcloudWebhookEnabledRegistrar;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookRegistrationService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookSecretHolder;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Quarkus build-time processor that wires the Nextcloud event-handling
 * infrastructure.
 *
 * <p>
 * Responsibilities:
 * </p>
 * <ul>
 * <li>Scans the Jandex index for {@link OnNextcloudEvent}-annotated
 * methods.</li>
 * <li>Generates a {@link NextcloudEventInvoker} implementation per handler via
 * Gizmo so dispatch is a direct method call, not reflection.</li>
 * <li>Registers all required CDI beans, including the declaring classes of
 * handler methods, so that plain POJOs are automatically promoted to
 * {@link ApplicationScoped} beans.</li>
 * <li>Registers the Vert.x webhook route at build time.</li>
 * </ul>
 */
class NextcloudEventProcessor {

    private static final Logger LOG = Logger.getLogger(NextcloudEventProcessor.class);

    private static final DotName ON_NEXTCLOUD_EVENT = DotName.createSimple(OnNextcloudEvent.class.getName());
    private static final DotName NEXTCLOUD_EVENT = DotName.createSimple(NextcloudEvent.class.getName());

    /**
     * Scans the combined Jandex index for {@link OnNextcloudEvent}-annotated
     * methods and produces one {@link NextcloudEventHandlerBuildItem} per valid
     * handler.
     *
     * <p>
     * Validation rules (build fails fast if violated):
     * </p>
     * <ul>
     * <li>Annotation must be on a method.</li>
     * <li>Method must have exactly one parameter of type
     * {@link NextcloudEvent}.</li>
     * <li>At least one event class name must be specified in {@code events()}.</li>
     * </ul>
     *
     * @param index    the combined Jandex index provided by Quarkus
     * @param handlers build item producer for discovered handler descriptors
     */
    @BuildStep
    void discoverEventHandlers(
            CombinedIndexBuildItem index,
            BuildProducer<NextcloudEventHandlerBuildItem> handlers) {

        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_NEXTCLOUD_EVENT)) {
            if (ann.target().kind() != Kind.METHOD) {
                continue;
            }

            final MethodInfo method = ann.target().asMethod();
            final String location = method.declaringClass().name() + "#" + method.name();

            if (method.parametersCount() != 1) {
                throw new IllegalStateException(
                        "@OnNextcloudEvent method " + location
                                + " must have exactly one parameter of type NextcloudEvent");
            }

            final Type paramType = method.parameterType(0);
            if (!paramType.name().equals(NEXTCLOUD_EVENT)) {
                throw new IllegalStateException(
                        "@OnNextcloudEvent method " + location
                                + " parameter must be NextcloudEvent, found: " + paramType.name());
            }

            if (method.returnType().kind() != Type.Kind.VOID) {
                throw new IllegalStateException(
                        "@OnNextcloudEvent method " + location + " must return void");
            }

            final String[] eventClassNames = ann.value("events").asStringArray();
            if (eventClassNames == null || eventClassNames.length == 0) {
                throw new IllegalStateException(
                        "@OnNextcloudEvent method " + location
                                + " must specify at least one event class name in value()");
            }

            final boolean tokenNeeded = Optional.ofNullable(ann.value("tokenNeeded")).map(av -> av.asBoolean())
                    .orElse(false);
            final boolean provideAuth = Optional.ofNullable(ann.value("provideAuth")).map(av -> av.asBoolean())
                    .orElse(false);

            LOG.debugf("Discovered @OnNextcloudEvent handler: %s -> %s", location, eventClassNames);

            handlers.produce(new NextcloudEventHandlerBuildItem(
                    method.declaringClass().name().toString(),
                    method.name(),
                    eventClassNames, tokenNeeded, provideAuth));
        }
    }

    /**
     * Conditionally sets up the full event-handling CDI stack when at least one
     * handler was discovered. Generates a Gizmo {@link NextcloudEventInvoker}
     * class per handler so dispatch is a direct method invocation, not reflection.
     *
     * <p>
     * The declaring class of each handler method is registered as an additional
     * CDI bean with {@link ApplicationScoped} as the default scope. This ensures
     * that plain POJOs (without an explicit scope annotation) are automatically
     * promoted to CDI beans and do not require manual registration.
     * </p>
     *
     * @param handlers          discovered handler descriptors from
     *                          {@link #discoverEventHandlers}
     * @param additionalBeans   producer for additional CDI bean registrations
     * @param syntheticBeans    producer for synthetic CDI bean build items
     *                          (reserved for future use)
     * @param generatedClasses  producer for generated class build items (reserved
     *                          for future use)
     * @param generatedBeans    producer for generated bean build items used by
     *                          Gizmo
     * @param reflectiveClasses producer for native-image reflective class
     *                          registrations (reserved for future use)
     */
    @BuildStep
    void setupEventHandling(
            List<NextcloudEventHandlerBuildItem> handlers,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        if (handlers.isEmpty()) {
            return;
        }

        // Register each handler's declaring class as a CDI bean.
        // setDefaultScope promotes plain POJOs to @ApplicationScoped when no
        // explicit scope annotation is present on the class.
        DotName appScoped = DotName.createSimple(ApplicationScoped.class.getName());
        for (NextcloudEventHandlerBuildItem handler : handlers) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(handler.getDeclaringClassName())
                    .setDefaultScope(appScoped)
                    .setUnremovable()
                    .build());
        }

        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeans);

        for (NextcloudEventHandlerBuildItem handler : handlers) {
            generateInvoker(classOutput, handler.getDeclaringClassName(),
                    handler.getMethodName(),
                    handler.getEventClassNames(), handler.isTokenNeeded(), handler.isProvideAuth());
        }
    }

    /**
     * Registers the shared event-infrastructure CDI beans
     * ({@link DefaultNextcloudEventDispatcher}, {@link NextcloudWebhookRegistrationService},
     * {@link NextcloudWebhookSecretHolder}) when at least one handler was discovered.
     *
     * @param handlers        discovered handler descriptors; beans are skipped when empty
     * @param additionalBeans producer for CDI bean registrations
     */
    @BuildStep
    void setupEventBeans(
            List<NextcloudEventHandlerBuildItem> handlers,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        if (handlers.isEmpty()) {
            return;
        }

        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(NextcloudWebhookSecretHolder.class)
                .addBeanClass(DefaultNextcloudEventDispatcher.class)
                .addBeanClass(NextcloudWebhookRegistrationService.class)
                .setUnremovable()
                .build());
    }

    /**
     * Registers {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookStartupRegistrar}
     * as a CDI bean when the application is <em>not</em> running as an ExApp. This registrar
     * registers webhooks against Nextcloud using admin credentials on startup.
     *
     * @param handlers        discovered handler descriptors; bean is skipped when empty
     * @param additionalBeans producer for CDI bean registrations
     */
    @BuildStep(onlyIfNot = IsExApp.class)
    void setupEventRegistrationBeans(
            List<NextcloudEventHandlerBuildItem> handlers,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        if (handlers.isEmpty()) {
            return;
        }

        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(NextcloudWebhookStartupRegistrar.class)
                .setUnremovable()
                .build());
    }

    /**
     * Registers {@link io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.impl.events.NextcloudWebhookEnabledRegistrar}
     * as a CDI bean when the application is running as an ExApp. This registrar registers
     * webhooks via the ExApp-specific lifecycle hooks rather than admin credentials.
     *
     * @param handlers        discovered handler descriptors; bean is skipped when empty
     * @param additionalBeans producer for CDI bean registrations
     */
    @BuildStep(onlyIf = IsExApp.class)
    void setupEventExappRegistrationBeans(
            List<NextcloudEventHandlerBuildItem> handlers,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        if (handlers.isEmpty()) {
            return;
        }

        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(NextcloudWebhookEnabledRegistrar.class)
                .setUnremovable()
                .build());
    }

    /**
     * Generates a {@link NextcloudEventInvoker} implementation for a single
     * {@link OnNextcloudEvent}-annotated method.
     *
     * <p>
     * The generated class holds the handler bean via an {@code @Inject} field and
     * invokes the target method directly (no reflection). CDI manages the bean
     * lifecycle through normal injection semantics.
     * </p>
     *
     * @param classOutput        Gizmo output to write the generated class bytes to
     * @param declaringClassName fully-qualified name of the class that declares the
     *                           handler method
     * @param methodName         name of the handler method
     * @param events             Nextcloud PHP event class names the handler listens
     *                           for
     * @param tokenNeeded        whether a temporary auth token should be requested
     *                           from Nextcloud
     * @param provideAuth        whether a {@code NextcloudAuthProvider} should be
     *                           made available in the request context
     * @return the fully-qualified name of the generated invoker class
     */
    private static String generateInvoker(ClassOutput classOutput, String declaringClassName,
            String methodName, String[] events, boolean tokenNeeded, boolean provideAuth) {
        String invokerClassName = declaringClassName + "_" + methodName + "_NCInvoker";

        try (ClassCreator cc = ClassCreator.builder()
                .classOutput(classOutput)
                .className(invokerClassName)
                .interfaces(NextcloudEventInvoker.class)
                .build()) {

            cc.addAnnotation(ApplicationScoped.class);
            buildInvokeMethod(cc, declaringClassName, methodName);
            buildEventsMethod(cc, events);
            buildRequestAuthTokenMethod(cc, tokenNeeded, provideAuth);
            buildProvideAuthProviderMethod(cc, tokenNeeded, provideAuth);
        }

        LOG.debugf("Generated invoker %s for %s#%s", invokerClassName, declaringClassName, methodName);
        return invokerClassName;
    }

    /**
     * Generates the {@code invoke(NextcloudEvent)} method body for the given
     * declaring class and handler method name.
     *
     * <p>
     * The generated bytecode reads an {@code @Inject}-ed delegate field and invokes
     * the target method directly. CDI manages the delegate's lifecycle through
     * normal injection semantics.
     * </p>
     *
     * @param cc                 class creator for the invoker being generated
     * @param declaringClassName fully-qualified name of the handler bean class
     * @param methodName         name of the {@link OnNextcloudEvent}-annotated
     *                           method
     */
    private static void buildInvokeMethod(ClassCreator cc, String declaringClassName, String methodName) {
        FieldCreator fc = cc.getFieldCreator("delegate", declaringClassName);
        fc.setModifiers(0); // package-private: ArC's generated _Bean class accesses this field via direct
                            // bytecode
        fc.addAnnotation(Inject.class);

        MethodCreator mc = cc.getMethodCreator("invoke", void.class, NextcloudEvent.class);

        ResultHandle bean = mc.readInstanceField(fc.getFieldDescriptor(), mc.getThis());

        mc.invokeVirtualMethod(
                MethodDescriptor.ofMethod(declaringClassName, methodName, void.class, NextcloudEvent.class),
                bean,
                mc.getMethodParam(0));

        mc.returnVoid();
    }

    /**
     * Generates the {@code events()} method that returns the array of Nextcloud
     * PHP event class names the handler is subscribed to.
     *
     * @param cc     class creator for the invoker being generated
     * @param events array of fully-qualified Nextcloud PHP event class names
     */
    private static void buildEventsMethod(ClassCreator cc, String[] events) {
        MethodCreator mc = cc.getMethodCreator("events", String[].class);
        ResultHandle array = mc.newArray(String.class, mc.load(events.length));
        for (int i = 0; i < events.length; i++) {
            mc.writeArrayValue(array, i, mc.load(events[i]));
        }
        mc.returnValue(array);
    }

    /**
     * Generates the {@code requestAuthToken()} method, which returns {@code true}
     * when a temporary Nextcloud auth token must be requested for the triggering
     * user before dispatching the event.
     *
     * <p>
     * Returns {@code true} when either {@code tokenNeeded} or {@code provideAuth}
     * is set, because providing an auth provider implicitly requires the token.
     * </p>
     *
     * @param cc          class creator for the invoker being generated
     * @param tokenNeeded value of the {@code tokenNeeded} annotation attribute
     * @param provideAuth value of the {@code provideAuth} annotation attribute
     */
    private static void buildRequestAuthTokenMethod(ClassCreator cc, boolean tokenNeeded, boolean provideAuth) {
        MethodCreator mc = cc.getMethodCreator("requestAuthToken", boolean.class);
        mc.returnBoolean(tokenNeeded || provideAuth);
    }

    /**
     * Generates the {@code provideAuthProvider()} method, which returns
     * {@code true}
     * when a {@code NextcloudAuthProvider} instance carrying the triggering user's
     * temporary token should be placed in the request context before the handler
     * is called.
     *
     * @param cc          class creator for the invoker being generated
     * @param tokenNeeded value of the {@code tokenNeeded} annotation attribute
     * @param provideAuth value of the {@code provideAuth} annotation attribute
     */
    private static void buildProvideAuthProviderMethod(ClassCreator cc, boolean tokenNeeded, boolean provideAuth) {
        MethodCreator mc = cc.getMethodCreator("provideAuthProvider", boolean.class);
        mc.returnBoolean(provideAuth);
    }

    /**
     * Registers a Vert.x route at {@code nextcloud.webhook.path} (default
     * {@code /webhook}) that forwards POST requests to
     * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookHandler}.
     * Only produced when at least one {@link OnNextcloudEvent} handler exists.
     *
     * @param recorder    Quarkus recorder used to create the handler instance at
     *                    runtime
     * @param handlers    discovered handler descriptors; route is skipped when
     *                    empty
     * @param buildConfig webhook configuration providing the mount path
     * @param httpRoot    Quarkus HTTP root path build item for route registration
     * @param routes      producer for Vert.x route build items
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerWebhookRoute(
            NextcloudWebhookRecorder recorder,
            List<NextcloudEventHandlerBuildItem> handlers,
            NextcloudWebhookBuildConfig buildConfig,
            HttpRootPathBuildItem httpRoot,
            BuildProducer<RouteBuildItem> routes) {

        if (handlers.isEmpty()) {
            return;
        }

        LOG.infof("Registering Nextcloud event webhook Vert.x route at: %s", buildConfig.path());

        routes.produce(httpRoot.routeBuilder()
                .route(buildConfig.path())
                .handler(recorder.webhookHandler())
                .displayOnNotFoundPage("Nextcloud Webhook")
                .build());
    }
}

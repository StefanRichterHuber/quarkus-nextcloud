package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudEventDispatcher;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudEventInvoker;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookBuildConfig;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookRecorder;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookRegistrar;
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookSecretHolder;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableInstance;
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
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

class NextcloudEventProcessor {

    private static final Logger LOG = Logger.getLogger(NextcloudEventProcessor.class);

    private static final DotName ON_NEXTCLOUD_EVENT = DotName.createSimple(OnNextcloudEvent.class.getName());
    private static final DotName NEXTCLOUD_EVENT = DotName.createSimple(NextcloudEvent.class.getName());
    private static final DotName SINGLETON_SCOPE = DotName.createSimple(Singleton.class);

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
     * <li>At least one event class name must be specified in {@code value()}.</li>
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
     * handler was discovered. Generates a Gizmo {@link NextcloudEventInvoker} class
     * per handler so dispatch is direct method invocation, not reflection.
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

        // Register the startup registrar and secret holder as unremovable CDI beans
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(NextcloudWebhookRegistrar.class)
                .addBeanClass(NextcloudWebhookSecretHolder.class)
                .addBeanClass(NextcloudEventDispatcher.class)
                .setUnremovable()
                .build());

        // ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClasses,
        // true);
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeans);

        for (NextcloudEventHandlerBuildItem handler : handlers) {
            generateInvoker(classOutput, handler.getDeclaringClassName(),
                    handler.getMethodName(),
                    handler.getEventClassNames(), handler.isTokenNeeded(), handler.isProvideAuth());
        }
    }

    /**
     * Generates a {@link NextcloudEventInvoker} implementation that casts
     * {@code bean}
     * to {@code declaringClassName} and calls {@code methodName(NextcloudEvent)}
     * directly.
     *
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

    private static void buildInvokeMethod(ClassCreator cc, String declaringClassName, String methodName) {
        MethodCreator mc = cc.getMethodCreator("invoke", void.class, NextcloudEvent.class);

        // 1. Get the Arc Container
        MethodDescriptor getContainer = MethodDescriptor.ofMethod(Arc.class, "container", ArcContainer.class);
        ResultHandle container = mc.invokeStaticMethod(getContainer);

        // 2. Select the bean (returns an InstanceHandle)
        MethodDescriptor selectMethod = MethodDescriptor.ofMethod(
                ArcContainer.class,
                "select",
                InjectableInstance.class,
                Class.class,
                Annotation[].class);
        ResultHandle instanceHandle = mc.invokeInterfaceMethod(
                selectMethod,
                container,
                mc.loadClassFromTCCL(declaringClassName),
                mc.newArray(Annotation.class, 0));

        // 3. Retrieve the actual instance from the handle
        MethodDescriptor getBeanMethod = MethodDescriptor.ofMethod(
                InjectableInstance.class,
                "get",
                Object.class);
        ResultHandle invoker = mc.invokeInterfaceMethod(getBeanMethod, instanceHandle);

        // 4. Invoke the method on the instance with the OnNextcloudEvent annotation
        MethodDescriptor invokeMethod = MethodDescriptor.ofMethod(
                declaringClassName,
                methodName,
                void.class,
                NextcloudEvent.class);
        mc.invokeVirtualMethod(invokeMethod, invoker, mc.getMethodParam(0));
        mc.returnVoid();
    }

    /**
     * Builds the 'events()' method of the NextcloudEventInvoker implementation.
     * Just returns a copy of the events array from the annotation
     * 
     * @param cc     ClassCreator to use
     * @param events Array of nextcloud events to return
     */
    private static void buildEventsMethod(ClassCreator cc, String[] events) {
        MethodCreator mc = cc.getMethodCreator("events", String[].class);
        // 1. Create the array handle of size 'values.size()'
        ResultHandle array = mc.newArray(String.class, mc.load(events.length));
        // 2. Populate the array with the string values
        for (int i = 0; i < events.length; i++) {
            // Load the string constant and write it to the specific array index
            mc.writeArrayValue(array, i, mc.load(events[i]));
        }
        mc.returnValue(array);
    }

    private static void buildRequestAuthTokenMethod(ClassCreator cc, boolean tokenNeeded, boolean provideAuth) {
        MethodCreator mc = cc.getMethodCreator("requestAuthToken", boolean.class);
        mc.returnBoolean(tokenNeeded || provideAuth);
    }

    private static void buildProvideAuthProviderMethod(ClassCreator cc, boolean tokenNeeded, boolean provideAuth) {
        MethodCreator mc = cc.getMethodCreator("provideAuthProvider", boolean.class);
        mc.returnBoolean(provideAuth);
    }

    /**
     * Registers a Vert.x route at {@code nextcloud.webhook.path} (default
     * {@code /webhook}) that forwards POST requests to the
     * {@link io.github.stefanrichterhuber.nextcloudlib.runtime.events.impl.NextcloudWebhookHandler}.
     * Only produced when at least one {@link OnNextcloudEvent} handler exists.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerWebhookRoute(
            NextcloudWebhookRecorder recorder, // Inject the recorde
            List<NextcloudEventHandlerBuildItem> handlers,
            NextcloudWebhookBuildConfig buildConfig,
            HttpRootPathBuildItem httpRoot,
            BuildProducer<RouteBuildItem> routes) {

        if (handlers.isEmpty()) {
            return;
        }

        LOG.infof("Registering Nextcloud webhook Vert.x route at: %s", buildConfig.path());

        routes.produce(httpRoot.routeBuilder()
                .route(buildConfig.path())
                .handler(recorder.webhookHandler())
                .displayOnNotFoundPage("Nextcloud Webhook")
                .build());
    }
}

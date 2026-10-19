package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import org.testcontainers.containers.Container.ExecResult;

import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.DevNextcloudEventTracker;
import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.DevNextcloudExAppStateTracker;
import io.github.stefanrichterhuber.nextcloudlib.runtime.devui.NextcloudDevUIJsonRpcService;
import io.github.stefanrichterhuber.nextcloudlib.runtime.exapp.NextcloudExappConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.buildtime.BuildTimeActionBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.CardText;
import io.quarkus.devui.spi.page.Page;

/**
 * Contributes the Nextcloud card to the Quarkus Dev UI: link to the Nextcloud
 * instance, instance information, event inspector with replay, handler
 * overview, occ console and ExApp status.
 *
 * <p>
 * Actions that need the Nextcloud container (occ console, webhook worker,
 * ExApp enable/disable) run here in the deployment module, since only it holds
 * the container started by {@link NextcloudDevServicesResultBuildItem}. They
 * are refused if no dev service container is running, e.g. because an external
 * Nextcloud instance is configured.
 * </p>
 */
class NextcloudDevUIProcessor {

    private static final String NOT_AVAILABLE = "Not available: no Nextcloud dev service container is running "
            + "(an external Nextcloud instance is configured or the container was stopped)";

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    CardPageBuildItem createCard(List<NextcloudEventHandlerBuildItem> handlers, NextcloudExappConfig exAppConfig) {
        final CardPageBuildItem card = new CardPageBuildItem();

        card.addText(CardText.textBuilder()
                .title("Connected to")
                .icon("font-awesome-solid:cloud")
                .dynamicText("getModeText")
                .build());

        card.addPage(Page.externalPageBuilder("Open Nextcloud")
                .icon("font-awesome-solid:arrow-up-right-from-square")
                .dynamicUrlJsonRPCMethodName("getNextcloudUrl")
                .doNotEmbed());

        card.addPage(Page.webComponentPageBuilder()
                .title("Instance")
                .icon("font-awesome-solid:server")
                .componentLink("qwc-nextcloud-instance.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Events")
                .icon("font-awesome-solid:bolt")
                .componentLink("qwc-nextcloud-events.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Handlers")
                .icon("font-awesome-solid:list-check")
                .staticLabel(Integer.toString(handlers.size()))
                .componentLink("qwc-nextcloud-handlers.js"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Tools")
                .icon("font-awesome-solid:terminal")
                .componentLink("qwc-nextcloud-tools.js"));

        if (exAppConfig.enabled()) {
            card.addPage(Page.webComponentPageBuilder()
                    .title("ExApp")
                    .icon("font-awesome-solid:puzzle-piece")
                    .componentLink("qwc-nextcloud-exapp.js"));
        }

        // Maps the invoker class names (as seen at runtime) to the annotated methods
        final Map<String, Map<String, Object>> handlerInfo = new LinkedHashMap<>();
        for (NextcloudEventHandlerBuildItem handler : handlers) {
            final Map<String, Object> info = new LinkedHashMap<>();
            info.put("declaringClass", handler.getDeclaringClassName());
            info.put("method", handler.getMethodName());
            info.put("events", List.of(handler.getEventClassNames()));
            info.put("filter", handler.getFilter());
            info.put("requestAuthToken", handler.isRequestAuthToken());
            handlerInfo.put(handler.getDeclaringClassName() + "_" + handler.getMethodName() + "_NCInvoker", info);
        }
        card.addBuildTimeData("handlerMethods", handlerInfo);
        card.addBuildTimeData("exAppEnabled", exAppConfig.enabled());

        return card;
    }

    /**
     * Registers the trackers recording events and ExApp state for the Dev UI. They
     * override the no-op trackers registered by {@link NextcloudlibProcessor}.
     *
     * @return the additional-bean build item
     */
    @BuildStep(onlyIf = IsLocalDevelopment.class)
    AdditionalBeanBuildItem devTrackers() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(DevNextcloudEventTracker.class)
                .addBeanClass(DevNextcloudExAppStateTracker.class)
                .setUnremovable()
                .build();
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    JsonRPCProvidersBuildItem createJsonRPCService() {
        return new JsonRPCProvidersBuildItem(NextcloudDevUIJsonRpcService.class);
    }

    @BuildStep(onlyIf = IsLocalDevelopment.class)
    BuildTimeActionBuildItem createContainerActions() {
        final BuildTimeActionBuildItem actions = new BuildTimeActionBuildItem();

        actions.actionBuilder()
                .methodName("getContainerStatus")
                .description("Returns if a Nextcloud dev service container is available for occ commands")
                .function(params -> {
                    final Map<String, Object> result = new LinkedHashMap<>();
                    final Optional<NextcloudContainer> container = NextcloudDevServiceState.runningContainer();
                    result.put("available", container.isPresent());
                    result.put("reason", container.isPresent() ? null : NOT_AVAILABLE);
                    result.put("containerId", container.map(NextcloudContainer::getContainerId).orElse(null));
                    result.put("exAppId", NextcloudDevServiceState.exAppId().orElse(null));
                    return result;
                })
                .build();

        actions.actionBuilder()
                .methodName("runWebhookWorker")
                .description("Runs the Nextcloud webhook background job once in the dev service container")
                .function(withContainer(
                        container -> container.occWithResult(NextcloudDevServicesResultBuildItem.OCC_COMMAND_WEBHOOK_CALL)))
                .build();

        actions.actionBuilder()
                .methodName("runOcc")
                .description("Runs an occ command in the Nextcloud dev service container")
                .parameter("command", "occ command line, e.g. 'app:list'")
                .function(params -> {
                    final List<String> args = tokenize(params.getOrDefault("command", ""));
                    if (args.isEmpty()) {
                        return CompletableFuture.completedFuture(unavailable("No occ command given"));
                    }
                    return withContainer(container -> container.occWithResult(args.toArray(String[]::new)))
                            .apply(params);
                })
                .build();

        actions.actionBuilder()
                .methodName("setExAppEnabled")
                .description("Enables or disables the ExApp in the Nextcloud dev service container")
                .parameter("enabled", "true to enable, false to disable the ExApp")
                .function(params -> {
                    final Optional<String> appId = NextcloudDevServiceState.exAppId();
                    if (appId.isEmpty()) {
                        return CompletableFuture
                                .completedFuture(unavailable("No ExApp registered in the dev service"));
                    }
                    final String command = Boolean.parseBoolean(params.get("enabled")) ? "app_api:app:enable"
                            : "app_api:app:disable";
                    return withContainer(container -> container.occWithResult(command, appId.get())).apply(params);
                })
                .build();

        actions.actionBuilder()
                .methodName("listExApps")
                .description("Lists the ExApps registered in the Nextcloud dev service container")
                .function(withContainer(container -> container.occWithResult("app_api:app:list")))
                .build();

        return actions;
    }

    /**
     * Wraps a container command: refuses it if no dev service container is
     * running, otherwise converts its {@link ExecResult} into a result map
     */
    private static Function<Map<String, String>, CompletableFuture<Map<String, Object>>> withContainer(
            Function<NextcloudContainer, CompletableFuture<ExecResult>> command) {
        return params -> {
            final Optional<NextcloudContainer> container = NextcloudDevServiceState.runningContainer();
            if (container.isEmpty()) {
                return CompletableFuture.completedFuture(unavailable(NOT_AVAILABLE));
            }
            return command.apply(container.get())
                    .thenApply(r -> {
                        final Map<String, Object> result = new LinkedHashMap<>();
                        result.put("available", true);
                        result.put("exitCode", r.getExitCode());
                        result.put("stdout", r.getStdout());
                        result.put("stderr", r.getStderr());
                        return result;
                    })
                    .exceptionally(t -> unavailable(
                            (t instanceof CompletionException && t.getCause() != null ? t.getCause() : t)
                                    .getMessage()));
        };
    }

    private static Map<String, Object> unavailable(String reason) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", false);
        result.put("reason", reason);
        return result;
    }

    /**
     * Splits an occ command line into arguments. Supports single and double
     * quotes. Backslashes are kept as is, since they are part of Nextcloud class
     * names. A leading {@code occ} or {@code php occ} is removed.
     *
     * @param commandLine Command line
     * @return Arguments
     */
    static List<String> tokenize(String commandLine) {
        final List<String> args = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean inToken = false;
        char quote = 0;
        for (int i = 0; i < commandLine.length(); i++) {
            final char c = commandLine.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    args.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) {
            args.add(current.toString());
        }
        if (args.size() >= 2 && args.get(0).equals("php") && args.get(1).endsWith("occ")) {
            args.subList(0, 2).clear();
        } else if (!args.isEmpty() && args.get(0).endsWith("occ")) {
            args.remove(0);
        }
        return args;
    }
}

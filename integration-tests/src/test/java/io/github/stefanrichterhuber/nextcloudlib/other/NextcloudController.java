package io.github.stefanrichterhuber.nextcloudlib.other;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class NextcloudController {
    public static final String OCC_COMMAND_SET_CONFIG_VALUE = "config:system:set";
    public static final String NEXTCLOUD_DEV_SERVICE_CONTAINER_ID_PROPERTY = "nextcloud.dev-services.container-id";

    @Inject
    DockerClient dockerClient;

    @Inject
    ScheduledExecutorService occExecutor;

    @Inject
    Logger log;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @ConfigProperty(name = NEXTCLOUD_DEV_SERVICE_CONTAINER_ID_PROPERTY)
    Optional<String> devServiceContainerId;

    private String assertContainerId() {
        if (devServiceContainerId.isPresent()) {
            return devServiceContainerId.get();
        } else {
            throw new IllegalStateException("Config property " + NEXTCLOUD_DEV_SERVICE_CONTAINER_ID_PROPERTY
                    + " not set. Unable to use " + this.getClass().getName());
        }
    }

    /**
     * 
     * Result of a command invocation
     * 
     * @param exitCode Exit code of the command execution. 0 for ok
     * @param stdout   Text written to STD OUT
     * @param stdErr   Text written to STD ERR
     */
    public record InvocationResult(long exitCode, String stdout, String stdErr) {
    }

    /**
     * Executes any shell command in the container.
     * 
     * @param command Command to execute
     * @return Success of the command
     */
    public CompletableFuture<InvocationResult> exec(String... command) {
        CompletableFuture<InvocationResult> execResult = CompletableFuture.supplyAsync(() -> {
            log.tracef("Execute command in running container: %s",
                    List.of(command).stream().collect(Collectors.joining(" ")));

            var resp = dockerClient.execCreateCmd(assertContainerId())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command).exec();

            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            dockerClient.execStartCmd(resp.getId()).exec(new ResultCallback<Frame>() {
                AtomicBoolean result = new AtomicBoolean(false);

                @Override
                public void close() throws IOException {
                }

                @Override
                public void onStart(Closeable closeable) {
                }

                @Override
                public void onNext(Frame object) {
                    if (object.getStreamType() == StreamType.STDOUT) {
                        try {
                            stdout.write(object.getPayload());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    if (object.getStreamType() == StreamType.STDERR) {
                        try {
                            stderr.write(object.getPayload());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    result.set(true);
                }

                @Override
                public void onComplete() {
                    result.set(true);
                }

                public void awaitCompletion() {
                    while (!result.get()) {
                        try {
                            Thread.sleep(Duration.ofMillis(100));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

            }).awaitCompletion();

            int exitCode = dockerClient.inspectExecCmd(resp.getId()).exec().getExitCodeLong().intValue();
            String stdErrString = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
            String stdOutString = new String(stdout.toByteArray(), StandardCharsets.UTF_8);
            // final ExecResult result = execInContainer(command);

            if (exitCode != 0) {
                log.errorf("Failed to execute command '%s': %s \n\n %s",
                        List.of(command).stream().collect(Collectors.joining(" ")),
                        stdErrString,
                        stdOutString);
            } else {
                log.debugf("Successfully executed command '%s' in container: %s ",
                        List.of(command).stream().collect(Collectors.joining(" ")),
                        stdOutString);
            }
            return new InvocationResult(exitCode, stdOutString, stdErrString);
        }, occExecutor);
        return execResult;
    }

    /**
     * Executes an {@code occ} command in the container. Wraps the command with the
     * correct PHP and user context ({@code www-data}) when the container is
     * running.
     *
     * @param command occ sub-command and its arguments, e.g.
     *                {@code "app:install", "webhook_listeners"}
     * @return a future that completes with {@code true} on success, {@code false}
     *         on a non-zero exit code
     */
    public CompletableFuture<InvocationResult> occ(String... command) {
        // Container is running, immediately execute command with the proper user
        // www-data
        final List<String> commandList = new ArrayList<>(8 + command.length);
        commandList.addAll(
                List.of("runuser", "--user", "www-data", "--", "/usr/local/bin/php", "-d",
                        "memory_limit=-1", "/var/www/html/occ"));
        commandList.addAll(List.of(command));
        return exec(commandList.toArray(new String[commandList.size()]));
    }

    /**
     * Sets a system configuration value in the Nextcloud container.
     * 
     * @param field Configuration field to set, e.g. {@code log}
     * @param key   Configuration key to set, e.g. {@code loglevel}
     * @param value Configuration value to set, e.g. {@code 2}
     * @param type  Configuration value type, e.g. {@code integer}, {@code string},
     *              {@code boolean}
     * @return
     */
    public CompletableFuture<InvocationResult> setConfigValue(String field, String key, String value, String type) {
        // TODO escape value (e.g. if it contains spaces or special characters) to avoid
        // shell injection
        if (field != null && !field.isEmpty()) {
            return occ(OCC_COMMAND_SET_CONFIG_VALUE, field, key, "--value", value, "--type", type);
        } else {
            return occ(OCC_COMMAND_SET_CONFIG_VALUE, key, "--value", value, "--type", type);
        }
    }

    /**
     * Sets a system configuration value in the Nextcloud container.
     * 
     * @param field Configuration field to set, e.g. {@code log}
     * @param key   Configuration key to set, e.g. {@code loglevel}
     * @param value Configuration value to set, e.g. {@code 2}
     * @return
     */
    public CompletableFuture<InvocationResult> setConfigValue(String field, String key, Object value) {
        String type = "string";
        if (value instanceof Integer) {
            type = "integer";
        } else if (value instanceof Boolean) {
            type = "boolean";
        }
        return setConfigValue(field, key, value.toString(), type);
    }

    /**
     * Model for the Auth Token entries returned by nextcloud for the 'occ
     * user:auth-tokens:list' command
     * UserAuthToken
     * 
     * @param id           ID of the token
     * @param name         Human-readable name of the token
     * @param lastActivity Unix timestamp of the last activity with the token
     * @param type         Type of token
     * @param scope        Scope of the token
     */
    public record UserAuthToken(int id, String name, long lastActivity, int type, Scope scope) {
        public record Scope(boolean filesystem) {
        }
    }

    /**
     * List the auth tokens (app passwords) for the given user
     * 
     * @param user Name of the user
     * @return List of auth tokens
     * @throws JsonMappingException
     * @throws JsonProcessingException
     */
    public List<UserAuthToken> listAuthTokens(String user) throws JsonMappingException, JsonProcessingException {
        final InvocationResult r = occ("user:auth-tokens:list", user, "--output", "json").join();
        final String json = r.stdout();
        if (r.exitCode() != 0) {
            throw new IllegalStateException(
                    String.format("Failed to execute 'occ user:auth-tokens:list %s --output json'", user));
        }

        final List<UserAuthToken> tokens = objectMapper.readValue(json, new TypeReference<List<UserAuthToken>>() {
        });

        return tokens;
    }
}

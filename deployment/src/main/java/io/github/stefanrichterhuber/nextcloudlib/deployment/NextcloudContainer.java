package io.github.stefanrichterhuber.nextcloudlib.deployment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

/**
 * Testcontainers-based wrapper for a Nextcloud Docker container used by the
 * Quarkus dev service. Supports deferred {@code occ} commands that are
 * collected as a post-installation shell script when the container has not
 * started yet, and executed directly once the container is running.
 */
public class NextcloudContainer extends GenericContainer<NextcloudContainer> {
    private static final Logger log = Logger.getLogger(NextcloudContainer.class);

    private static final String DEFAULT_IMAGE = "nextcloud:latest";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String DATABASE_NAME = "nxtcloud.db";
    private static final String SETUP_SCRIPT_PATH = "/docker-entrypoint-hooks.d/post-installation/setupscript.sh";

    private static final String ADMIN_PASSWORD_PROPERTY = "NEXTCLOUD_ADMIN_PASSWORD";
    private static final String ADMIN_USER_PROPERTY = "NEXTCLOUD_ADMIN_USER";
    private static final String SQLITE_DATABASE_PROPERTY = "SQLITE_DATABASE";

    private final List<String> startupScripts = new ArrayList<>();

    private final ScheduledExecutorService occExecutor = java.util.concurrent.Executors
            .newScheduledThreadPool(2);

    /**
     * Creates a container using the given image and the built-in default admin credentials.
     *
     * @param image Docker image name, e.g. {@code nextcloud:latest}
     */
    public NextcloudContainer(final String image) {
        this(image, ADMIN_USER, ADMIN_PASSWORD);
    }

    /**
     * Creates a container using the given image and explicit admin credentials.
     *
     * @param image    Docker image name, e.g. {@code nextcloud:latest}
     * @param user     Nextcloud admin username
     * @param password Nextcloud admin password
     */
    public NextcloudContainer(final String image, final String user, final String password) {
        super(image);
        withEnv(SQLITE_DATABASE_PROPERTY, DATABASE_NAME);
        withExposedPorts(80);
        withNextcloudUser(user);
        withNextcloudPassword(password);
        waitingFor(Wait.forLogMessage("^.*AH00094: Command line: 'apache2 -D FOREGROUND'.*$",
                1));
        withReuse(false);
        // Necessary to reach external apps
        // withExtraHost("host.docker.internal", "host-gateway");

    }

    /**
     * Creates a container using the default Nextcloud image and default admin credentials.
     */
    public NextcloudContainer() {
        this(DEFAULT_IMAGE);
    }

    /**
     * Sets a nextcloud admin user, if not set a default value is used
     *
     * @param user Nextcloud admin user
     * @return This container for chaining
     */
    public NextcloudContainer withNextcloudUser(String user) {
        withEnv(ADMIN_USER_PROPERTY, user);
        return this;
    }

    /**
     * Sets a nextcloud admin password, if not set a random default value is used
     *
     * @param password Nextcloud admin password
     * @return This container for chaining
     */
    public NextcloudContainer withNextcloudPassword(String password) {
        withEnv(ADMIN_PASSWORD_PROPERTY, password);
        return this;
    }

    /**
     * Installs a Nextcloud app in the container. Schedules the install for the
     * post-installation hook if the container has not started yet, or executes
     * it immediately if the container is already running.
     *
     * @param app app identifier, e.g. {@code webhook_listeners}
     * @return this container for chaining
     */
    public NextcloudContainer withApp(String app) {
        occ("app:install", "-f", "--keep-disabled", app);
        occ("app:enable", "-f", app);
        return this;
    }

    /**
     * Sets the log level of nextcloud, if not set a default value is used
     * 
     * @param logLevel 0 = Debug, 1 = Info, 2 = Warning, 3 = Error, 4 = Fatal
     * @return This container for chaining
     */
    public NextcloudContainer withLogLevel(int logLevel) {
        occ("config:system:set", "loglevel", "--value=" + logLevel, "--type=integer");
        return this;
    }

    /**
     * Installs multiple Nextcloud apps in the container. Delegates to
     * {@link #withApp(String)} for each entry.
     *
     * @param apps app identifiers to install
     * @return this container for chaining
     */
    public NextcloudContainer withApps(Iterable<String> apps) {
        for (String app : apps) {
            withApp(app);
        }
        return this;
    }

    /**
     * Assembles any deferred startup commands into a post-installation shell script,
     * copies it into the container at the appropriate hook location, and then starts
     * the container.
     */
    public void start() {
        if (!startupScripts.isEmpty()) {
            // Render and copy startup scripts
            StringBuilder sb = new StringBuilder();
            sb.append("#!/bin/bash\n");
            for (String script : startupScripts) {
                sb.append(script);
            }
            // Copy script in to post-installation folder, ensure it is executable!
            withCopyToContainer(Transferable.of(sb.toString(), 0100777), SETUP_SCRIPT_PATH);
        }

        super.start();

    }

    /**
     * Executes any shell command in the container. If the container is not yet
     * running, it is added to the startup scripts, if it is running directly
     * execute it
     * 
     * @param command Command to execute
     * @return Success of the command
     */
    public CompletableFuture<Boolean> exec(String... command) {
        CompletableFuture<Boolean> execResult = new CompletableFuture<>();
        if (isRunning()) {
            occExecutor.execute(() -> {
                try {
                    log.tracef("Execute command in running container: %s",
                            List.of(command).stream().collect(Collectors.joining(" ")));
                    final ExecResult result = execInContainer(command);

                    if (result.getExitCode() != 0) {
                        log.errorf("Failed to execute command '%s': %s \n\n %s",
                                List.of(command).stream().collect(Collectors.joining(" ")), result.getStderr(),
                                result.getStdout());
                        execResult.complete(false);
                    } else {
                        log.debugf("Successfully executed command '%s' in container: %s ",
                                List.of(command).stream().collect(Collectors.joining(" ")), result.getStdout());
                        execResult.complete(true);
                    }

                } catch (UnsupportedOperationException | IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            // Defer to later run on startup
            startupScripts.add(List.of(command).stream()
                    // Escape double quotes
                    .map(c -> c.replace("\"", "\\\""))
                    // put every command part with spaces within double quotes
                    .map(c -> c.contains(" ") ? "\"" + c + "\"" : c).collect(Collectors.joining(" ")) + "\n");
            execResult.complete(true);
        }
        return execResult;
    }

    /**
     * Executes an {@code occ} command in the container. Wraps the command with the
     * correct PHP and user context ({@code www-data}) when the container is running,
     * or schedules it in the post-installation script otherwise.
     *
     * @param command occ sub-command and its arguments, e.g.
     *                {@code "app:install", "webhook_listeners"}
     * @return a future that completes with {@code true} on success, {@code false}
     *         on a non-zero exit code
     */
    public CompletableFuture<Boolean> occ(String... command) {
        if (isRunning()) {
            // Container is running, immediately execute command with the proper user
            // www-data
            final List<String> commandList = new ArrayList<>(8 + command.length);
            commandList.addAll(
                    List.of("runuser", "--user", "www-data", "--", "/usr/local/bin/php", "-d",
                            "memory_limit=-1", "/var/www/html/occ"));
            commandList.addAll(List.of(command));
            return exec(commandList.toArray(new String[commandList.size()]));
        } else {
            final List<String> commandList = new ArrayList<>(4 + command.length);
            commandList.addAll(
                    List.of("/usr/local/bin/php", "-d",
                            "memory_limit=-1", "/var/www/html/occ"));
            commandList.addAll(List.of(command));
            return exec(commandList.toArray(new String[commandList.size()]));
        }
    }

}

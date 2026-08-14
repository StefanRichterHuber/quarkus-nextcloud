# Quarkus Nextcloud Extension

[![Maven Central](https://img.shields.io/maven-central/v/io.github.stefanrichterhuber/nextcloudlib)](https://central.sonatype.com/artifact/io.github.stefanrichterhuber/nextcloudlib)
[![Java 17+](https://img.shields.io/badge/Java-17+-brightgreen)](https://adoptium.net/)
[![Quarkus 3.x](https://img.shields.io/badge/Quarkus-3.x-4695EB?logo=quarkus&logoColor=white)](https://quarkus.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> **Disclaimer:** This project is an independent community effort and is not affiliated with, maintained, or endorsed by the original [Nextcloud project](https://nextcloud.com/) or [Quarkus project](https://quarkus.io/)

A Quarkus extension for building applications that interact with Nextcloud.

**Main Features:**

* File, Calendar, Contact, Comment, and Fulltext-search services as injectable CDI beans
* Nextcloud instance as a dev service — zero-config local Nextcloud with configurable apps
* Unified authentication system with pluggable `NextcloudAuthProvider`
* Webhook event handling with zero-reflection build-time dispatch
* Nextcloud ExApp / AppAPI support for developing first-class Nextcloud external applications

---

## Table of Contents

* [How to use](#how-to-use)
* [Quick Start](#quick-start)
* [Features](#features)
  * [Authentication](#authentication)
  * [CDI Services](#cdi-services)
  * [Nextcloud Container as Dev Service](#nextcloud-container-as-dev-service)
  * [Nextcloud Webhook Events](#nextcloud-webhook-events)
  * [Nextcloud AppAPI Support](#nextcloud-appapi-support)
* [Limits](#limits)

---

## How to use

Add the following Maven dependency to your `pom.xml`. Replace `[current version]` with the appropriate version number.

```xml
<dependency>
    <groupId>io.github.stefanrichterhuber</groupId>
    <artifactId>nextcloudlib</artifactId>
    <version>[current version]</version>
</dependency>
```

## Quick Start

1. **Add the dependency** above.

2. **Start in dev mode** — Quarkus automatically starts a local Nextcloud container via the dev service:

   ```bash
   ./mvnw quarkus:dev
   ```

3. **Inject a service** and start interacting with Nextcloud:

   ```java
   @ApplicationScoped
   public class MyService {

       @Inject
       NextcloudFileService files;

       public List<NextcloudFile> listRoot() {
           return files.listFiles("/");
       }
   }
   ```

4. **React to file events** with a single annotation:

   ```java
   @ApplicationScoped
   public class FileEventHandler {

       @OnNextcloudEvent(events = {NextcloudEvent.FileNodeCreatedEvent})
       public void onFileCreated(NextcloudEvent<?> event) {
           System.out.println("File created: " + event.event().className());
       }
   }
   ```

> **Tip:** The dev service injects `nextcloud.url`, `nextcloud.user`, `nextcloud.password`, `nextcloud.admin-user`, `nextcloud.admin-password` and `nextcloud.webhook.host` automatically — no `application.properties` entries needed for local development.

---

## Features

### Authentication

Provide an `ApplicationScoped` or `RequestScoped` implementation of
`io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider` to supply the
Nextcloud server URL and user credentials to all other services. A default implementation
(`io.github.stefanrichterhuber.nextcloudlib.runtime.auth.ConfiguredNextcloudAuthProvider`)
reads credentials from config properties:

| Property | Description |
| --- | --- |
| `nextcloud.url` | Base URL of the Nextcloud instance |
| `nextcloud.user` | Username |
| `nextcloud.password` | Password |
| `nextcloud.admin-user` | Username for an admin account. Optional, only required if admin access is necessary, falls back to `nextcloud.user` |
| `nextcloud.admin-password` | Password for an admin account. Optional, only required if admin access is necessary, falls back to `nextcloud.password` |

### CDI Services

The following CDI services are provided for convenient access to Nextcloud modules:

* `com.github.sardine.Sardine` — authenticated WebDAV client
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService` — file access
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileDiffService` — file diff / comparison
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService` — calendar access
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudContactService` — contacts access
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCommentService` — file comments
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService` — user information
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudSystemTagService` — global system tags on files
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudLoginService` — performs the Nextcloud [Login Flow V2](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html) to obtain per-user app passwords

### Nextcloud Container as Dev Service

Provides a pre-configured, ready-to-use Nextcloud container with an embedded SQLite database.
The dev service is automatically started in dev and test mode and skipped when `nextcloud.url`
is already set.

| Property | Default | Description |
| --- | --- | --- |
| `nextcloud.dev-services.image` | `nextcloud:latest` | Docker image for the dev service |
| `nextcloud.dev-services.user` | `admin` | Nextcloud admin username |
| `nextcloud.dev-services.password` | *(random)* | Nextcloud admin password |
| `nextcloud.dev-services.log-level` | `1` | Nextcloud log level (0 Debug … 4 Fatal) |
| `nextcloud.dev-services.apps` | *(empty)* | Comma-separated list of Nextcloud apps to install |
| `nextcloud.dev-services.enable-ex-app` | `false` | Install AppAPI, register a local daemon, and register this app as an ExApp |
| `nextcloud.dev-services.enable-webhook-worker` | `true` | Run the `WebhookCall` background-job worker so webhook events are dispatched without waiting for a cron trigger |

The dev service injects the following properties into the running application:

| Property | Description |
| --- | --- |
| `nextcloud.url` | Base URL of the started Nextcloud instance |
| `nextcloud.user` | Nextcloud user |
| `nextcloud.password` | Nextcloud password |
| `nextcloud.admin-user` | Admin username (same as `nextcloud.user` for dev services) |
| `nextcloud.admin-password` | Admin password (same as `nextcloud.password` for dev services) |

### Nextcloud Webhook Events

The extension can automatically register Nextcloud webhook listeners and dispatch incoming events
to annotated CDI bean methods. The webhook endpoint and the registration with Nextcloud are set
up entirely at build time — no configuration changes are needed when adding or removing event
handlers. Registering event handlers requires admin credentials, though. So provides these, either by `nextcloud.admin-user` / `nextcloud.admin-password` or a custom `NextcloudAuthProvider` with a `@NextcloudAdmin` qualifier.

#### Receiving events

Annotate any method on a CDI-managed bean with `@OnNextcloudEvent`. The method must accept
exactly one parameter of type `NextcloudEvent<?>`.

```java
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FileEventHandler {

    // Single event type
    @OnNextcloudEvent(events = {NextcloudEvent.FileNodeCreatedEvent})
    public void onFileCreated(NextcloudEvent<?> event) {
        System.out.println("File created: " + event.event().className());
    }

    // Multiple event types on one method
    @OnNextcloudEvent(events = {
        NextcloudEvent.FileNodeCreatedEvent,
        NextcloudEvent.FileNodeDeletedEvent,
        NextcloudEvent.FileNodeWrittenEvent
    })
    public void onFileChanged(NextcloudEvent<?> event) {
        System.out.println("File event: " + event.event().className());
    }
}
```

Constants for all supported event class names are defined on `NextcloudEvent`
(e.g. `FileNodeCreatedEvent`, `FileNodeDeletedEvent`, `FileNodeWrittenEvent`,
`FileNodeCopiedEvent`, `FileNodeRenamedEvent`, and more).

#### How it works

When at least one `@OnNextcloudEvent`-annotated method is present, the extension automatically:

1. **Registers a webhook endpoint** at `nextcloud.webhook.path` (default `/webhook`) as a Vert.x route.
2. **Generates zero-reflection invoker classes** at build time (using Gizmo bytecode generation) for direct method dispatch without `Method.invoke()`.
3. **Registers webhooks with Nextcloud** on application startup, using the configured host and secret.
4. **Deregisters webhooks on shutdown** when `nextcloud.webhook.deregister-webhooks-on-shutdown` is `true`.

If no `@OnNextcloudEvent` methods are present, the feature is a complete no-op.

#### Configuration

All webhook configuration lives under the `nextcloud.webhook.*` prefix:

| Property | Default | Description |
| --- | --- | --- |
| `nextcloud.webhook.path` | `/webhook` | Path at which the webhook endpoint is mounted. Changing this requires a rebuild. |
| `nextcloud.webhook.host` | `http://localhost:8080` | Publicly reachable base URL of this application as seen by the Nextcloud server. Combined with `path` to form the full callback URL. |
| `nextcloud.webhook.secret` | *(random 256-bit hex)* | Shared secret sent by Nextcloud in the authentication header. If absent, a cryptographically random secret is generated at startup and a warning is logged. A random secret changes on every restart and breaks re-registration — always set this in production. |
| `nextcloud.webhook.header` | `X-Nextcloud-Webhook-Secret` | HTTP header name used to transmit the shared secret. |
| `nextcloud.webhook.always-register` | `false` | When `true`, any existing webhook registration is deleted and re-created on every startup. |
| `nextcloud.webhook.deregister-webhooks-on-shutdown` | `true` | When `true`, webhooks registered by this application are removed from Nextcloud on shutdown. |

#### Prerequisites

The webhook callback URL (`nextcloud.webhook.host` + `nextcloud.webhook.path`) must be reachable
from the Nextcloud server. The dev service automatically configured everything necessary, when a event handler is detected during build.

### Nextcloud AppAPI Support

Support for developing Nextcloud [AppAPI](https://docs.nextcloud.com/server/stable/developer_manual/exapp_development/Introduction.html)
applications. Enable it with `nextcloud.exapp.enabled=true` at build time.

#### ExApp Configuration

| Property | Default | Description |
| --- | --- | --- |
| `nextcloud.exapp.enabled` | `false` | Enables ExApp mode. Must be set at build time. |

The following properties are normally injected as environment variables by the AppAPI daemon at
runtime, but can be set manually for local development:

| Property | Default | Description |
| --- | --- | --- |
| `app.id` | `quarkus-exapp` | Unique application identifier registered in Nextcloud |
| `app.display-name` | `Quarkus ExApp` | Human-readable display name |
| `app.version` | `1.0.0` | Application version |
| `app.protocol` | `http` | Protocol used to reach this app from Nextcloud |
| `app.host` | `localhost` | Hostname Nextcloud uses to reach this app |
| `app.port` | `8080` | Port this app listens on |
| `app.secret` | *(set by AppAPI)* | Shared secret for authenticating AppAPI requests |
| `app.persistent-storage` | `/tmp/${app.id}` | Path to the persistent storage directory |

#### Provided lifecycle endpoints

The extension registers the mandatory ExApp lifecycle endpoints:

* `GET /heartbeat` — responds with `{"status": "ok"}` to signal the app is running
* `POST /init` — reports initialization progress via `NextcloudExAppInitProgress` bean (responds HTTP 404 if no bean is present, which AppAPI interprets as "no initialization needed")
* `POST /enabled` — fires `ExAppEnabledEvent` or `ExAppDisabledEvent` CDI events when Nextcloud enables or disables the app

Observe `ExAppEnabledEvent` to register app features (e.g. menu entries)
with Nextcloud after the app is enabled.
When Webhook events are enabled `${app.protocol}://{app.host}:${app.port}` is used as webhook host, so no further configuration is necessary, just annotate methods with `@OnNextcloudEvent`.
A custom default implementation of `io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider` is provided to provide the configured secret `app.secret` as password at runtime.

#### Dev Services

When `nextcloud.exapp.enabled=true` or `nextcloud.dev-services.enable-ex-app=true`, the dev
service additionally:

* Installs the Nextcloud AppAPI app
* Registers a local [AppAPI daemon](https://docs.nextcloud.com/server/stable/admin_manual/exapps_management/ManagingDeployDaemons.html) that bridges the Nextcloud Docker container to the host via `host.docker.internal`
* Registers this Quarkus app as an ExApp within that daemon with the configured port (`quarkus.http.test-port` in test mode, `quarkus.http.port` in dev mode)
* Enables the ExApp in Nextcloud

---

## Limits

* Native image support is partially prepared but not thoroughly tested.

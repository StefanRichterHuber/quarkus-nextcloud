# Quarkus Nextcloud Extension

> **Disclaimer:** This project is an independent community effort and is not affiliated with, maintained, or endorsed by the original [Nextcloud project](https://nextcloud.com/) or [Quarkus project](https://quarkus.io/)

A Quarkus extension for building apps interacting with Nextcloud

Main Features:

* Wrapper around File, Calendar, Contact, Fulltext and file search
* Nextcloud Instance as dev service with configurable apps installed
* Unified authentication system
* Webhook event handling with zero-reflection dispatch

## How to use

To use the library, add the following Maven dependency to your `pom.xml`. Replace `[current version]` with the appropriate version number.

```xml
<dependency>
    <groupId>io.github.stefanrichterhuber</groupId>
    <artifactId>nextcloudlib</artifactId>
    <version>[current version]</version>
</dependency>
```

## Features

### Authentication

In general it is recommended to provide an `ApplicationScoped` or `RequestScoped` implementation of `io.github.stefanrichterhuber.nextcloudlib.runtime.auth.NextcloudAuthProvider` which is used for all other services to get the location of the Nextcloud server and the credentials of the current user. There is an default implemenation (`io.github.stefanrichterhuber.nextcloudlib.runtime.auth.ConfiguredNextcloudAuthProvider`) using the config properties for a static, single-user authentication:

| Property | Description |
| --- | --- |
| `nextcloud.url` | Base URL of the Nextcloud instance |
| `nextcloud.user` | User of the Nextcloud instance.  |
| `nextcloud.password` | Password of the Nextcloud instance. |

### CDI Services

Several CDI services are provided for convenient access to several Nextcloud modules:

* `com.github.sardine.Sardine` A CDI provided, authenticated instance of Sardine for general WebDav access
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudFileService` for file access
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudCalendarService` for calendar access
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudContactService` for contacts access
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudUserService` for accessing user information
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudLoginService` to perfom the Nextcloud [LoginFlow V2]( https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html). Necessary to obtain an app password per user for all other operations
* `io.github.stefanrichterhuber.nextcloudlib.runtime.NextcloudSystemTagService` to add / remove global System tags on files

### Nextcloud container as Dev Service

Provides a pre-configured, ready-to-use Nextcloud container with an embedded sqllite database

Possible config properties:

| Property | Default | Description |
| --- | --- | --- |
| `nextcloud.dev-services.image` | `nextcloud:latest` | Docker image to use for the dev service |
| `nextcloud.dev-services.user` | `admin` | Admin user to use for the access to the Nextcloud instance |
| `nextcloud.dev-services.password` | [Random String] | Password for the Nextcloud user |
| `nextcloud.dev-services.apps` | [Empty] | Comma-separated list of nextcloud apps to install |
| `nextcloud.dev-services.enable-webhook-worker` | `false` | Enable the Nextcloud async webhook worker (required for webhook delivery) |

Config properties provided by the dev service for direct access (matching the properties required for the default authentication provider):

| Property | Description |
| --- | --- |
| `nextcloud.url` | Base URL of the Nextcloud instance |
| `nextcloud.user` | User of the Nextcloud instance.  |
| `nextcloud.password` | Password of the Nextcloud instance. |

### Nextcloud Webhook Events

The extension can automatically register Nextcloud webhook listeners and dispatch incoming events to annotated CDI bean methods. The webhook endpoint and the registration with Nextcloud are set up entirely at build time — no configuration changes are needed when adding or removing event handlers.

#### Receiving events

Annotate any method on an `@ApplicationScoped` (or any CDI-managed) bean with `@OnNextcloudEvent`. The method must accept exactly one parameter of type `NextcloudEvent<?>`.

```java
import io.github.stefanrichterhuber.nextcloudlib.runtime.events.OnNextcloudEvent;
import io.github.stefanrichterhuber.nextcloudlib.runtime.models.NextcloudEvent;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FileEventHandler {

    // Single event type
    @OnNextcloudEvent(NextcloudEvent.FileNodeCreatedEvent)
    public void onFileCreated(NextcloudEvent<?> event) {
        System.out.println("File created: " + event.event().className());
    }

    // Multiple event types on one method
    @OnNextcloudEvent({
        NextcloudEvent.FileNodeCreatedEvent,
        NextcloudEvent.FileNodeDeletedEvent,
        NextcloudEvent.FileNodeWrittenEvent
    })
    public void onFileChanged(NextcloudEvent<?> event) {
        System.out.println("File event: " + event.event().className());
    }
}
```

Constants for all supported event class names are defined on `NextcloudEvent` (e.g. `FileNodeCreatedEvent`, `FileNodeDeletedEvent`, `FileNodeWrittenEvent`, `FileNodeCopiedEvent`, `FileNodeRenamedEvent`, and more).

#### How it works

When at least one `@OnNextcloudEvent`-annotated method is present, the extension automatically:

1. **Generates a webhook endpoint** at `nextcloud.webhook.path` (default `/webhook`) as a Vert.x route.
2. **Generates zero-reflection invoker classes** at build time (using Gizmo bytecode generation) for direct method dispatch without `Method.invoke()`.
3. **Registers webhooks with Nextcloud** on application startup via `NextcloudWebhookRegistrar`, using the configured host and secret.

If no `@OnNextcloudEvent` methods are present, the feature is a complete no-op.

#### Configuration

All webhook configuration lives under the `nextcloud.webhook.*` prefix:

| Property | Default | Description |
| --- | --- | --- |
| `nextcloud.webhook.path` | `/webhook` | Path at which the webhook endpoint is mounted. Changing this requires a rebuild. |
| `nextcloud.webhook.host` | `http://localhost:8080` | Publicly reachable base URL of this application as seen by the Nextcloud server. Combined with `path` to form the full callback URL. |
| `nextcloud.webhook.secret` | [Random 256-bit hex] | Shared secret sent by Nextcloud in the authentication header. If absent, a cryptographically random secret is generated at startup and a warning is logged. A random secret changes on every restart and breaks re-registration — always set this in production. |
| `nextcloud.webhook.header` | `X-Nextcloud-Webhook-Secret` | HTTP header name used to transmit the shared secret. |
| `nextcloud.webhook.always-register` | `false` | When `true`, any existing webhook registration is deleted and re-created on every startup. |

#### Prerequisites

The Nextcloud server must have the `webhook_listeners` app installed. For the dev service this can be done via:

```properties
nextcloud.dev-services.apps=webhook_listeners
nextcloud.dev-services.enable-webhook-worker=true
```

The webhook callback URL (`nextcloud.webhook.host` + `nextcloud.webhook.path`) must be reachable from the Nextcloud server. When Nextcloud runs in Docker (e.g. via the dev service), use `host.docker.internal` to reach the host machine:

```properties
nextcloud.webhook.host=http://host.docker.internal:8080
```

## Limits

* Native image generation is somewhat prepared but not thoroughly tested.

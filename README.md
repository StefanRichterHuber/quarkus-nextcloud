# Quarkus Nextcloud Extension

> **Disclaimer:** This project is an independent community effort and is not affiliated with, maintained, or endorsed by the original [Nextcloud project](https://nextcloud.com/) or [Quarkus project](https://quarkus.io/)

A Quarkus extension for building apps interacting with Nextcloud

Main Features:

* Wrapper around File, Calendar, Contact, Fulltext and file search
* Nextcloud Instance as dev service with configurable apps installed
* Unified authentication system

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

Config properties provided by the dev service for direct access (matching the properties required for the default authentication provider):

| Property | Description |
| --- | --- |
| `nextcloud.url` | Base URL of the Nextcloud instance |
| `nextcloud.user` | User of the Nextcloud instance.  |
| `nextcloud.password` | Password of the Nextcloud instance. |

## Limits

* Native image generation is somewhat prepared but not thoroughly tested.
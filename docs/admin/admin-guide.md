# Server Administrator Guide

This guide covers installation, configuration, and administration of the Hytale WebServer Plugin.

## Installation

Copy the plugin JAR file into your server's `mods/` folder.

By default, the web server binds to the game server's port +3. This can be overridden by creating
a file under `mods/Nitrado_WebServer/config.json`:

```json
{
  "BindHost": "127.0.0.1",
  "BindPort": 7003
}
```

## TLS Configuration

TLS is enabled by default using a self-signed certificate. To customize TLS settings, add a `Tls` section to your config:

### Self-signed Configuration

```json
{
  "Tls": {
    "CertificateProvider": "selfsigned",
    "SelfSigned": {
      "CommonName": "my-server.example.com"
    }
  }
}
```

### Disable TLS (not recommended)

```json
{
  "Tls": {
    "Insecure": true
  }
}
```

### Advanced TLS Options

For PEM certificates or Let's Encrypt integration, see the [Advanced TLS Configuration](tls-advanced.md) guide.

## Authentication

### Player Password

A player with the `nitrado.webserver.command.logincode.create` permission can execute the following command in-game:

```
/webserver code create
```

This displays a short-lived code that can be used to log in via the web server. Users can also use this code to assign
a long-lived password so that they can continue to log in even while not connected in-game.

### OAuth

OAuth support will be added if/when this functionality is officially supported by Hytale.

### Service Accounts

Service Accounts are intended for processes that automatically interact with the server through HTTP APIs.
They can be provisioned automatically or created through the Web UI.

For detailed setup instructions, see the [Service Accounts](service-accounts.md) guide.

### The Anonymous User

This plugin automatically creates an `ANONYMOUS` permission group. Un-authenticated requests will have their permissions
checked against this group.

With this mechanism, plugin developers can set up permissions for all actions provided by their plugins, but still
leave it up to server admins to decide which of those should be available to the public by adding permissions to the
`ANONYMOUS` group.

Please note: While failed permission checks for an authenticated user result in a `403 Forbidden`, failed permission
checks for the anonymous user result in a `401 Unauthorized`, which may then trigger an authentication flow.

## Built-in Permissions

The WebServer plugin provides the following built-in permissions:

| Permission                                   | Description                                                                      |
|----------------------------------------------|----------------------------------------------------------------------------------|
| `nitrado.webserver.command.logincode.create` | Allows a player to create a login code via the `/webserver code create` command. |
| `nitrado.webserver.web.list.plugins`         | Allows viewing the list of installed plugins through the web UI.                 |


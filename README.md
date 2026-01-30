# Hytale WebServer Plugin

A base plugin for the game Hytale, allowing other Hytale plugins to serve web-based content.

## Overview

Many plugin use cases require serving data via HTTP, such as for webhooks, live maps, or for exposing
data about the connected players. However, multiple plugins each opening their own HTTP servers for their
respective use cases is unnecessary overhead, and causes headaches for server admins and service providers.

This plugin provides a common solution for plugin developers, and solves typical requirements around web servers
in one single place, so that plugins stay compatible with each other.

Game Server Providers are encouraged to detect the presence of this plugin, and to provide it with a configuration that
ensures compatibility with their respective hosting platform.

## Main Features

- **Secure by default:** TLS using self-signed or user-provided certificates, as well as support for
  certificate retrieval.
- **Player Authentication:** Players may authenticate based on their Hytale account, allowing plugins
  to act in the context of that respective player.
- **Permission checks:** Built-in support for performing authorization checks based on an authenticated
  player's permissions on the Hytale server.
- **Service Accounts (API Users):** Allowing server owners to create accounts independent of a player
  account, which can still be managed using Hytale's permission system.
- **Extensibility:** All important implementations are behind interfaces, so that they can be replaced
  if required. To ensure intercompatibility with other web servers, classes from `jakarta.servlet.http` are used.

## Quick Start

### Installation

Get the JAR file from one of the following sources:

- The [GitHub Release Page](https://github.com/nitrado/hytale-plugin-webserver/releases)
- [CurseForge](https://www.curseforge.com/hytale/mods/nitrado-webserver)
- [Modtale](https://modtale.net/mod/nitrado-webserver-d077c7bf-6ad7-4477-b5aa-093bb6eaa67d)

Copy the plugin JAR file into your server's `mods/` folder. The web server binds to the game server's port +3 by default.

### Basic Configuration

Create `mods/Nitrado_WebServer/config.json` to customize settings:

```json
{
  "BindHost": "127.0.0.1",
  "BindPort": 7003
}
```

## Documentation

- **[Server Administrator Guide](docs/admin/admin-guide.md)** — Installation, TLS configuration, authentication setup, and service account management.
- **[Plugin Developer Guide](docs/developer/developer-guide.md)** — Integrating your plugins with WebServer, registering routes, and handling permissions.

## Contributing

Community contributions are welcome and encouraged. If you are a plugin developer and this plugin does not fulfill your
needs, please consider contributing to this repository before building your own web server implementation.

Due to the nature of this plugin, we need to ensure that it is versatile enough to fulfill the needs of plugin
developers, but we also need to avoid the plugin becoming bloated with features that would make it cumbersome to use.
So if you plan to work on a feature, please open an Issue here on GitHub first.

### Security

If you believe to have found a security vulnerability, please report your findings via security@nitrado.net.
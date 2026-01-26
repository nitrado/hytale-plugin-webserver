# Hytale WebServer Plugin

This is a base plugin for the game Hytale, allowing other Hytale plugins to serve web-based content.

## Purpose of this plugin
Many plugin use cases require serving data via HTTP, such as for webhooks, live maps, or for exposing
data about the connected players. However, multiple plugins each opening their own HTTP servers for their
respective use cases is unnecessary overhead, and causes headaches for server admins and service providers.

The aim of this plugin is to provide a common solution for plugin developers, and to solve typical
requirements around web servers in one single place, so that plugins stay compatible with each other.

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

### TLS Configuration

TLS is enabled by default using a self-signed certificate. To customize TLS settings, add a `Tls` section to your config:

```json
{
  "BindHost": "127.0.0.1",
  "BindPort": 7003,
  "Tls": {
    "Insecure": false,
    "CertificateProvider": "selfsigned"
  }
}
```

**Disable TLS (not recommended):**
```json
{
  "Tls": {
    "Insecure": true
  }
}
```

**Certificate Providers:**

| Provider      | Description                                      |
|---------------|--------------------------------------------------|
| `selfsigned`  | Generates a self-signed certificate (default)    |
| `pem`         | Uses PEM certificate and key files               |
| `letsencrypt` | Obtains certificates from Let's Encrypt via ACME |

**Self-signed configuration:**
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

**PEM configuration:**
```json
{
  "Tls": {
    "CertificateProvider": "pem",
    "Pem": {
      "CertificatePath": "/path/to/certificate.pem",
      "PrivateKeyPath": "/path/to/private-key.pem"
    }
  }
}
```

**Let's Encrypt configuration:**

Using Let's Encrypt requires agreement to their [Subscriber Agreement](https://letsencrypt.org/repository/). You must set `AgreeToTermsOfService` to `true` to acknowledge this.

```json
{
  "Tls": {
    "CertificateProvider": "letsencrypt",
    "LetsEncrypt": {
      "Domain": "my-server.example.com",
      "Production": false,
      "AgreeToTermsOfService": true
    }
  }
}
```

Set `Production` to `true` to use Let's Encrypt's production servers (has rate limits). When `false`, uses the staging environment for testing.

You can also configure a custom port for the ACME HTTP-01 challenge server using `ChallengePort` (defaults to 80):
```json
{
  "Tls": {
    "CertificateProvider": "letsencrypt",
    "LetsEncrypt": {
      "Domain": "my-server.example.com",
      "Production": false,
      "AgreeToTermsOfService": true,
      "ChallengePort": 8080
    }
  }
}
```

**Port 80 Permissions (Linux):**

Let's Encrypt uses the HTTP-01 challenge, which requires that port 80 is reachable from the internet. On Linux, binding to ports below 1024 requires elevated privileges. Here are the recommended approaches:

1. **Using `setcap` (recommended for non-containerized setups):**
   Grant the Java binary the capability to bind to privileged ports:
   ```bash
   sudo setcap 'cap_net_bind_service=+ep' $(readlink -f $(which java))
   ```
   Note: This must be re-applied after Java updates and grants this capability to _all_ Java applications If this is a
   concern, consider using a separate Java installation for Hytale, and only assign this capability to that installation.

2. **Using Docker:**
   When running in Docker, map port 80 from the host to the challenge port inside the container:
   ```bash
   docker run -p 80:8080 -p 5520:5520 -p 5523:5523 your-server-image
   ```
   Then configure `ChallengePort` to match the container port (8080 in this example).

   Alternatively, when using host networking, grant the container the capability to bind to privileged ports:
   ```bash
   docker run --network host --cap-add NET_BIND_SERVICE your-server-image
   ```

3. **Using Docker Compose:**
   ```yaml
   services:
     hytale:
       image: your-server-image
       ports:
         - "80:8080"    # ACME challenge: host port 80 -> container port 8080
         - "5520:5520"  # Game Server
         - "5523:5523"  # WebServer
   ```
   Then configure `ChallengePort` to match the container port (8080 in this example).

   Alternatively, when using host networking:
   ```yaml
   services:
     hytale:
       image: your-server-image
       network_mode: host
       cap_add:
         - NET_BIND_SERVICE
   ```

4. **Port forwarding with `iptables`:**
   Forward port 80 to a higher unprivileged port (e.g., 8080):
   ```bash
   sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8080
   ```
   Then set `ChallengePort` to 8080 in the configuration.

5. **Running as root (not recommended):**
   Running the server as root allows binding to port 80 but exposes your system to significant security risks if the server process is compromised.

## Usage

### Development Setup
Example using maven:

```xml
    <dependencies>
        ...
        <dependency>
            <groupId>net.nitrado.hytale.plugins</groupId>
            <artifactId>webserver</artifactId>
            <version>1.0-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
        ...
    </dependencies>
```

[[ TODO: Example for Gradle ]]

The `<scope>provided</scope>` indicates that your resulting plugin does not need to bundle the web
server code, as it is provided by the Hytale server at runtime.

In your plugin's `manifest.json`, define `Nitrado:WebServer` as a dependency:

```json
{
  "Dependencies": {
    "Nitrado:WebServer": "*"
  }
}
```

### Registering Routes
Below is example code taken from [Nitrado:Query](https://github.com/nitrado/hytale-plugin-query):

```java
public class Query extends JavaPlugin {

  private WebServerPlugin webServerPlugin;

  public Query(@Nonnull JavaPluginInit init) {
    super(init);
  }

  @Override
  protected void setup() {
    this.registerHandlers();
  }

  private void registerHandlers() {
    var plugin = PluginManager.get().getPlugin(new PluginIdentifier("Nitrado", "WebServer"));

    if (!(plugin instanceof WebServerPlugin webServer)) {
      return;
    }

    this.webServerPlugin = webServer;

    try {
      webServerPlugin.addServlet(this, "", new QueryServlet());
    } catch (Exception e) {
      getLogger().at(Level.SEVERE).withCause(e).log("Failed to register route.");
    }
  }

  @Override
  protected void shutdown() {
    webServerPlugin.removeServlets(this);
  }
}
```

The handler will be automatically registered at `/<PluginGroup>/<PluginName>`, so `/Nitrado/Query` for
the example above. This approach avoids collisions between multiple plugins.

Also note that in the `shutdown()` method the plugin removes itself from the web server again. This ensures
that you can reload your plugin at runtime.

### Handling Permissions
To check for permissions, the most convenient way is via annotations in the servlet.

In the example below, the `doGet` handler requires the requesting user to have at least one of three given permissions.
If none of these permissions is fulfilled, the request is declined. The registered servlet can then still check for
those permissions to adjust its output:

```java
public class QueryServlet extends HttpServlet {

    @Override
    @RequirePermissions(value = {Permissions.READ_PLAYERS, Permissions.READ_SERVER, Permissions.READ_UNIVERSE}, mode = RequirePermissions.Mode.ANY)
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");

        Document doc = new Document();

        var principal = req.getUserPrincipal();
        if (principal instanceof HytaleUserPrincipal user) {
            if (user.hasPermission(Permissions.READ_SERVER)) {
                this.addServerData(doc);
            }

            if (user.hasPermission(Permissions.READ_PLAYERS)) {
                this.addPlayerData(doc);
            }

            if (user.hasPermission(Permissions.READ_UNIVERSE)) {
                this.addUniverseData(doc);
            }
        }

        resp.getWriter().println(doc.toJson(JsonWriterSettings.builder().indent(true).build()));
    }
    // ...
}
```

#### Using RequirePermissionsFilter

If you cannot use the `@RequirePermissions` annotation (e.g., when using a third-party servlet, a dynamically
generated servlet, or when you need to configure permissions at runtime), you can use `RequirePermissionsFilter`
instead.

The filter is registered alongside your servlet and performs the same permission checks:

```java
// Require ALL permissions (default behavior)
webServerPlugin.addServlet(this, "/protected", new ThirdPartyServlet(),
    new RequirePermissionsFilter("my.plugin.web.read", "my.plugin.web.write"));

// Require ANY of the permissions (pass `true` as first argument)
webServerPlugin.addServlet(this, "/protected", new ThirdPartyServlet(),
    new RequirePermissionsFilter(true, "my.plugin.web.read.a", "my.plugin.web.read.b"));
```

The filter behaves identically to the annotation:
- Returns `401 Unauthorized` if no user is authenticated (or if the anonymous user lacks permission)
- Returns `403 Forbidden` if an authenticated user lacks the required permissions

**Note:** When using both the annotation and the filter on the same servlet, both checks must pass.

### Built-in Permissions

The WebServer plugin provides the following built-in permissions:

| Permission                                   | Description                                                                      |
|----------------------------------------------|----------------------------------------------------------------------------------|
| `nitrado.webserver.command.logincode.create` | Allows a player to create a login code via the `/webserver code create` command. |
| `nitrado.webserver.web.list.plugins`         | Allows viewing the list of installed plugins through the web UI.                 |

### Authentication

#### Player Password
A player with the `nitrado.webserver.command.logincode.create` permission can execute the following command in-game:

```
/webserver code create
```

This displays a short-lived code that can be used to log in via the web server. Users can also use this code to assign
a long-lived password so that they can continue to log in even while not connected in-game.

#### OAuth
OAuth support will be added if/when this functionality is officially supported by Hytale. 

#### Service Accounts
Service Accounts are intended for processes that automatically interact with the server through HTTP APIs. For
security purposes, it is recommended to use service accounts that have the exact set of permissions to fulfill the
tasks they are intended for.

Service accounts will be automatically added to the `SERVICE_ACCOUNT` group to make them easier to identify in
permission management.

Service Accounts can be either created through the Web UI (not implemented yet) or provisioned automatically.

##### Authenticating as a Service Account
You can then use a Service Account password to authenticate against the web server using Basic Auth, such as with:

```
curl -u serviceaccount.MyServiceAccountName:MyPassword <url>
```

Note the `serviceaccount.` prefix when authenticating with a service account.

#### Creation of Service Accounts through the Web UI
[[ TODO ]]

##### Automatic Provisioning of Service Accounts
Create the folder `mods/Nitrado_WebServer/provisioning`. In it, you can place files that end in
`.serviceaccount.json`, such as `example.serviceaccount.json` with the following content structure:

```json
{
  "Enabled": true,
  "Name": "serviceaccount.example",
  "PasswordHash": "$2b$10$ME8G6/YZ3hXUOAhLs3mrh.a3cuZTvzE2zGjQIqxztgPXKtm7sFCde",
  "Groups": ["Creative"],
  "Permissions": ["nitrado.query.web.read.players"]
}
```

A service account with `Enabled` set to `true` will be automatically created or updated on server start. Setting
`Enabled` to `false` will lead to the service account to be removed, also removing it from any groups and permissions,
to not clutter your permission management.

#### The Anonymous User
This plugin automatically creates an `ANONYMOUS` permission group. Un-authenticated requests will have their permissions
checked against this group.

With this mechanism, plugin developers can set up permissions for all actions provided by their plugins, but still
leave it up to server admins to decide which of those should be available to the public by adding permissions to the
`ANONYMOUS` group.

Please note: While failed permission checks for an authenticated user result in a `403 Forbidden`, failed permission
checks for the anonymous user result in a `401 Unauthorized`, which may then trigger an authentication flow.

## Contributing
Community contributions are welcome and encouraged. If you are a plugin developer and this plugin does not fulfill your
needs, please consider contributing to this repository before building your own web server implementation.

Due to the nature of this plugin, we need to ensure that it is versatile enough to fulfill the needs of plugin
developers, but we also need to avoid the plugin to become bloated with features that would make it cumbersome to use.
So if you plan to work on a feature, please open an Issue here on GitHub first.

### Security
If you believe to have found a security vulnerability, please report your findings via security@nitrado.net.
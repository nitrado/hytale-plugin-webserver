# Hytale Plugin WebServer

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

**Please note:** This plugin is under active development. Not all features listed below are
already supported, but will be supported until the Hytale release.

- **Secure by default:** TLS using self-signed or user-provided certificates, as well as support for
  certificate retrieval.
- **Player Authentication:** Users may authenticate based on their Hytale account, allowing plugins
  to act in the context of that respective player.
- **Permission checks:** Built-in support for performing authorization checks based on an authenticated
  player's permissions on the Hytale server.
- **Service Accounts (API Users):** Allowing server owners to create accounts independent of a player
  account, which can still be managed using Hytale's permission system.
- **Extensibility:** All important implementations are behind interfaces, so that they can be replaced
  if required. To ensure intercompatibility with other web servers, classes from `jakarta.servlet.http` are used.

## Installation

Copy the plugin JAR file into your server's `plugins/` folder.

By default, the web server binds to the game server's port +3. This can be overridden by creating
a file under `plugins/Nitrado_WebServer/config.json`:

```json
{
  "BindHost": "127.0.0.1",
  "BindPort": 7003
}
```

[[TODO: Configuration Options for TLS]]

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
    @Override
    protected void setup() {
        this.registerHandlers();
    }

    private void registerHandlers() {
        var plugin = PluginManager.get().getPlugin(new PluginIdentifier("Nitrado", "WebServer"));

        if (!(plugin instanceof WebServerPlugin webServerPlugin)) {
            return;
        }

        try {
            webServerPlugin
                    .createHandlerBuilder(this)
                    .requireAnyPermissionOf(
                            Permissions.VIEW_PLAYERS,
                            Permissions.VIEW_SERVER,
                            Permissions.VIEW_UNIVERSE
                    )
                    .addServlet(new QueryServlet(), "/")
                    .register();
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to register route: " + e.getMessage());
        }
    }
}
```

The handler will be automatically registered at `/<plugin_group>/<plugin_name>/`, lowercased. So `/nitrado/query/` for
the example above. This approach avoids collisions between multiple plugins.

The registered handler requires the requesting user to have at least one of three given permissions. If none of these
permissions is fulfilled, the request is declined. The registered servlet can then still check for those permissions
to adjust its output, such as:

```java
public class QueryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");

        Document doc = new Document();

        var principal = req.getUserPrincipal();
        if (principal instanceof HytaleUserPrincipal user) {
            if (user.hasPermission(Permissions.VIEW_SERVER)) {
                this.addServerData(doc);
            }

            if (user.hasPermission(Permissions.VIEW_PLAYERS)) {
                this.addPlayerData(doc);
            }

            if (user.hasPermission(Permissions.VIEW_UNIVERSE)) {
                this.addUniverseData(doc);
            }
        }

        resp.getWriter().println(doc.toJson(JsonWriterSettings.builder().indent(true).build()));
    }
    
    // ...
}
```

### Authentication

#### User Password
A player with the `nitrado.webserver.userpassword.set` permission can execute the following command in-game:

```
/webserver userpassword set MyPassword
```

They can then use that password to authenticate against the web server. The currently supported authentication method
is Basic Auth, such as with

```
curl -u username:password <url>
```

The `username` may be the user's UUID, or their display name at time of creation of the password.

[[ TODO: Form Login Flow / Session handling ]]

#### OAuth
[[ TODO ]]

#### Service Accounts
A player with the `nitrado.webserver.serviceaccount.create` permission can execute the following command in-game

```
/webserver serviceaccount create MyServiceAccountName MyPassword
```

You can then use that password to authenticate against the web server using Basic Auth, such as with:

```
curl -u serviceaccount.MyServiceAccountName:MyPassword <url>
```

Note the `serviceaccount.` prefix when authenticating with a service account.

Service accounts will be automatically added to the `SERVICE_ACCOUNT` group to make them easier to identify in
permission management.

To list the already created service accounts with their names and UUIDs, use 

```
/webserver serviceaccount list
```

And for deletion:

```
/webserver serviceaccount delete NameOrUUID
```

Deleting a service account will automatically remove it from any groups and permissions, to not clutter your permission
management.

#### The Anonymous User
This plugin automatically creates a permissions entry for a user with the UUID `00000000-0000-0000-0000-000000000000` in
group `ANONYMOUS`. Un-authenticated requests will appear as that user, with the permissions that have been assigned to
that user.

With this mechanism, plugin developers can set up permissions for all actions provided by their plugins, but still
leave it up to server admins to decide which of those should be available to the public.

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
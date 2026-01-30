# Permission Handling

This guide covers how to handle permissions when integrating your plugins with Nitrado:WebServer.

## Permission Naming Conventions

When defining permissions for web endpoints, follow this naming scheme:

```
plugin_group.plugin_name.web.action.resource
```

### Structure

| Segment        | Description                                      | Example             |
|----------------|--------------------------------------------------|---------------------|
| `plugin_group` | Your organization or plugin collection namespace | `nitrado`, `my_org` |
| `plugin_name`  | The specific plugin name                         | `economy`, `chat`   |
| `web`          | Literal segment indicating a web permission      | `web`               |
| `action`       | CRUDL action (see below)                         | `read`, `create`    |
| `resource`     | The resource being accessed                      | `players`, `config` |

### CRUDL Actions

Use standard CRUDL actions that map to HTTP verbs:

| Action   | HTTP Verb     | Description                    | Example Permission                      |
|----------|---------------|--------------------------------|-----------------------------------------|
| `create` | `POST`        | Create a new resource          | `my_org.economy.web.create.transaction` |
| `read`   | `GET`         | Read a single resource         | `my_org.economy.web.read.balance`       |
| `update` | `PUT`/`PATCH` | Modify an existing resource    | `my_org.economy.web.update.config`      |
| `delete` | `DELETE`      | Remove a resource              | `my_org.economy.web.delete.player`      |
| `list`   | `GET`         | List a collection of resources | `my_org.economy.web.list.transactions`  |

### Examples

```java
public final class Permissions {
    // Single resource operations
    public static final String WEB_READ_PLAYER = "my_org.players.web.read.player";
    public static final String WEB_UPDATE_PLAYER = "my_org.players.web.update.player";
    public static final String WEB_DELETE_PLAYER = "my_org.players.web.delete.player";
    
    // Collection operations
    public static final String WEB_LIST_PLAYERS = "my_org.players.web.list.players";
    public static final String WEB_CREATE_PLAYER = "my_org.players.web.create.player";
}
```

### Best Practices

- **Be consistent**: Use the same `plugin_group.plugin_name` prefix for all permissions in your plugin.
- **Use singular for single-resource actions**: `read.player`, `update.config`
- **Use plural for collection actions**: `list.players`, `list.transactions`
- **Avoid overly generic names**: Prefer `read.player_stats` over just `read.stats`

## The Anonymous User

This plugin automatically creates an `ANONYMOUS` permission group. Un-authenticated requests will have their permissions
checked against this group.

With this mechanism, you can set up permissions for all actions provided by your plugins, but still
leave it up to server admins to decide which of those should be available to the public by adding permissions to the
`ANONYMOUS` group.

Please note: While failed permission checks for an authenticated user result in a `403 Forbidden`, failed permission
checks for the anonymous user result in a `401 Unauthorized`, which may then trigger an authentication flow.

## Using Annotations

To check for permissions, the most convenient way is via annotations in the servlet.

In the example below, the `doGet` handler requires the requesting user to have at least one of the given permissions.
If none of these permissions is fulfilled, the request is declined. The registered servlet can then still check for
those permissions to adjust its output:

```java
public class QueryServlet extends HttpServlet {

    @Override
    @RequirePermissions(
            mode = RequirePermissions.Mode.ANY,
            value = {
                Permissions.WEB_READ_BASIC,
                Permissions.WEB_READ_PLAYERS,
                Permissions.WEB_READ_SERVER,
                Permissions.WEB_READ_UNIVERSE,
                Permissions.WEB_READ_PLUGINS
            }
    )
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("application/json");

        var response = new QueryResponseV1();

        var principal = req.getUserPrincipal();
        if (principal instanceof HytaleUserPrincipal user) {
            if (user.hasPermission(Permissions.WEB_READ_BASIC)) {
                response.addBasicData();
            }

            if (user.hasPermission(Permissions.WEB_READ_SERVER)) {
                response.addServerData();
            }

            if (user.hasPermission(Permissions.WEB_READ_PLAYERS)) {
                response.addPlayerData();
            }

            if (user.hasPermission(Permissions.WEB_READ_UNIVERSE)) {
                response.addUniverseData();
            }

            if (user.hasPermission(Permissions.WEB_READ_PLUGINS)) {
                response.addPluginData();
            }
        }

        resp.getWriter().println(response.toDocument().toJson(JsonWriterSettings.builder().indent(true).build()));
    }
    // ...
}
```

## Using RequirePermissionsFilter

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

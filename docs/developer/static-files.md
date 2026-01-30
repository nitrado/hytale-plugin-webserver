# Serving Static Files

Nitrado:WebServer provides a `StaticFileServlet` for serving static assets such as CSS, JavaScript, images, and fonts. This servlet can serve files from either the filesystem or your plugin's JAR (classpath).

## Basic Setup (from JAR Resources)

Place your static files in `src/main/resources/static/` and register the servlet:

```java
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import net.nitrado.hytale.plugins.webserver.servlets.StaticFileServlet;

public class MyPlugin extends JavaPlugin {

    public MyPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        var plugin = PluginManager.get().getPlugin(new PluginIdentifier("Nitrado", "WebServer"));
        if (!(plugin instanceof WebServerPlugin webServerPlugin)) {
            return;
        }

        // Serve files from src/main/resources/static/ in the JAR
        webServerPlugin.addServlet(this,
            "/static/*",
            new StaticFileServlet("static", getClass().getClassLoader()));
    }
}
```

Your files will be accessible at `/{Group}/{PluginName}/static/...`:

| Source File                              | URL                                          |
|------------------------------------------|----------------------------------------------|
| `src/main/resources/static/css/app.css`  | `/MyOrg/MyPlugin/static/css/app.css`         |
| `src/main/resources/static/js/app.js`    | `/MyOrg/MyPlugin/static/js/app.js`           |

> **Note:** Paths are case-sensitive. Use the exact casing from your plugin's `PluginIdentifier` (group and name).

## Serving from Filesystem

For development or user-customizable files, serve directly from a directory on disk:

```java
// Serve files from a directory on disk
webServerPlugin.addServlet(this,
    "/static/*",
    new StaticFileServlet(Path.of("mods/YourGroup_YourPlugin/static")));
```

## Hybrid Approach (Filesystem with JAR Fallback)

This allows overriding JAR-bundled files with filesystem files—useful for development or admin customization:

```java
// Files are served from filesystem first, falling back to JAR resources
webServerPlugin.addServlet(this,
    "/static/*",
    new StaticFileServlet(
        Path.of("mods/YourGroup_YourPlugin/theme/static"),  // Filesystem (checked first)
        "static",                                             // Classpath base
        getClass().getClassLoader()                           // Class loader for JAR
    ));
```

## Referencing Static Files in Templates

Thymeleaf's `@{...}` syntax resolves URLs from the **server root**, not from your plugin's base path. Since your servlets are mounted under `/{Group}/{PluginName}/`, you must include the full path prefix:

```html
<!-- For a plugin with group "MyOrg" and name "MyPlugin" -->
<link id="additionalCss" th:href="@{/MyOrg/MyPlugin/static/css/app.css}" rel="stylesheet"/>

<th:block id="additionalJavascript">
    <script th:src="@{/MyOrg/MyPlugin/static/js/app.js}" type="text/javascript"></script>
</th:block>
```

**Tip:** To avoid hardcoding paths, pass the plugin prefix as a template variable:

```java
// In your servlet
Map<String, Object> variables = new HashMap<>();
variables.put("pluginBase", "/" + plugin.getIdentifier().getGroup() + "/" + plugin.getIdentifier().getName());
renderTemplate(req, resp, "my-template", variables);
```

```html
<!-- In your template -->
<link id="additionalCss" th:href="@{${pluginBase} + '/static/css/app.css'}" rel="stylesheet"/>
```

See [Templating](templating.md#adding-custom-css-and-javascript) for more details on injecting CSS and JavaScript into the base layout.

## Supported MIME Types

`StaticFileServlet` automatically sets appropriate `Content-Type` headers for common file types:

| Category | Extensions                                               |
|----------|----------------------------------------------------------|
| Text     | `.html`, `.css`, `.js`, `.json`, `.xml`, `.txt`          |
| Images   | `.png`, `.jpg`, `.jpeg`, `.gif`, `.svg`, `.ico`, `.webp` |
| Fonts    | `.woff`, `.woff2`, `.ttf`, `.otf`, `.eot`                |
| Other    | `.pdf`, `.zip`, `.map`                                   |

Unknown extensions default to `application/octet-stream`.


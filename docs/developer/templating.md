# Templating

Nitrado:WebServer includes built-in support for HTML templating using [Thymeleaf](https://www.thymeleaf.org/). This allows plugins to render dynamic HTML pages with a consistent look and feel.

## Using TemplateServlet

To use templating, extend `TemplateServlet` instead of `HttpServlet`:

```java
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import net.nitrado.hytale.plugins.webserver.servlets.TemplateServlet;

public class MyServlet extends TemplateServlet {

    public MyServlet(WebServerPlugin parentPlugin, JavaPlugin thisPlugin) {
        super(parentPlugin, thisPlugin);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("text/html");

        var vars = new HashMap<String, Object>();
        vars.put("title", "My Page");
        vars.put("items", List.of("Item 1", "Item 2", "Item 3"));

        this.renderTemplate(req, resp, "myplugin.mytemplate", vars);
    }
}
```

The `renderTemplate` method takes:
- `req` / `resp`: The servlet request and response
- `template`: The template name (without `.html` extension)
- `variables`: A map of variables to pass to the template (optional)

## Creating Templates

Place your template files in `src/main/resources/templates/` with a `.html` extension.

### Using the Base Layout

Nitrado:WebServer provides a base layout that includes Bootstrap CSS, a sidebar with plugin navigation, and user authentication status. To use it, add this to your template's `<html>` tag:

```html
<html th:replace="~{nitrado.webserver :: layout(~{::title}, ~{::section})}" lang="en">
<head>
    <title>My Page Title</title>
</head>
<body>
    <section class="container py-4">
        <!-- Your content here -->
        <h1>Hello World</h1>
    </section>
</body>
</html>
```

The `th:replace` attribute replaces your entire HTML with the base layout, injecting:
- `~{::title}` - Your page's `<title>` element
- `~{::section}` - Your page's `<section>` element as the main content

### Adding Custom CSS and JavaScript

The base layout provides optional slots for injecting custom CSS and JavaScript into your pages. Use elements with specific IDs that the layout will pick up:

| Element ID             | Location in Layout | Purpose                      |
|------------------------|--------------------|------------------------------|
| `additionalCss`        | End of `<head>`    | Custom stylesheets or styles |
| `additionalJavascript` | End of `<body>`    | Custom scripts               |

**Example with both CSS and JavaScript:**

```html
<html th:replace="~{nitrado.webserver :: layout(~{::title}, ~{::section})}" lang="en">
<head>
    <title>My Page</title>
    <link id="additionalCss" th:href="@{/MyOrg/MyPlugin/static/css/my-styles.css}" rel="stylesheet"/>
</head>
<body>
    <section class="container py-4">
        <h1>My Content</h1>
    </section>

    <th:block id="additionalJavascript">
        <script th:src="@{/MyOrg/MyPlugin/static/js/my-script.js}" type="text/javascript"></script>
    </th:block>
</body>
</html>
```

**Notes:**
- Thymeleaf's `@{...}` resolves URLs from the server root, so you must include the full path with your plugin group and name (e.g., `@{/MyOrg/MyPlugin/static/...}`)
- Static files are not served automatically (see "Serving Static Files" in the next section)
- For CSS, apply the `id="additionalCss"` directly to the `<link>` or `<style>` element
- For JavaScript, wrap your `<script>` tags in a `<th:block id="additionalJavascript">` element
- If these elements are not present in your template, the slots are simply omitted
- Paths are case-sensitive - use the exact casing from your plugin's `PluginIdentifier`

### Serving Static Files

To serve your custom CSS and JavaScript files, use the `StaticFileServlet`. See [Serving Static Files](static-files.md) for complete documentation on:

- Serving files from JAR resources or filesystem
- Hybrid approaches with filesystem override and JAR fallback
- Supported MIME types

### Example Template

Below is an example from [Nitrado:Query](https://github.com/nitrado/hytale-plugin-query):

```html
<html th:replace="~{nitrado.webserver :: layout(~{::title}, ~{::section})}" lang="en">
<head>
    <title>Nitrado:Query</title>
</head>
<body>
    <section class="container py-4">
        <h1 class="mb-5">Server Information</h1>
        
        <!-- Conditional rendering -->
        <div th:if="${response.server != null}" class="mb-4">
            <h5>Server</h5>
            <table class="table table-striped">
                <tbody>
                    <tr><th scope="row">Name</th><td th:text="${response.server.name}"></td></tr>
                    <tr><th scope="row">Version</th><td th:text="${response.server.version}"></td></tr>
                </tbody>
            </table>
        </div>

        <!-- Iterating over collections -->
        <div th:if="${response.players != null}" class="mb-4">
            <h5>Players</h5>
            <table class="table table-striped">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>UUID</th>
                    </tr>
                </thead>
                <tbody>
                    <tr th:each="player : ${response.players}">
                        <td th:text="${player.name}"></td>
                        <td th:text="${player.uuid}"></td>
                    </tr>
                </tbody>
            </table>
            <p th:if="${response.players.isEmpty()}" class="text-muted">No players online</p>
        </div>
    </section>
</body>
</html>
```

## Built-in Variables

The following variables are automatically available in all templates:

| Variable          | Type                  | Description                                                  |
|-------------------|-----------------------|--------------------------------------------------------------|
| `user`            | `HytaleUserPrincipal` | The authenticated user (if any)                              |
| `version`         | `String`              | The WebServer plugin version                                 |
| `pluginsByGroups` | `Map<String, List>`   | Registered plugins grouped by group (if user has permission) |
| `pluginGroups`    | `List<String>`        | Sorted list of plugin group names (if user has permission)   |

## Theme Support

Nitrado:WebServer supports a theme folder system that allows you to override templates and static files without rebuilding your plugin JAR. This is useful for:

- **Development**: Edit templates and see changes immediately without recompiling
- **Customization**: Server admins can customize the look and feel without modifying plugin code

### Theme Folder Structure

Place theme files in the plugin's data directory under `theme/`:

```
mods/
└── Nitrado_WebServer/
    └── theme/
        ├── templates/
        │   └── nitrado.webserver.html      # Override base layout
        └── static/
            └── css/
                └── custom.css              # Custom static files
```

For your own plugin:

```
mods/
└── YourGroup_YourPlugin/
    └── theme/
        └── templates/
            └── yourplugin.mytemplate.html  # Override your plugin's template
```

### Resolution Priority

Templates are resolved in the following order (first match wins):

1. **Plugin's theme folder** (`mods/YourGroup_YourPlugin/theme/templates/`)
2. **Plugin's JAR** (`templates/` in the plugin JAR)
3. **WebServerPlugin's theme folder** (`mods/Nitrado_WebServer/theme/templates/`)
4. **WebServerPlugin's JAR** (default templates)

This allows you to:
- Override your own plugin's templates during development
- Override WebServerPlugin's base layout for custom styling
- Fall back to defaults when no override exists

### Development Workflow

For rapid template development:

1. Build and deploy your plugin once to register the servlet
2. Copy your template files to the theme folder:
   ```bash
   mkdir -p mods/YourGroup_YourPlugin/theme/templates
   cp src/main/resources/templates/*.html mods/YourGroup_YourPlugin/theme/templates/
   ```
3. Edit templates in the theme folder — changes are reflected immediately (no caching)
4. When satisfied, copy the templates back to `src/main/resources/templates/` and rebuild

**Alternative: Using symlinks**

Instead of copying files back and forth, you can symlink the theme folder directly to your project's templates directory:

```bash
# Remove the theme folder if it exists
rm -rf mods/YourGroup_YourPlugin/theme

# Create a symlink to your project's resources
ln -s /path/to/your/project/src/main/resources mods/YourGroup_YourPlugin/theme
```

This way, edits to your source templates are immediately reflected without any copying. When you're ready to release, simply rebuild the JAR - the templates are already in the right place.

**Note:** Theme folder templates are not cached, so every request loads the template from disk. This is ideal for development but may impact performance in production. Remove theme folders (or symlinks) when deploying to production.

## Thymeleaf Syntax Reference

Common Thymeleaf attributes:

| Attribute   | Description               | Example                                      |
|-------------|---------------------------|----------------------------------------------|
| `th:text`   | Sets element text content | `<span th:text="${user.name}"></span>`       |
| `th:if`     | Conditional rendering     | `<div th:if="${items != null}">`             |
| `th:unless` | Inverse conditional       | `<div th:unless="${items.isEmpty()}">`       |
| `th:each`   | Iteration                 | `<tr th:each="item : ${items}">`             |
| `th:href`   | Dynamic href attribute    | `<a th:href="@{/path}">`                     |
| `th:class`  | Dynamic class attribute   | `<div th:class="${active ? 'active' : ''}">` |

For complete documentation, see the [Thymeleaf documentation](https://www.thymeleaf.org/doc/tutorials/3.1/usingthymeleaf.html).


# Plugin Developer Guide

This guide covers how to integrate your Hytale plugins with the Nitrado:WebServer plugin.

## Development Setup

### Maven

Nitrado:WebServer is published on the [HytaleModding.dev](https://HytaleModding.dev) Maven repository. Add it to your `pom.xml`:

```xml
<repositories>
    ...
    <repository>
        <id>hytalemodding-releases</id>
        <name>HytaleModding</name>
        <url>https://maven.hytalemodding.dev/releases</url>
    </repository>
    ...
</repositories>
```

Then add the dependency:

```xml
<dependencies>
    ...
    <dependency>
        <groupId>net.nitrado.hytale.plugins</groupId>
        <artifactId>nitrado-webserver</artifactId>
        <version>1.1.1</version>
        <scope>provided</scope>
    </dependency>
    ...
</dependencies>
```

The `<scope>provided</scope>` indicates that your resulting plugin does not need to bundle the web server code, as it is provided by the Hytale server at runtime.

### Gradle

[[ TODO: Example for Gradle ]]

### Plugin Manifest

In your plugin's `manifest.json`, define `Nitrado:WebServer` as a dependency:

```json
{
  "Dependencies": {
    "Nitrado:WebServer": ">=1.1.0"
  }
}
```

This will require _any_ version at or above 1.1.0. 

## Registering Routes

Below is example code taken from [Nitrado:Query](https://github.com/nitrado/hytale-plugin-query):

```java
public class QueryPlugin extends JavaPlugin {

  private WebServerPlugin webServerPlugin;

  public QueryPlugin(@Nonnull JavaPluginInit init) {
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
      webServerPlugin.addServlet(
              this,
              "",
              new QueryServlet(webServerPlugin, this, publicAddress)
      );
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

> **Best Practice:** The standard sidebar navigation (provided by the base layout) automatically links to `/<PluginGroup>/<PluginName>` for each registered plugin. You should provide an HTML landing page at this root path so users have a clear entry point when browsing via the web interface.

Also note that in the `shutdown()` method the plugin removes itself from the web server again. This ensures
that you can reload your plugin at runtime.

## Handling Permissions

Nitrado:WebServer provides built-in support for permission checks via annotations or filters.

For detailed information, see the [Permission Handling](permissions.md) guide.

## Templating

Nitrado:WebServer includes built-in HTML templating using Thymeleaf, with a base layout that provides consistent styling and navigation.

For detailed information, see the [Templating](templating.md) guide.

## Serving Static Files

The `StaticFileServlet` allows you to serve CSS, JavaScript, images, and other static assets from your plugin's JAR or filesystem.

For detailed information, see the [Serving Static Files](static-files.md) guide.

## Content Negotiation

Nitrado:WebServer provides utilities for HTTP content negotiation, allowing your endpoints to serve multiple response formats (e.g., HTML and JSON) and support API versioning through custom media types.

For detailed information, see the [Content Type Negotiation](content-type-negotiation.md) guide.


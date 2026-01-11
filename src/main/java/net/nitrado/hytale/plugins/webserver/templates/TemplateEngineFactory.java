package net.nitrado.hytale.plugins.webserver.templates;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;

/**
 * Factory for creating Thymeleaf {@link TemplateEngine} instances configured for use within the
 * WebServer plugin.
 * <p>
 * This factory provides template engines that support multiple template resolution strategies:
 * <ul>
 *   <li><strong>ClassLoader resolution</strong> - loads templates from the {@code templates/} folder in the plugin's bundled resources</li>
 *   <li><strong>Theme folder resolution</strong> - loads templates from {@code theme/templates/} resolved from the plugin's data directory</li>
 * </ul>
 * The theme folder resolver has higher priority, allowing server administrators to override bundled templates.
 * </p>
 *
 * <h3>Usage</h3>
 * <p>
 * Consumer plugins should obtain an instance of this factory via
 * {@link WebServerPlugin#getTemplateEngineFactory()} and then call {@link #getEngineFor(JavaPlugin)}
 * to get a template engine configured for their plugin:
 * </p>
 * <pre>{@code
 * TemplateEngineFactory factory = webServerPlugin.getTemplateEngineFactory();
 * TemplateEngine engine = factory.getEngineFor(myPlugin);
 * }</pre>
 *
 * @see TemplateEngine
 * @see WebServerPlugin#getTemplateEngineFactory()
 */
public final class TemplateEngineFactory {

    private final WebServerPlugin plugin;

    /**
     * Creates a new TemplateEngineFactory instance.
     *
     * @param plugin the WebServerPlugin instance that owns this factory
     */
    public TemplateEngineFactory(WebServerPlugin plugin) {
        this.plugin = plugin;
    }

    private JarTemplateResolver getJarTemplateResolverFor(JavaPlugin plugin) {
        var resolver = new JarTemplateResolver(plugin.getFile());
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setName(plugin.getIdentifier() + "_Jar");

        resolver.setCheckExistence(true);

        return resolver;
    }

    private FileTemplateResolver getThemeFolderTemplateResolverFor(JavaPlugin plugin) {
        var resolver = new FileTemplateResolver();

        var dataDir = plugin.getDataDirectory();

        var prefix = dataDir.resolve("theme/templates").toAbsolutePath() + "/";
        resolver.setPrefix(prefix);
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setName(plugin.getIdentifier() + "_ThemeFolder");

        plugin.getLogger().atWarning().log("Template resolver for prefix %s", prefix);

        resolver.setCheckExistence(true);

        return resolver;
    }

    /**
     * Creates a default {@link TemplateEngine} configured with resolvers for the WebServerPlugin's
     * own templates.
     * <p>
     * Resolution priority (lower order = higher priority):
     * <ol>
     *   <li>WebServerPlugin's theme folder resolver (order 15)</li>
     *   <li>WebServerPlugin's JAR resolver (order 20)</li>
     * </ol>
     * </p>
     *
     * @return a configured TemplateEngine for the WebServerPlugin's templates
     */
    public TemplateEngine getDefaultEngine() {
        var result = new TemplateEngine();

        var jarResolver = this.getJarTemplateResolverFor(this.plugin);
        jarResolver.setOrder(20);

        result.addTemplateResolver(jarResolver);

        var themeFolderResolver = this.getThemeFolderTemplateResolverFor(this.plugin);
        themeFolderResolver.setOrder(15);

        result.addTemplateResolver(themeFolderResolver);

        return result;
    }

    /**
     * Creates a {@link TemplateEngine} configured to resolve templates for the specified plugin,
     * with fallback to the WebServerPlugin's default templates.
     * <p>
     * Templates are expected to be in the {@code templates/} directory of the plugin's JAR
     * or in {@code <dataDir>/theme/templates/}, with {@code .html} suffix and UTF-8 encoding.
     * JAR-resolved templates are cached; theme folder templates are not cached to allow live editing.
     * </p>
     * <p>
     * Resolution priority (lower order = higher priority):
     * <ol>
     *   <li>Plugin's theme folder resolver (order 5)</li>
     *   <li>Plugin's JAR resolver (order 10)</li>
     *   <li>WebServerPlugin's theme folder resolver (order 15)</li>
     *   <li>WebServerPlugin's JAR resolver (order 20)</li>
     * </ol>
     * This allows plugins to override WebServerPlugin templates (e.g., layout.html) while
     * still falling back to defaults.
     * </p>
     *
     * @param plugin the plugin for which to create the template engine
     * @return a configured TemplateEngine with resolvers for both the specified plugin and WebServerPlugin
     */
    public TemplateEngine getEngineFor(JavaPlugin plugin) {
        var result = this.getDefaultEngine();

        var jarResolver = this.getJarTemplateResolverFor(plugin);
        jarResolver.setOrder(10);

        result.addTemplateResolver(jarResolver);

        var themeFolderResolver = this.getThemeFolderTemplateResolverFor(plugin);
        themeFolderResolver.setOrder(5);

        result.addTemplateResolver(themeFolderResolver);

        this.plugin.getLogger().atWarning().log("Number of resolvers: %d", result.getTemplateResolvers().size());

        return result;
    }
}

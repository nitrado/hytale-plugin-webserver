package net.nitrado.hytale.plugins.webserver.templates;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.io.IOException;

public final class TemplateEngineFactory {

    private final WebServerPlugin plugin;

    public TemplateEngineFactory(WebServerPlugin plugin) {
        this.plugin = plugin;
    }

    public ClassLoaderTemplateResolver getClassLoaderTemplateResolverFor(PluginBase plugin) {
        var resolver = new ClassLoaderTemplateResolver(plugin.getClass().getClassLoader());
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);
        resolver.setCharacterEncoding("UTF-8");

        resolver.setCheckExistence(true);

        return resolver;
    }

    public FileTemplateResolver getThemeFolderTemplateResolverFor(PluginBase plugin) throws IOException {
        var resolver = new FileTemplateResolver();

        var dataDir = plugin.getDataDirectory();

        resolver.setPrefix(dataDir.resolve("theme/templates").toString() + "/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        resolver.setCharacterEncoding("UTF-8");

        resolver.setCheckExistence(true);

        return resolver;
    }

    public TemplateEngine getDefaultEngine() throws IOException {
        var result = new TemplateEngine();

        var classLoaderResolver = this.getClassLoaderTemplateResolverFor(this.plugin);
        classLoaderResolver.setOrder(20);

        result.addTemplateResolver(classLoaderResolver);

        var themeFolderResolver = this.getThemeFolderTemplateResolverFor(this.plugin);
        themeFolderResolver.setOrder(15);

        result.addTemplateResolver(themeFolderResolver);

        return result;
    }

    public TemplateEngine getEngineFor(PluginBase plugin) throws IOException {
        var result = this.getDefaultEngine();

        var classLoaderResolver = this.getClassLoaderTemplateResolverFor(plugin);
        classLoaderResolver.setOrder(10);

        result.addTemplateResolver(classLoaderResolver);

        var themeFolderResolver = this.getThemeFolderTemplateResolverFor(plugin);
        themeFolderResolver.setOrder(5);

        result.addTemplateResolver(themeFolderResolver);

        return result;
    }
}

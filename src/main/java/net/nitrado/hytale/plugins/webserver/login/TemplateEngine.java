package net.nitrado.hytale.plugins.webserver.login;

import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;

/**
 * Template engine using Thymeleaf for HTML rendering.
 */
public class TemplateEngine {

    private static final org.thymeleaf.TemplateEngine engine;

    static {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix("");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);
        resolver.setCharacterEncoding("UTF-8");

        engine = new org.thymeleaf.TemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    /**
     * Load and render a template with variable substitution.
     *
     * @param templateName the template filename (e.g., "login.html")
     * @param context      object or map containing template data
     * @return rendered HTML string
     */
    public static String render(String templateName, Object context) {
        Context thymeleafContext = new Context();
        if (context instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                thymeleafContext.setVariable(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return engine.process(templateName, thymeleafContext);
    }

    /**
     * Load a template without variable substitution.
     */
    public static String render(String templateName) {
        return render(templateName, Map.of());
    }
}


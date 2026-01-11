package net.nitrado.hytale.plugins.webserver.servlets;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.Permissions;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import net.nitrado.hytale.plugins.webserver.authentication.HytaleUserPrincipal;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.util.*;

public abstract class TemplateServlet extends HttpServlet {

    private final TemplateEngine templateEngine;
    private JakartaServletWebApplication webApplication;
    private WebServerPlugin parentPlugin;

    @Override
    public void init() throws ServletException {
        super.init();webApplication =
                JakartaServletWebApplication.buildApplication(this.getServletContext());
    }

    public TemplateServlet(WebServerPlugin parentPlugin) {
        this.parentPlugin = parentPlugin;
        this.templateEngine = parentPlugin.getTemplateEngineFactory().getDefaultEngine();
    }

    public TemplateServlet(WebServerPlugin parentPlugin, JavaPlugin thisPlugin) {
        this.parentPlugin = parentPlugin;
        this.templateEngine = parentPlugin.getTemplateEngineFactory().getEngineFor(thisPlugin);
    }

    protected void renderTemplate(HttpServletRequest req, HttpServletResponse resp, String template) throws IOException {
        this.renderTemplate(req, resp, template, null);
    }

    protected void renderTemplate(HttpServletRequest req, HttpServletResponse resp, String template, Map<String, Object> variables) throws IOException {
        var exchange = webApplication.buildExchange(req, resp);
        var thymeleafContext = new WebContext(exchange);

        thymeleafContext.setVariables(getBuiltinVariables(req));

        if (variables != null) {
            thymeleafContext.setVariables(variables);
        }

        this.templateEngine.process(template, thymeleafContext, resp.getWriter());
    }

    protected Map<String, Object> getBuiltinVariables(HttpServletRequest req) {
        Map<String, Object> variables = new HashMap<>();

        var userPrincipal = req.getUserPrincipal();
        if (userPrincipal instanceof HytaleUserPrincipal hytaleUserPrincipal) {
            variables.put("user", hytaleUserPrincipal);
        }

        var version = TemplateServlet.class.getPackage().getImplementationVersion();
        variables.put("version", version != null ? version : "dev");

        if (
            userPrincipal instanceof HytaleUserPrincipal hytaleUserPrincipal
                    && hytaleUserPrincipal.hasPermission(Permissions.WEB_LIST_PLUGINS)
        ) {
            var pluginsByGroups = getPluginsByGroups();

            parentPlugin.getLogger().atWarning().log("%v", pluginsByGroups);

            variables.put("pluginsByGroups", pluginsByGroups);
            variables.put("pluginGroups", pluginsByGroups.keySet().stream().sorted().toList());
        }

        return variables;
    }

    protected TemplateEngine getTemplateEngine() {
        return this.templateEngine;
    }

    protected Map<String, List<PluginIdentifier>> getPluginsByGroups() {
        var result = new HashMap<String, List<PluginIdentifier>>();
        var registeredPlugins = this.parentPlugin.getRegisteredPlugins();

        for (var plugin:  registeredPlugins) {
            result.computeIfAbsent(plugin.getGroup(), k -> new ArrayList<>()).add(plugin);
        }

        for (var list : result.values()) {
            list.sort(Comparator.comparing(PluginIdentifier::getName));
        }

        return result;
    }
}

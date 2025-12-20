package net.nitrado.hytale.plugins.webserver;

import com.hypixel.hytale.logger.HytaleLogger;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import net.nitrado.hytale.plugins.webserver.filters.AuthFilter;
import net.nitrado.hytale.plugins.webserver.auth.AuthProvider;
import net.nitrado.hytale.plugins.webserver.filters.AuthRequiredFilter;
import net.nitrado.hytale.plugins.webserver.filters.PermissionsRequiredFilter;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class HandlerBuilder {

    private final HytaleLogger logger;
    private final WebServer webServer;
    private final String prefix;
    private final List<ServletMapping> servlets;
    private AuthProvider[] authProviders;

    private boolean authenticationRequired;
    private String[] requiredPermissions = new String[0];
    private String[] requiredPermissionsAny = new String[0];

    private record ServletMapping(HttpServlet servlet, String path) {}

    protected HandlerBuilder(HytaleLogger logger, WebServer webServer, String prefix) {
        this.logger = logger;
        this.webServer = webServer;
        this.prefix = prefix;
        this.servlets = new ArrayList<>();
    }

    public HandlerBuilder addServlet(HttpServlet servlet, String path) {
        this.servlets.add(new ServletMapping(servlet, path));
        return this;
    }

    public HandlerBuilder withAuthProviders(AuthProvider ...authProviders) {
        this.authProviders = authProviders;
        return this;
    }

    public HandlerBuilder requireAuthentication() {
        this.authenticationRequired = true;
        return this;
    }

    public HandlerBuilder requirePermissions(String... permissions) {
        this.requiredPermissions = permissions;
        return this;
    }

    public HandlerBuilder requireAnyPermissionOf(String ...permissions) {
        this.requiredPermissionsAny = permissions;
        return this;
    }

    public void register() throws Exception {
        this.logger.atInfo().log("Registering handler for prefix: %s", this.prefix);

        var context = this.webServer.getMainContext();
        String filterPathWildcard = this.prefix + "/*";
        String[] filterPaths = new String[] { this.prefix, filterPathWildcard };

        // Add auth filter for this prefix
        var authFilter = new AuthFilter(this.authProviders);
        for (String filterPath : filterPaths) {
            context.addFilter(new FilterHolder(authFilter), filterPath, EnumSet.of(DispatcherType.REQUEST));
        }

        if (this.authenticationRequired && this.requiredPermissions.length == 0) {
            for (String filterPath : filterPaths) {
                context.addFilter(new FilterHolder(new AuthRequiredFilter()), filterPath, EnumSet.of(DispatcherType.REQUEST));
            }
        }

        if (this.requiredPermissions.length > 0) {
            for (String filterPath : filterPaths) {
                context.addFilter(new FilterHolder(new PermissionsRequiredFilter(this.requiredPermissions)), filterPath, EnumSet.of(DispatcherType.REQUEST));
            }
        }

        if (this.requiredPermissionsAny.length > 0) {
            for (String filterPath : filterPaths) {
                context.addFilter(new FilterHolder(new PermissionsRequiredFilter(true, this.requiredPermissionsAny)), filterPath, EnumSet.of(DispatcherType.REQUEST));
            }
        }

        // Add servlets at their prefixed paths
        for (var mapping : this.servlets) {
            String fullPath = this.prefix + mapping.path();
            context.addServlet(new ServletHolder(mapping.servlet()), fullPath);
            this.logger.atInfo().log("Added servlet at: %s", fullPath);
        }

        // Mark prefix as registered
        this.webServer.registerPrefix(this.prefix);
    }
}

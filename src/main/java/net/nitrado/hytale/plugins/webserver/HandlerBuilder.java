package net.nitrado.hytale.plugins.webserver;

import jakarta.servlet.http.HttpServlet;
import net.nitrado.hytale.plugins.webserver.filters.AuthFilter;
import net.nitrado.hytale.plugins.webserver.auth.AuthProvider;
import net.nitrado.hytale.plugins.webserver.filters.AuthRequiredFilter;
import net.nitrado.hytale.plugins.webserver.filters.PermissionsRequiredFilter;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

public class HandlerBuilder {

    private final WebServer webServer;
    private final ServletContextHandler handler;
    private final List<Object> beans;
    private final AuthProvider[] authProviders;

    private boolean authenticationRequired;
    private String[] requiredPermissions = new String[0];
    private String[] requiredPermissionsAny = new String[0];

    protected HandlerBuilder(WebServer webServer, String prefix) {
        this.webServer = webServer;

        this.handler = new ServletContextHandler(prefix);
        this.beans = new LinkedList<>();
        this.authProviders = this.webServer.getDefaultAuthProviders();
    }

    public HandlerBuilder addServlet(HttpServlet servlet, String path)  {
        this.handler.addServlet(servlet, path);

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
        this.webServer.getLogger().at(Level.INFO).log("Registering handler for " + this.handler.getContextPath());

        var authFilter = new AuthFilter(this.authProviders);
        this.handler.addFilter(authFilter, "/*", java.util.EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));

        if (this.authenticationRequired && this.requiredPermissions.length == 0) {
            this.handler.addFilter(new AuthRequiredFilter(), "/*", java.util.EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));
        }

        if (this.requiredPermissions.length > 0) {
            this.handler.addFilter(new PermissionsRequiredFilter(this.requiredPermissions), "/*", java.util.EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));
        }

        if (this.requiredPermissionsAny.length > 0) {
            this.handler.addFilter(new PermissionsRequiredFilter(true,  this.requiredPermissionsAny), "/*", java.util.EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));
        }

        this.webServer.register(this.handler, this.beans);
        this.handler.start();
    }
}

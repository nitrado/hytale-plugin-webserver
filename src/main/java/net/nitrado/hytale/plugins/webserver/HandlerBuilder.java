package net.nitrado.hytale.plugins.webserver;

import jakarta.servlet.http.HttpServlet;
import org.eclipse.jetty.ee10.servlet.security.ConstraintMapping;
import org.eclipse.jetty.ee10.servlet.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.Constraint;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.UserStore;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Password;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

public class HandlerBuilder {

    private final WebServer webServer;
    private final ServletContextHandler handler;
    private final List<Object> beans;

    protected HandlerBuilder(WebServer webServer, String prefix) {
        this.webServer = webServer;

        this.handler = new ServletContextHandler(prefix);
        this.beans = new LinkedList<>();
    }

    public HandlerBuilder addServlet(HttpServlet servlet, String path)  {
        this.handler.addServlet(servlet, path);

        return this;
    }

    public HandlerBuilder withBasicAuth(String user, String pass) {
        // 1. Create a UserStore and add users to it
        UserStore userStore = new UserStore();
        userStore.addUser(user, new Password(pass), new String[]{"user"});

        // 2. Create a LoginService and set the UserStore
        HashLoginService loginService = new HashLoginService();
        loginService.setName("MyRealm");
        loginService.setUserStore(userStore); // <-- Use setUserStore

        // 3. Define the security constraint
        Constraint constraint = new Constraint.Builder()
                .name(user)
                .roles("user")
                .build();

        // 4. Map the constraint to a path
        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setConstraint(constraint);
        mapping.setPathSpec("/*"); // Protect all paths

        // 5. Create a security handler
        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        securityHandler.setAuthenticator(new BasicAuthenticator());
        securityHandler.setRealmName("MyRealm");
        securityHandler.addConstraintMapping(mapping);
        securityHandler.setLoginService(loginService);

        this.handler.setSecurityHandler(securityHandler);

        this.beans.add(loginService);

        return this;
    }

    public void register() throws Exception {
        this.webServer.getLogger().at(Level.INFO).log("Registering handler for " + this.handler.getContextPath());

        this.webServer.register(this.handler, this.beans);
        this.handler.start();
    }
}

package net.nitrado.hytale.plugins.webserver;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.util.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.config.WebServerConfig;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.Level;

public class WebServer extends JavaPlugin {

    public WebServer(@Nonnull JavaPluginInit init) {
        super(init);

        this.contexts = new ContextHandlerCollection();
    }

    private final Config<WebServerConfig> config = withConfig(WebServerConfig.CODEC);
    private final ContextHandlerCollection contexts;
    private Server server;

    @Override
    protected void setup() {
        var l = getLogger();
        var addr = new InetSocketAddress(config.get().getBindHost(), config.get().getBindPort());

        l.at(Level.INFO).log("Binding WebServer to " + addr);

        this.server = new Server(addr);
        this.server.setHandler(this.contexts);

        try {
            this.server.start();
        } catch (Exception e) {
            l.at(Level.SEVERE).log(e.getMessage());
        }
    }

    @Override
    protected void shutdown() {
        try {
            this.server.stop();
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to stop WebServer: " + e.getMessage());
        }
    }

    public HandlerBuilder createHandlerBuilder(String prefix) throws PrefixAlreadyRegisteredException{
        if (this.hasHandlerFor(prefix)) {
            throw new PrefixAlreadyRegisteredException();
        }

        return new HandlerBuilder(this, prefix);
    }

    private boolean hasHandlerFor(String prefix) {
        var handlers = this.contexts.getHandlers();

        if (handlers == null) {
            return false;
        }

        for (Handler handler : handlers) {
            if (handler instanceof ContextHandler contextHandler) {
                String contextPath = contextHandler.getContextPath();

                if (contextPath != null && (contextPath.equals(prefix) || contextPath.startsWith(prefix + "/") || prefix.startsWith(contextPath + "/"))) {
                    return true;
                }
            }
        }

        return false;
    }

    protected void register(Handler handler, Object... beans) throws Exception {
        contexts.addHandler(handler);
        for (var o : beans) {
            this.server.addBean(o);
        }
    }
}

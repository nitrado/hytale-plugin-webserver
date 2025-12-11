package net.nitrado.hytale.plugins.webserver;

import com.hypixel.hytale.server.core.command.CommandManager;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.util.Config;
import net.nitrado.hytale.plugins.webserver.auth.AuthProvider;
import net.nitrado.hytale.plugins.webserver.auth.BasicAuthProvider;
import net.nitrado.hytale.plugins.webserver.auth.store.CombinedCredentialValidator;
import net.nitrado.hytale.plugins.webserver.auth.store.CredentialValidator;
import net.nitrado.hytale.plugins.webserver.auth.store.JsonPasswordStore;
import net.nitrado.hytale.plugins.webserver.auth.store.UserCredentialStore;
import net.nitrado.hytale.plugins.webserver.commands.WebServerCommand;
import net.nitrado.hytale.plugins.webserver.config.WebServerConfig;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
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

    private CredentialValidator userCredentialValidator;
    private CredentialValidator serviceAccountCredentialValidator;

    private UserCredentialStore userCredentialStore;
    private UserCredentialStore serviceAccountCredentialStore;

    @Override
    protected void setup() {
        var l = getLogger();
        var addr = new InetSocketAddress(config.get().getBindHost(), config.get().getBindPort());

        l.at(Level.INFO).log("Binding WebServer to " + addr);

        this.server = new Server(addr);
        this.server.setHandler(this.contexts);

        try {
            this.setupAuthStores();
        } catch (IOException e) {
            l.at(Level.SEVERE).log("failed to setup stores for webserver credentials: %s", e.getMessage());
            return;
        }

        this.setupCommands();

        try {
            this.server.start();
        } catch (Exception e) {
            l.at(Level.SEVERE).log(e.getMessage());
        }
    }

    protected void setupAnonymousUser() {
        PermissionsModule.get().addUserToGroup(new UUID(0,0), "ANONYMOUS");
    }

    protected void setupCommands() {
        CommandManager.get().register(new WebServerCommand(this));
    }

    protected void setupAuthStores() throws IOException {
        // TODO: Make implementation configurable somehow?

        var dataDir = getDataDirectory();

        var serviceAccountStore = new JsonPasswordStore(dataDir.resolve("serviceaccounts.json"), getLogger().getSubLogger("ServiceAccountCredentialStore"));
        serviceAccountStore.load();

        var userStore = new JsonPasswordStore(dataDir.resolve("users.json"), getLogger().getSubLogger("UserCredentialStore"));
        userStore.load();

        this.serviceAccountCredentialStore = serviceAccountStore;
        this.serviceAccountCredentialValidator = serviceAccountStore;

        this.userCredentialStore = userStore;
        this.userCredentialValidator = userStore;
    }

    @Override
    protected void start() {
        this.setupAnonymousUser();
    }

    @Override
    protected void shutdown() {
        try {
            this.server.stop();
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to stop WebServer: " + e.getMessage());
        }
    }

    public HandlerBuilder createHandlerBuilder(PluginBase plugin) throws PrefixAlreadyRegisteredException{
        var id = plugin.getIdentifier();
        var prefix = String.format("/%s/%s", id.getGroup().toLowerCase(), id.getName().toLowerCase());

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
        if (handler instanceof ContextHandler contextHandler) {
            getLogger().atInfo().log("registered handler at prefix: %s", contextHandler.getContextPath());
        }

        contexts.addHandler(handler);
        for (var o : beans) {
            this.server.addBean(o);
        }
    }

    public UserCredentialStore getUserCredentialStore() {
        return this.userCredentialStore;
    }

    public UserCredentialStore getServiceAccountCredentialStore() {
        return this.serviceAccountCredentialStore;
    }

    public AuthProvider[] getDefaultAuthProviders() {
        var combined = new CombinedCredentialValidator();
        combined.add(this.userCredentialValidator);
        combined.add(this.serviceAccountCredentialValidator);

        return new AuthProvider[]{
            new BasicAuthProvider(combined),
        };
    }
}

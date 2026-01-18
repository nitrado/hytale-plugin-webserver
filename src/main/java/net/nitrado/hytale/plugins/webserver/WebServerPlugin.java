package net.nitrado.hytale.plugins.webserver;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.PermissionProvider;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.util.Config;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import net.nitrado.hytale.plugins.webserver.authentication.AuthProvider;
import net.nitrado.hytale.plugins.webserver.authentication.internal.AuthFilter;
import net.nitrado.hytale.plugins.webserver.authentication.internal.BasicAuthProvider;
import net.nitrado.hytale.plugins.webserver.authentication.internal.SessionAuthProvider;
import net.nitrado.hytale.plugins.webserver.authentication.store.*;
import net.nitrado.hytale.plugins.webserver.commands.WebServerCommand;
import net.nitrado.hytale.plugins.webserver.config.WebServerConfig;
import net.nitrado.hytale.plugins.webserver.servlets.internal.IndexServlet;
import net.nitrado.hytale.plugins.webserver.servlets.internal.LoginServlet;
import net.nitrado.hytale.plugins.webserver.servlets.internal.LogoutServlet;
import net.nitrado.hytale.plugins.webserver.servlets.StaticFileServlet;
import net.nitrado.hytale.plugins.webserver.templates.TemplateEngineFactory;
import org.bson.Document;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Plugin that provides an embedded Jetty web server with authentication and authorization support.
 * <p>
 * This plugin manages HTTP servlets, authentication providers, and service accounts for the
 * Hytale server. Other plugins can register their own servlets through this plugin's API,
 * and they will be automatically prefixed with the plugin's group and name (e.g., {@code /group/name/path}).
 * </p>
 * <p>
 * This class is the primary entry point for consumer plugins. Use the following methods:
 * <ul>
 *   <li>{@link #addServlet} - Register an HTTP servlet</li>
 *   <li>{@link #removeServlet} / {@link #removeServlets} - Unregister servlets</li>
 *   <li>{@link #setAuthProviders} - Configure custom authentication</li>
 *   <li>{@link #getDefaultAuthProviders} - Get the default auth providers</li>
 * </ul>
 * </p>
 */
public final class WebServerPlugin extends JavaPlugin {

    /**
     * Creates a new WebServerPlugin instance.
     *
     * @param init the plugin initialization data provided by the server
     */
    public WebServerPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    private final Config<WebServerConfig> config = withConfig(WebServerConfig.CODEC);
    private WebServer webServer;

    private CredentialValidator userCredentialValidator;
    private CredentialValidator serviceAccountCredentialValidator;

    private UserCredentialStore userCredentialStore;
    private UserCredentialStore serviceAccountCredentialStore;
    private TemplateEngineFactory templateEngineFactory;
    private LoginCodeStore loginCodeStore;

    private Path dataDir;

    @Override
    protected void setup() {
        var l = getLogger();
        var cfg = config.get();

        this.dataDir = getDataDirectory();

        this.templateEngineFactory = new TemplateEngineFactory(this);
        this.webServer = new WebServer(l.getSubLogger("WebServer"), cfg, dataDir);

        try {
            this.setupAuthStores();
        } catch (IOException e) {
            l.at(Level.SEVERE).withCause(e).log("Failed to setup stores for webserver credentials");
            return;
        }

        this.loginCodeStore = new LoginCodeStore();

        try {
            this.setupBuiltinRoutes();
        } catch (IOException e) {
            l.at(Level.SEVERE).withCause(e).log("Failed to setup built-in routes");
            return;
        }

        this.setupCommands();
        try {
            this.webServer.addServlet(
                    new StaticFileServlet(this.dataDir.resolve("theme/static"), "static", WebServer.class.getClassLoader()), "/static/*");
        } catch (IllegalPathSpecException e) {}
    }

    @Override
    protected void start() {
        this.setupAnonymousUser();

        try {
            this.importServiceAccounts();
        } catch (IOException e) {
            getLogger().atSevere().withCause(e).log("Failed to import service accounts for webserver: %s", e.getMessage());
        }

        try {
            this.webServer.start();
        } catch (Exception e) {
            getLogger().atSevere().log(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    void setupAnonymousUser() {
        PermissionsModule.get().addUserToGroup(new UUID(0,0), "ANONYMOUS");
    }

    void setupCommands() {
        CommandManager.get().register(new WebServerCommand(this.loginCodeStore));
    }

    void setupAuthStores() throws IOException {
        // TODO: Make implementation configurable somehow?

        var dataDir = getDataDirectory();

        var serviceAccountStore = new JsonPasswordStore(dataDir.resolve("store/serviceaccounts.json"), getLogger().getSubLogger("ServiceAccountCredentialStore"));
        serviceAccountStore.load();

        var userStore = new JsonPasswordStore(dataDir.resolve("store/users.json"), getLogger().getSubLogger("UserCredentialStore"));
        userStore.load();

        this.serviceAccountCredentialStore = serviceAccountStore;
        this.serviceAccountCredentialValidator = serviceAccountStore;

        this.userCredentialStore = userStore;
        this.userCredentialValidator = userStore;
    }

    void setupBuiltinRoutes() throws IOException {
        try {
            this.webServer.addServlet(new IndexServlet(
                    this
            ), "", new AuthFilter(getDefaultAuthProviders()));

            this.webServer.addServlet(new LoginServlet(
                    this,
                    getLogger().getSubLogger("LoginServlet"),
                    this.userCredentialStore,
                    this.userCredentialValidator,
                    this.loginCodeStore
            ), "/login", new AuthFilter(getDefaultAuthProviders()));

            this.webServer.addServlet(
                    new LogoutServlet(getLogger().getSubLogger("LogoutServlet")), "/logout", new AuthFilter(getDefaultAuthProviders()));
        } catch (IllegalPathSpecException e) {
            // we don't make mistakes
        }
    }

    @Override
    protected void shutdown() {
        this.webServer.stop();
    }

    /**
     * Returns the default authentication providers used for servlet authentication.
     * <p>
     * The returned array contains providers that are tried in order:
     * <ol>
     *   <li>{@link SessionAuthProvider} - authenticates via HTTP session cookies</li>
     *   <li>{@link BasicAuthProvider} - authenticates via HTTP Basic Authentication using
     *       credentials from service account store</li>
     * </ol>
     * </p>
     *
     * @return an array of authentication providers in priority order
     */
    public AuthProvider[] getDefaultAuthProviders() {
        var combined = new CombinedCredentialValidator();
        combined.add(this.serviceAccountCredentialValidator);

        return new AuthProvider[]{
                new SessionAuthProvider(getLogger().getSubLogger("SessionAuthProvider")),
                new BasicAuthProvider(combined),
        };
    }

    /**
     * Imports all service accounts from JSON files in the provisioning directory.
     * <p>
     * This method scans the {@code provisioning/} directory under the plugin's data directory
     * for files matching the pattern {@code *.serviceaccount.json}. Each file is parsed and
     * the service account is created or updated. If a service account already exists, it is
     * deleted and recreated to ensure permissions and groups are reset.
     * </p>
     * <p>
     * Service account JSON files should contain:
     * <ul>
     *   <li>{@code Name} - the service account name</li>
     *   <li>{@code Enabled} - whether the account should be active</li>
     *   <li>{@code PasswordHash} - bcrypt-hashed password</li>
     *   <li>{@code Groups} - list of permission groups</li>
     *   <li>{@code Permissions} - list of individual permissions</li>
     * </ul>
     * </p>
     *
     * @throws IOException if the provisioning directory cannot be created or read
     */
    public void importServiceAccounts() throws IOException {
        var dir = this.dataDir.resolve("provisioning");

        if (!Files.exists(dir)) {
            Files.createDirectory(dir);
        }

        Files.list(dir).forEach(file -> {
            if (file.getFileName().toString().endsWith(".serviceaccount.json")) {
                getLogger().atInfo().log("Importing service account file %s", file.getFileName());
                try {
                    this.importServiceAccount(file);
                } catch (Exception e) {
                    this.getLogger().atSevere().withCause(e).log("Failed to import service account file %s", file.toString());
                }
            }
        });
    }

    /**
     * Sets custom authentication providers for a plugin's servlets.
     * <p>
     * This method allows a plugin to override the default authentication providers
     * ({@link #getDefaultAuthProviders()}) with custom ones. This must be called
     * <strong>before</strong> registering any servlets with {@link #addServlet}, as the
     * auth providers are applied when the first servlet is registered for a plugin.
     * </p>
     * <p>
     * It is generally recommended to retrieve the list of default authentication providers
     * with ({@link #getDefaultAuthProviders()}) and append any additional authentication
     * providers instead of fully replacing the default list.
     * </p>
     * <p>
     * If any servlets have already been registered for this plugin, this method has no effect.
     * </p>
     *
     * @param plugin        the plugin whose authentication providers to set
     * @param authProviders the authentication providers to use for this plugin's servlets
     */
    public void setAuthProviders(@Nonnull PluginBase plugin, AuthProvider ...authProviders) {
        getWebServer().setAuthProviders(plugin, authProviders);
    }

    /**
     * Registers an HTTP servlet for a plugin at the specified path.
     * <p>
     * The servlet will be mounted at a path prefixed with the plugin's group and name:
     * {@code /{group}/{name}/{pathSpec}}. For example, if a plugin with group "myplugins"
     * and name "admin" registers a servlet at "/users", the full path will be
     * {@code /myplugins/admin/users}.
     * </p>
     * <p>
     * Authentication is automatically applied using either custom providers set via
     * {@link #setAuthProviders} or the default providers from {@link #getDefaultAuthProviders()}.
     * Authorization is handled via Hytale's permissions system.
     * </p>
     *
     * @param plugin   the plugin registering the servlet
     * @param pathSpec the path specification (must be empty or start with "/")
     * @param servlet  the HTTP servlet to register
     * @param filters  optional HTTP filters to apply to this path
     * @throws IllegalPathSpecException if the pathSpec is invalid (non-empty and doesn't start with "/")
     */
    public void addServlet(@Nonnull PluginBase plugin, String pathSpec, HttpServlet servlet, Filter ...filters) throws IllegalPathSpecException {
        getWebServer().addServlet(plugin, pathSpec, servlet, filters, getDefaultAuthProviders());
    }

    /**
     * Removes a previously registered servlet for a plugin at the specified path.
     * <p>
     * The pathSpec should match the one used when registering the servlet with
     * {@link #addServlet}. This also removes any filters that were associated with
     * this specific path.
     * </p>
     *
     * @param plugin   the plugin that registered the servlet
     * @param pathSpec the path specification of the servlet to remove
     * @throws IllegalPathSpecException if the pathSpec is invalid (non-empty and doesn't start with "/")
     */
    public void removeServlet(@Nonnull PluginBase plugin, String pathSpec)  throws IllegalPathSpecException {
        getWebServer().removeServlet(plugin, pathSpec);
    }

    /**
     * Removes all servlets registered by a plugin.
     * <p>
     * This method removes all servlets and their associated filters that were
     * registered by the specified plugin, including the authentication filters
     * for the plugin's path prefix.
     * </p>
     * <p>
     * Plugins should call this method as part of their {@code shutdown()} method.
     * </p>
     *
     * @param plugin the plugin whose servlets should be removed
     */
    public void removeServlets(@Nonnull PluginBase plugin) {
        getWebServer().removeServlets(plugin);
    }

    /**
     * Returns the {@link TemplateEngineFactory} for creating Thymeleaf template engines.
     * <p>
     * Consumer plugins should use this factory to obtain a {@link org.thymeleaf.TemplateEngine}
     * configured for their plugin by calling {@link TemplateEngineFactory#getEngineFor(JavaPlugin)}.
     * </p>
     *
     * @return the template engine factory instance
     * @see TemplateEngineFactory#getEngineFor(JavaPlugin)
     */
    public TemplateEngineFactory getTemplateEngineFactory() {
        return this.templateEngineFactory;
    }

    public Set<PluginIdentifier> getRegisteredPlugins() {
        return this.webServer.getRegisteredPlugins();
    }

    UUID createServiceAccountBcrypt(String name, String passwordHash) throws IOException {
        return this.createServiceAccountBcrypt(UUID.randomUUID(), name, passwordHash);
    }

    UUID createServiceAccountBcrypt(@Nonnull UUID uuid, String name, String passwordHash) throws IOException {
        if (!name.startsWith("serviceaccount.")) {
            name = "serviceaccount." + name;
        }

        try {
            this.serviceAccountCredentialStore.importUserCredential(uuid, name, passwordHash);
            PermissionsModule.get().addUserToGroup(uuid, "SERVICE_ACCOUNT");
            return uuid;

        } catch (IOException e) {
            getLogger().at(Level.SEVERE).log("failed to create service account credentials: %s", e.getMessage());
            throw e;
        }
    }

    private void importServiceAccount(Path file) throws IOException {
        String jsonString = Files.readString(file);
        Document document = Document.parse(jsonString);

        var name = document.getString("Name");

        // Delete the service account every time to also reset its permissions and groups
        var uuid = this.deleteServiceAccount(name);

        var enabled = document.getBoolean("Enabled");
        if (!enabled) {
            return;
        }

        var passwordHash = document.getString("PasswordHash");

        if (uuid == null) {
            uuid = this.createServiceAccountBcrypt(name, passwordHash);
        } else {
            this.createServiceAccountBcrypt(uuid, name, passwordHash);
        }

        var groups = document.getList("Groups", String.class);
        var permissions = document.getList("Permissions", String.class);

        for (String group : groups) {
            PermissionsModule.get().addUserToGroup(uuid, group);
        }

        PermissionsModule.get().addUserPermission(uuid, Set.copyOf(permissions));
    }

    void deleteServiceAccount(UUID uuid) throws IOException {
        try {
            this.serviceAccountCredentialStore.deleteUserCredential(uuid);
            var perm = PermissionsModule.get();

            for (PermissionProvider provider : perm.getProviders()) {
                var groups = Set.copyOf(provider.getGroupsForUser(uuid));

                for (var group : groups) {
                    getLogger().atInfo().log("Removing %s from group %s", uuid.toString(), group);
                    provider.removeUserFromGroup(uuid, group);
                }

                var permissions = provider.getUserPermissions(uuid);
                getLogger().atInfo().log("Removing %s from permissions %s", uuid.toString(), permissions);
                provider.removeUserPermissions(uuid, permissions);
            }

        } catch (IOException e) {
            getLogger().at(Level.SEVERE).log("failed to delete service account: %s", e.getMessage());
            throw e;
        }
    }

    UUID deleteServiceAccount(String name) throws IOException {
        if (!name.startsWith("serviceaccount.")) {
            name = "serviceaccount." + name;
        }

        var uuid = this.serviceAccountCredentialStore.getUUIDByName(name);
        if (uuid == null) {
            return null;
        }

        this.deleteServiceAccount(uuid);
        return uuid;
    }

    private WebServer getWebServer() {
        return webServer;
    }
}

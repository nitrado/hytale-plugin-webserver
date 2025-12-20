package net.nitrado.hytale.plugins.webserver;

import com.hypixel.hytale.logger.HytaleLogger;
import net.nitrado.hytale.plugins.webserver.cert.CertificateProvider;
import net.nitrado.hytale.plugins.webserver.config.WebServerConfig;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class WebServer {
    private final ServletContextHandler mainContext;
    private final Set<String> registeredPrefixes;
    private Server server;
    private HytaleLogger logger;

    public WebServer(HytaleLogger logger, WebServerConfig config, Path dataDir) {
        this.logger = logger;
        this.registeredPrefixes = new HashSet<>();

        this.mainContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        this.mainContext.setContextPath("/");

        var tlsConfig = config.getTls();
        var addr = new InetSocketAddress(config.getBindHost(), config.getBindPort());

        this.logger.atInfo().log("Binding WebServer to " + addr);

        this.server = new Server();

        ServerConnector connector;
        if (tlsConfig.isInsecure()) {
            this.logger.atWarning().log("TLS is disabled - using insecure plain HTTP!");
            connector = new ServerConnector(this.server);
        } else {
            connector = this.createTLSConnector(config, dataDir);
        }

        connector.setHost(addr.getHostName());
        connector.setPort(addr.getPort());

        this.server.addConnector(connector);
        this.server.setHandler(this.mainContext);
    }

    /**
     * Returns the main servlet context handler.
     * Plugins add their servlets and filters to this shared context.
     */
    public ServletContextHandler getMainContext() {
        return this.mainContext;
    }

    /**
     * Adds a servlet at the specified path.
     */
    public void addServlet(HttpServlet servlet, String pathSpec) {
        this.mainContext.addServlet(new ServletHolder(servlet), pathSpec);
        this.logger.atInfo().log("Added servlet at path: %s", pathSpec);
    }

    /**
     * Adds a filter at the specified path.
     */
    public void addFilter(Filter filter, String pathSpec) {
        this.mainContext.addFilter(new FilterHolder(filter), pathSpec, EnumSet.of(DispatcherType.REQUEST));
    }

    public void start() throws Exception {
        for (var connector : this.server.getConnectors()) {
            if (connector instanceof ServerConnector sc) {
                this.logger.atInfo().log("WebServer listening on %s:%d", sc.getHost(), sc.getPort());
            }
        }
        this.server.start();
    }

    public void stop() {
        try {
            this.server.stop();
        } catch (Exception e) {
            this.logger.atSevere().withCause(e).log("Failed to stop WebServer");
        }
    }

    protected ServerConnector createTLSConnector(WebServerConfig config, Path dataDir) {
        SSLContext sslContext;
        try {
            sslContext = this.createSSLContext(config, dataDir);
        } catch (Exception e) {
            this.logger.atSevere().log("Failed to create SSL context: " + e.getMessage());
            throw new RuntimeException(e);
        }

        SslContextFactory.Server ssl = new SslContextFactory.Server();
        ssl.setSslContext(sslContext);
        ssl.setSniRequired(false);

        HttpConfiguration httpsConfig = new HttpConfiguration();
        SecureRequestCustomizer secureRequestCustomizer = new SecureRequestCustomizer();
        secureRequestCustomizer.setSniRequired(false);
        secureRequestCustomizer.setSniHostCheck(false);
        httpsConfig.addCustomizer(secureRequestCustomizer);

        return new ServerConnector(this.server,
                new SslConnectionFactory(ssl, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpsConfig));
    }

    protected SSLContext createSSLContext(WebServerConfig config, Path dataDir) throws Exception {
        var tlsConfig = config.getTls();

        CertificateProvider provider = tlsConfig.createCertificateProvider(
                config.getBindHost(),
                dataDir,
                msg -> this.logger.atInfo().log(msg)
        );
        this.logger.at(Level.INFO).log("Using certificate provider: " + tlsConfig.getCertificateProvider());
        return provider.createSSLContext();
    }

    /**
     * Checks if a handler is already registered for the given prefix.
     * Prevents overlapping path registrations.
     */
    public boolean hasHandlerFor(String prefix) {
        for (String registered : this.registeredPrefixes) {
            if (registered.equals(prefix) ||
                registered.startsWith(prefix + "/") ||
                prefix.startsWith(registered + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers a prefix as being used by a plugin.
     */
    public void registerPrefix(String prefix) {
        this.registeredPrefixes.add(prefix);
        this.logger.atInfo().log("Registered prefix: %s", prefix);
    }
}

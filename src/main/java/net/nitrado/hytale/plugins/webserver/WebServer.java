package net.nitrado.hytale.plugins.webserver;

import com.hypixel.hytale.logger.HytaleLogger;
import net.nitrado.hytale.plugins.webserver.cert.CertificateProvider;
import net.nitrado.hytale.plugins.webserver.config.WebServerConfig;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.logging.Level;

public class WebServer {
    private final ContextHandlerCollection contexts;
    private Server server;
    private HytaleLogger logger;

    public WebServer(HytaleLogger logger, WebServerConfig config, Path dataDir) {
        this.logger = logger;
        this.contexts = new ContextHandlerCollection();

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
        this.server.setHandler(this.contexts);
    }

    public void start() throws Exception {
        for (var connector :  this.server.getConnectors()) {
            if (connector instanceof ServerConnector sc) {
                this.logger.atInfo().log("WebServer listening on on %s:%d", sc.getHost(), sc.getPort());
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

    public boolean hasHandlerFor(String prefix) {
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

    public void register(Handler handler, Object... beans) throws Exception {
        if (handler instanceof ContextHandler contextHandler) {
            this.logger.atInfo().log("registered handler at prefix: %s", contextHandler.getContextPath());
        }

        contexts.addHandler(handler);
        for (var o : beans) {
            this.server.addBean(o);
        }
    }
}

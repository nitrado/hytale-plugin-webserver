package net.nitrado.hytale.plugins.webserver.cert;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import com.sun.net.httpserver.HttpServer;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * A CertificateProvider implementation that obtains certificates from Let's Encrypt
 * using the ACME protocol with HTTP-01 challenge.
 *
 * <p>This provider automatically starts a temporary HTTP server on port 80 during
 * certificate issuance/renewal to handle the ACME HTTP-01 challenge.</p>
 *
 * <p><strong>Important:</strong> Let's Encrypt has rate limits. Use staging for testing.
 * Port 80 must be available and accessible from the internet.</p>
 */
public final class LetsEncryptCertificateProvider implements CertificateProvider {

    /** Production ACME server URI */
    public static final String LETSENCRYPT_PRODUCTION = "acme://letsencrypt.org";
    /** Staging ACME server URI (for testing, issues untrusted certs) */
    public static final String LETSENCRYPT_STAGING = "acme://letsencrypt.org/staging";

    private static final int CHALLENGE_PORT = 80;
    private static final Duration RENEWAL_THRESHOLD = Duration.ofDays(30);
    private static final int MAX_POLL_ATTEMPTS = 60;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(3);

    private final String domain;
    private final String acmeServerUri;
    private final Path storagePath;
    private final Consumer<String> logger;

    // Pending HTTP-01 challenges: token -> authorization content
    private final Map<String, String> pendingChallenges = new ConcurrentHashMap<>();

    private KeyPair accountKeyPair;
    private KeyPair domainKeyPair;
    private Instant certificateExpiry;
    private HttpServer challengeServer;

    /**
     * Creates a Let's Encrypt certificate provider.
     *
     * @param domain        the domain name for the certificate
     * @param storagePath   path to store account keys and certificates
     * @param useProduction true for production, false for staging (recommended for testing)
     */
    public LetsEncryptCertificateProvider(String domain, Path storagePath, boolean useProduction) {
        this(domain, storagePath, useProduction, msg -> {});
    }

    /**
     * Creates a Let's Encrypt certificate provider with logging.
     *
     * @param domain        the domain name for the certificate
     * @param storagePath   path to store account keys and certificates
     * @param useProduction true for production, false for staging
     * @param logger        consumer for log messages
     */
    public LetsEncryptCertificateProvider(String domain, Path storagePath, boolean useProduction,
                                          Consumer<String> logger) {
        this.domain = domain;
        this.storagePath = storagePath;
        this.acmeServerUri = useProduction ? LETSENCRYPT_PRODUCTION : LETSENCRYPT_STAGING;
        this.logger = logger;
    }

    /**
     * Returns the map of pending HTTP-01 challenges.
     * These must be served at {@code /.well-known/acme-challenge/<token>} returning the value.
     *
     * @return map of token to authorization content
     */
    public Map<String, String> getPendingChallenges() {
        return pendingChallenges;
    }

    @Override
    public SSLContext createSSLContext() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        Files.createDirectories(storagePath);

        loadOrCreateKeyPairs();

        if (!loadExistingCertificate()) {
            obtainNewCertificate();
        }

        return buildSSLContext();
    }

    @Override
    public boolean refresh() throws Exception {
        if (certificateExpiry == null) {
            return false;
        }

        Instant renewalTime = certificateExpiry.minus(RENEWAL_THRESHOLD);
        if (Instant.now().isAfter(renewalTime)) {
            logger.accept("Certificate expires soon, initiating renewal...");
            obtainNewCertificate();
            return true;
        }
        return false;
    }

    private void loadOrCreateKeyPairs() throws Exception {
        Path accountKeyPath = storagePath.resolve("account.key");
        Path domainKeyPath = storagePath.resolve("domain.key");

        if (Files.exists(accountKeyPath)) {
            try (Reader reader = Files.newBufferedReader(accountKeyPath)) {
                accountKeyPair = KeyPairUtils.readKeyPair(reader);
            }
            logger.accept("Loaded existing account key");
        } else {
            accountKeyPair = KeyPairUtils.createKeyPair(2048);
            try (Writer writer = Files.newBufferedWriter(accountKeyPath)) {
                KeyPairUtils.writeKeyPair(accountKeyPair, writer);
            }
            logger.accept("Created new account key");
        }

        if (Files.exists(domainKeyPath)) {
            try (Reader reader = Files.newBufferedReader(domainKeyPath)) {
                domainKeyPair = KeyPairUtils.readKeyPair(reader);
            }
            logger.accept("Loaded existing domain key");
        } else {
            domainKeyPair = KeyPairUtils.createKeyPair(2048);
            try (Writer writer = Files.newBufferedWriter(domainKeyPath)) {
                KeyPairUtils.writeKeyPair(domainKeyPair, writer);
            }
            logger.accept("Created new domain key");
        }
    }

    private boolean loadExistingCertificate() {
        Path certPath = storagePath.resolve("certificate.crt");
        if (!Files.exists(certPath)) {
            return false;
        }

        try {
            // Validate certificate is not expired
            java.security.cert.CertificateFactory cf = 
                    java.security.cert.CertificateFactory.getInstance("X.509");
            try (InputStream is = Files.newInputStream(certPath)) {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
                certificateExpiry = cert.getNotAfter().toInstant();
                
                if (Instant.now().isAfter(certificateExpiry)) {
                    logger.accept("Existing certificate has expired");
                    return false;
                }
                
                Instant renewalTime = certificateExpiry.minus(RENEWAL_THRESHOLD);
                if (Instant.now().isAfter(renewalTime)) {
                    logger.accept("Existing certificate needs renewal soon");
                    return false;
                }
            }

            logger.accept("Loaded existing certificate, expires: " + certificateExpiry);
            return true;
        } catch (Exception e) {
            logger.accept("Failed to load existing certificate: " + e.getMessage());
            return false;
        }
    }

    private void obtainNewCertificate() throws Exception {
        logger.accept("Obtaining new certificate for domain: " + domain);

        // Create or login to account
        // Set context classloader to ensure ServiceLoader finds ACME providers in shaded JAR
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            Session session = new Session(acmeServerUri);
            Account account = new AccountBuilder()
                    .agreeToTermsOfService()
                    .useKeyPair(accountKeyPair)
                    .create(session);

            logger.accept("Account registered/logged in");

            // Order the certificate
            Order order = account.newOrder()
                    .domain(domain)
                    .create();

            logger.accept("Order created for domain: " + domain);

            // Process authorizations
            for (Authorization auth : order.getAuthorizations()) {
                if (auth.getStatus() == Status.VALID) {
                    continue;
                }
                processAuthorization(auth);
            }

            // Generate CSR and finalize order
            CSRBuilder csrBuilder = new CSRBuilder();
            csrBuilder.addDomain(domain);
            csrBuilder.sign(domainKeyPair);

            order.execute(csrBuilder.getEncoded());

        // Wait for order to be ready
        int attempts = 0;
        while (order.getStatus() != Status.VALID && attempts < MAX_POLL_ATTEMPTS) {
            if (order.getStatus() == Status.INVALID) {
                throw new AcmeException("Order failed: " + order.getError()
                        .map(Problem::toString).orElse("unknown error"));
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
            order.fetch();
            attempts++;
        }

        if (order.getStatus() != Status.VALID) {
            throw new AcmeException("Order did not complete in time");
        }

        // Download certificate
        org.shredzone.acme4j.Certificate certificate = order.getCertificate();
        if (certificate == null) {
            throw new AcmeException("No certificate received");
        }

        // Save certificate chain
        saveCertificate(certificate);

        // Update expiry
        List<X509Certificate> chain = certificate.getCertificateChain();
        if (!chain.isEmpty()) {
            certificateExpiry = chain.getFirst().getNotAfter().toInstant();
        }

        logger.accept("Certificate obtained successfully, expires: " + certificateExpiry);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private void processAuthorization(Authorization auth) throws Exception {
        Http01Challenge challenge = auth.findChallenge(Http01Challenge.class)
                .orElseThrow(() -> new AcmeException(
                        "HTTP-01 challenge not available for " + auth.getIdentifier().getDomain()));

        String token = challenge.getToken();
        String authorization = challenge.getAuthorization();

        // Store challenge for serving
        pendingChallenges.put(token, authorization);

        // Start challenge server
        startChallengeServer();
        logger.accept("HTTP-01 challenge server started on port " + CHALLENGE_PORT);

        try {
            // Trigger validation
            challenge.trigger();

            // Wait for validation
            int attempts = 0;
            while (auth.getStatus() != Status.VALID && attempts < MAX_POLL_ATTEMPTS) {
                if (auth.getStatus() == Status.INVALID) {
                    throw new AcmeException("Challenge failed: " + challenge.getError()
                            .map(Problem::toString).orElse("unknown error"));
                }
                Thread.sleep(POLL_INTERVAL.toMillis());
                auth.fetch();
                attempts++;
            }

            if (auth.getStatus() != Status.VALID) {
                throw new AcmeException("Authorization did not complete in time");
            }

            logger.accept("Authorization successful for: " + auth.getIdentifier().getDomain());
        } finally {
            pendingChallenges.remove(token);
            stopChallengeServer();
        }
    }

    private void startChallengeServer() throws IOException {
        if (challengeServer != null) {
            return; // Already running
        }

        challengeServer = HttpServer.create(new InetSocketAddress(CHALLENGE_PORT), 0);
        challengeServer.createContext("/.well-known/acme-challenge/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String token = path.substring(path.lastIndexOf('/') + 1);
            String authorization = pendingChallenges.get(token);

            if (authorization != null) {
                byte[] response = authorization.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
                logger.accept("Served ACME challenge for token: " + token);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });
        challengeServer.setExecutor(Executors.newSingleThreadExecutor());
        challengeServer.start();
    }

    private void stopChallengeServer() {
        if (challengeServer != null) {
            challengeServer.stop(0);
            challengeServer = null;
            logger.accept("HTTP-01 challenge server stopped");
        }
    }

    private void saveCertificate(org.shredzone.acme4j.Certificate certificate) throws IOException {
        Path certPath = storagePath.resolve("certificate.crt");
        try (Writer writer = Files.newBufferedWriter(certPath)) {
            certificate.writeCertificate(writer);
        }
        logger.accept("Certificate saved to: " + certPath);
    }

    private SSLContext buildSSLContext() throws Exception {
        Path certPath = storagePath.resolve("certificate.crt");
        Path keyPath = storagePath.resolve("domain.key");

        // Use PemCertificateProvider to load the saved certificate
        PemCertificateProvider pemProvider = new PemCertificateProvider(certPath, keyPath);
        return pemProvider.createSSLContext();
    }
}


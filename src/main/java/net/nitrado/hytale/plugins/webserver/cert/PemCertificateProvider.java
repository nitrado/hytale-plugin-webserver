package net.nitrado.hytale.plugins.webserver.cert;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * A CertificateProvider implementation that loads certificates and private keys
 * from PEM-formatted files.
 */
public final class PemCertificateProvider implements CertificateProvider {

    private final Path certificatePath;
    private final Path privateKeyPath;
    private final char[] keyStorePassword;

    /**
     * Creates a PEM-based certificate provider.
     *
     * @param certificatePath path to the PEM certificate file (may contain certificate chain)
     * @param privateKeyPath  path to the PEM private key file
     */
    public PemCertificateProvider(Path certificatePath, Path privateKeyPath) {
        this.certificatePath = certificatePath;
        this.privateKeyPath = privateKeyPath;
        this.keyStorePassword = generateRandomPassword();
    }

    private static char[] generateRandomPassword() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes).toCharArray();
    }

    @Override
    public SSLContext createSSLContext() throws Exception {
        KeyStore keyStore = loadKeyStore();

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        return sslContext;
    }

    private KeyStore loadKeyStore() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // Load certificates
        List<X509Certificate> certificates = loadCertificates();
        if (certificates.isEmpty()) {
            throw new IllegalStateException("No certificates found in " + certificatePath);
        }

        // Load private key
        PrivateKey privateKey = loadPrivateKey();

        // Create in-memory KeyStore
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null);

        Certificate[] certChain = certificates.toArray(new Certificate[0]);
        keyStore.setKeyEntry("pem-cert", privateKey, keyStorePassword, certChain);

        return keyStore;
    }

    private List<X509Certificate> loadCertificates() throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        try (InputStream is = Files.newInputStream(certificatePath)) {
            while (is.available() > 0) {
                Certificate cert = certFactory.generateCertificate(is);
                if (cert instanceof X509Certificate) {
                    certificates.add((X509Certificate) cert);
                }
            }
        }

        return certificates;
    }

    private PrivateKey loadPrivateKey() throws Exception {
        try (PEMParser pemParser = new PEMParser(Files.newBufferedReader(privateKeyPath))) {
            Object object = pemParser.readObject();

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (object instanceof PEMKeyPair) {
                // Unencrypted key pair (e.g., RSA PRIVATE KEY)
                return converter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
            } else if (object instanceof PrivateKeyInfo) {
                // PKCS#8 format (e.g., PRIVATE KEY)
                return converter.getPrivateKey((PrivateKeyInfo) object);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported key format in " + privateKeyPath + ": " +
                                (object != null ? object.getClass().getName() : "null"));
            }
        }
    }
}


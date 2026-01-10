package net.nitrado.hytale.plugins.webserver.cert;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.*;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;
import java.io.*;

public final class SelfSignedCertificateProvider implements CertificateProvider {

    private final String commonName;
    private final char[] keyStorePassword;

    public SelfSignedCertificateProvider(String commonName) {
        this.commonName = commonName;
        // Generate a random password for runtime use (never persisted)
        this.keyStorePassword = generateRandomPassword();
    }

    private static char[] generateRandomPassword() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes).toCharArray();
    }

    @Override
    public SSLContext createSSLContext() throws Exception {
        // Generate self-signed cert in-memory
        KeyStore keyStore = generateSelfSignedKeyStore();

        // Create KeyManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyStorePassword);

        // Create SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        return sslContext;
    }

    private KeyStore generateSelfSignedKeyStore() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // 1. Key Pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair pair = keyGen.generateKeyPair();

        // 2. Certificate Details
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now);
        Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000); // 1 year
        BigInteger serial = new BigInteger(64, new SecureRandom());
        X500Name dnName = new X500Name("CN=" + this.commonName);

        // 3. Build certificate
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(pair.getPrivate());
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, serial, notBefore, notAfter, dnName, pair.getPublic());
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(certBuilder.build(signer));
        cert.verify(pair.getPublic());

        // 4. Store in a keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null);
        ks.setKeyEntry("selfsigned", pair.getPrivate(), keyStorePassword, new Certificate[]{cert});

        return ks;
    }
}

package net.nitrado.hytale.plugins.webserver.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import net.nitrado.hytale.plugins.webserver.cert.*;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Configuration for TLS/SSL settings.
 *
 * <p>Supported certificate provider types:</p>
 * <ul>
 *   <li>{@code selfsigned} - Generates a self-signed certificate (default)</li>
 *   <li>{@code pem} - Uses PEM certificate and key files</li>
 *   <li>{@code letsencrypt} - Obtains certificates from Let's Encrypt via ACME</li>
 * </ul>
 */
public final class TlsConfig {

    /**
     * Configuration for self-signed certificate provider.
     */
    public static class SelfSignedConfig {
        public static final BuilderCodec<SelfSignedConfig> CODEC = BuilderCodec.builder(SelfSignedConfig.class, SelfSignedConfig::new)
                .append(
                        new KeyedCodec<>("CommonName", Codec.STRING),
                        (config, value) -> config.commonName = value,
                        config -> config.commonName
                ).add()
                .build();

        private String commonName = null;

        public String getCommonName() {
            return commonName;
        }
    }

    /**
     * Configuration for PEM certificate provider.
     */
    public static class PemConfig {
        public static final BuilderCodec<PemConfig> CODEC = BuilderCodec.builder(PemConfig.class, PemConfig::new)
                .append(
                        new KeyedCodec<>("CertificatePath", Codec.STRING),
                        (config, value) -> config.certificatePath = value,
                        config -> config.certificatePath
                ).add()
                .append(
                        new KeyedCodec<>("PrivateKeyPath", Codec.STRING),
                        (config, value) -> config.privateKeyPath = value,
                        config -> config.privateKeyPath
                ).add()
                .build();

        private String certificatePath = null;
        private String privateKeyPath = null;

        public String getCertificatePath() {
            return certificatePath;
        }

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }
    }

    /**
     * Configuration for Let's Encrypt certificate provider.
     */
    public static class LetsEncryptConfig {
        public static final BuilderCodec<LetsEncryptConfig> CODEC = BuilderCodec.builder(LetsEncryptConfig.class, LetsEncryptConfig::new)
                .append(
                        new KeyedCodec<>("Domain", Codec.STRING),
                        (config, value) -> config.domain = value,
                        config -> config.domain
                ).add()
                .append(
                        new KeyedCodec<>("Production", Codec.BOOLEAN),
                        (config, value) -> config.production = value,
                        config -> config.production
                ).add()
                .append(
                        new KeyedCodec<>("AgreeToTermsOfService", Codec.BOOLEAN),
                        (config, value) -> config.agreeToTermsOfService = value,
                        config -> config.agreeToTermsOfService
                ).add()
                .append(
                        new KeyedCodec<>("ChallengePort", Codec.INTEGER),
                        (config, value) -> config.challengePort = value,
                        config -> config.challengePort
                ).add()
                .build();

        private String domain = null;
        private boolean production = false;
        private boolean agreeToTermsOfService = false;
        private int challengePort = 80;

        public String getDomain() {
            return domain;
        }

        public boolean isProduction() {
            return production;
        }

        public boolean isAgreeToTermsOfService() {
            return agreeToTermsOfService;
        }

        public int getChallengePort() {
            return challengePort;
        }
    }

    public static final BuilderCodec<TlsConfig> CODEC = BuilderCodec.builder(TlsConfig.class, TlsConfig::new)
            .append(
                    new KeyedCodec<>("Insecure", Codec.BOOLEAN),
                    (config, value) -> config.insecure = value,
                    config -> config.insecure
            ).add()
            .append(
                    new KeyedCodec<>("CertificateProvider", Codec.STRING),
                    (config, value) -> config.certificateProvider = value,
                    config -> config.certificateProvider
            ).add()
            .append(
                    new KeyedCodec<>("SelfSigned", SelfSignedConfig.CODEC),
                    (config, value) -> config.selfSigned = value,
                    config -> config.selfSigned
            ).add()
            .append(
                    new KeyedCodec<>("Pem", PemConfig.CODEC),
                    (config, value) -> config.pem = value,
                    config -> config.pem
            ).add()
            .append(
                    new KeyedCodec<>("LetsEncrypt", LetsEncryptConfig.CODEC),
                    (config, value) -> config.letsEncrypt = value,
                    config -> config.letsEncrypt
            ).add()
            .build();

    // General TLS settings
    private boolean insecure = false;
    private String certificateProvider = "selfsigned";

    // Provider-specific configurations
    private SelfSignedConfig selfSigned = new SelfSignedConfig();
    private PemConfig pem = new PemConfig();
    private LetsEncryptConfig letsEncrypt = new LetsEncryptConfig();

    public boolean isInsecure() {
        return insecure;
    }

    public String getCertificateProvider() {
        return certificateProvider;
    }

    public SelfSignedConfig getSelfSigned() {
        return selfSigned;
    }

    public PemConfig getPem() {
        return pem;
    }

    public LetsEncryptConfig getLetsEncrypt() {
        return letsEncrypt;
    }

    /**
     * Creates a CertificateProvider based on this configuration.
     *
     * @param defaultCommonName the common name to use if not explicitly configured (typically BindHost)
     * @param storagePath       base path for storing certificates (used by LetsEncrypt)
     * @param logger            consumer for log messages
     * @return the configured CertificateProvider
     * @throws IllegalArgumentException if configuration is invalid
     */
    public CertificateProvider createCertificateProvider(String defaultCommonName, Path storagePath,
                                                          Consumer<String> logger) {
        return switch (certificateProvider.toLowerCase()) {
            case "selfsigned" -> {
                String cn = selfSigned.getCommonName() != null ? selfSigned.getCommonName() : defaultCommonName;
                yield new SelfSignedCertificateProvider(cn);
            }
            case "pem" -> {
                if (pem.getCertificatePath() == null || pem.getPrivateKeyPath() == null) {
                    throw new IllegalArgumentException(
                            "PEM certificate provider requires Pem.CertificatePath and Pem.PrivateKeyPath");
                }
                yield new PemCertificateProvider(
                        Path.of(pem.getCertificatePath()),
                        Path.of(pem.getPrivateKeyPath()));
            }
            case "letsencrypt" -> {
                if (letsEncrypt.getDomain() == null) {
                    throw new IllegalArgumentException(
                            "LetsEncrypt certificate provider requires LetsEncrypt.Domain");
                }
                if (!letsEncrypt.isAgreeToTermsOfService()) {
                    throw new IllegalArgumentException(
                            "LetsEncrypt certificate provider requires LetsEncrypt.AgreeToTermsOfService to be true. " +
                            "Please review the Let's Encrypt Subscriber Agreement at https://letsencrypt.org/repository/");
                }
                Path certStorage = storagePath.resolve("letsencrypt");
                yield new LetsEncryptCertificateProvider(
                        letsEncrypt.getDomain(),
                        certStorage,
                        letsEncrypt.isProduction(),
                        letsEncrypt.getChallengePort(),
                        logger);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown certificate provider: " + certificateProvider +
                    ". Supported: selfsigned, pem, letsencrypt");
        };
    }
}


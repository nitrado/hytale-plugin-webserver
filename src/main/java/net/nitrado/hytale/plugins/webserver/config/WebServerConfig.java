package net.nitrado.hytale.plugins.webserver.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.Options;

public final class WebServerConfig {
    public static final BuilderCodec<WebServerConfig> CODEC = BuilderCodec.builder(WebServerConfig.class, WebServerConfig::new)
            .append(
                    new KeyedCodec<>("BindHost", Codec.STRING),
                    (config, value) -> config.bindHost = value,
                    config -> config.bindHost
            ).add()
            .append(
                    new KeyedCodec<>("BindPort", Codec.INTEGER),
                    (config, value) -> config.bindPort = value,
                    config -> config.bindPort
            ).add()
            .append(
                    new KeyedCodec<>("Tls", TlsConfig.CODEC),
                    (config, value) -> config.tls = value,
                    config -> config.tls
            ).add()
            .build();

    private String bindHost = Options.getOptionSet().valueOf(Options.BIND).getHostName();
    private int bindPort = Options.getOptionSet().valueOf(Options.BIND).getPort() + 3;
    private TlsConfig tls = new TlsConfig();

    public String getBindHost() {
        return bindHost;
    }

    public int getBindPort() {
        return bindPort;
    }

    public TlsConfig getTls() {
        return tls;
    }
}

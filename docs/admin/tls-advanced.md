# Advanced TLS Configuration

This guide covers advanced TLS configuration options for the Hytale WebServer Plugin.

For basic TLS setup (self-signed certificates or disabling TLS), see the [Server Administrator Guide](admin-guide.md#tls-configuration).

## Certificate Providers

| Provider      | Description                                      |
|---------------|--------------------------------------------------|
| `selfsigned`  | Generates a self-signed certificate (default)    |
| `pem`         | Uses PEM certificate and key files               |
| `letsencrypt` | Obtains certificates from Let's Encrypt via ACME |

## PEM Configuration

Use your own certificate and private key files:

```json
{
  "Tls": {
    "CertificateProvider": "pem",
    "Pem": {
      "CertificatePath": "/path/to/certificate.pem",
      "PrivateKeyPath": "/path/to/private-key.pem"
    }
  }
}
```

## Let's Encrypt Configuration

Using Let's Encrypt requires agreement to their [Subscriber Agreement](https://letsencrypt.org/repository/). You must set `AgreeToTermsOfService` to `true` to acknowledge this.

```json
{
  "Tls": {
    "CertificateProvider": "letsencrypt",
    "LetsEncrypt": {
      "Domain": "my-server.example.com",
      "Production": false,
      "AgreeToTermsOfService": true
    }
  }
}
```

Set `Production` to `true` to use Let's Encrypt's production servers (has rate limits). When `false`, uses the staging environment for testing.

You can also configure a custom port for the ACME HTTP-01 challenge server using `ChallengePort` (defaults to 80):
```json
{
  "Tls": {
    "CertificateProvider": "letsencrypt",
    "LetsEncrypt": {
      "Domain": "my-server.example.com",
      "Production": false,
      "AgreeToTermsOfService": true,
      "ChallengePort": 8080
    }
  }
}
```

### Port 80 Permissions (Linux)

Let's Encrypt uses the HTTP-01 challenge, which requires that port 80 is reachable from the internet. On Linux, binding to ports below 1024 requires elevated privileges. Here are the recommended approaches:

1. **Using `setcap` (recommended for non-containerized setups):**
   Grant the Java binary the capability to bind to privileged ports:
   ```bash
   sudo setcap 'cap_net_bind_service=+ep' $(readlink -f $(which java))
   ```
   Note: This must be re-applied after Java updates and grants this capability to _all_ Java applications If this is a
   concern, consider using a separate Java installation for Hytale, and only assign this capability to that installation.

2. **Using Docker:**
   When running in Docker, map port 80 from the host to the challenge port inside the container:
   ```bash
   docker run -p 80:8080 -p 5520:5520 -p 5523:5523 your-server-image
   ```
   Then configure `ChallengePort` to match the container port (8080 in this example).

   Alternatively, when using host networking, grant the container the capability to bind to privileged ports:
   ```bash
   docker run --network host --cap-add NET_BIND_SERVICE your-server-image
   ```

3. **Using Docker Compose:**
   ```yaml
   services:
     hytale:
       image: your-server-image
       ports:
         - "80:8080"    # ACME challenge: host port 80 -> container port 8080
         - "5520:5520"  # Game Server
         - "5523:5523"  # WebServer
   ```
   Then configure `ChallengePort` to match the container port (8080 in this example).

   Alternatively, when using host networking:
   ```yaml
   services:
     hytale:
       image: your-server-image
       network_mode: host
       cap_add:
         - NET_BIND_SERVICE
   ```

4. **Port forwarding with `iptables`:**
   Forward port 80 to a higher unprivileged port (e.g., 8080):
   ```bash
   sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8080
   ```
   Then set `ChallengePort` to 8080 in the configuration.

5. **Running as root (not recommended):**
   Running the server as root allows binding to port 80 but exposes your system to significant security risks if the server process is compromised.


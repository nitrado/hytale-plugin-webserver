package net.nitrado.hytale.plugins.webserver.authentication.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.authentication.AuthProvider;
import net.nitrado.hytale.plugins.webserver.authentication.HytaleUserPrincipal;
import net.nitrado.hytale.plugins.webserver.authentication.store.CredentialValidator;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public final class BasicAuthProvider implements AuthProvider {
    protected final CredentialValidator credentialValidator;

    public BasicAuthProvider(CredentialValidator credentialValidator) {
        this.credentialValidator = credentialValidator;
    }

    @Override
    public AuthProvider.AuthResult authenticate(HttpServletRequest req, HttpServletResponse res) {
        String authHeader = req.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return new AuthResult(AuthResultType.NONE, null);
        }

        String base64Credentials = authHeader.substring("Basic ".length());
        String credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return new AuthResult(AuthResultType.FAILURE, null);
        }

        int colonIndex = credentials.indexOf(':');
        if (colonIndex < 0) {
            return new AuthResult(AuthResultType.FAILURE, null);
        }

        String username = credentials.substring(0, colonIndex);
        String password = credentials.substring(colonIndex + 1);

        UUID uuid = null;

        try {
            uuid = UUID.fromString(username);
        } catch (IllegalArgumentException e) {
            // ignore
        }

        // if username is a UUID, treat is as such
        if (uuid == null) {
            uuid = credentialValidator.validateCredential(username, password);
        } else {
            uuid = credentialValidator.validateCredential(uuid, password);
        }

        if (uuid == null) {
            return new AuthResult(AuthResultType.FAILURE, null);
        }

        return new AuthResult(AuthResultType.SUCCESS, new HytaleUserPrincipal(uuid));
    }

    @Override
    public boolean challenge(HttpServletRequest req, HttpServletResponse res) {
        res.setHeader("WWW-Authenticate", "Basic");

        return true;
    }
}


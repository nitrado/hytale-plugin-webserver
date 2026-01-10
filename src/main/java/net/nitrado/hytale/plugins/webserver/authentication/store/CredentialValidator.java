package net.nitrado.hytale.plugins.webserver.authentication.store;

import java.util.UUID;

public interface CredentialValidator {
    boolean hasUser(String username);
    boolean hasUser(UUID uuid);
    UUID validateCredential(String username, String credential);
    UUID validateCredential(UUID uuid, String credential);
}
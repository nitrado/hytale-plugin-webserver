package net.nitrado.hytale.plugins.webserver.authentication.store;

import java.util.UUID;

public interface CredentialValidator {
    record ValidationResult(UUID uuid, String username){}

    boolean hasUser(String username);
    boolean hasUser(UUID uuid);
    ValidationResult validateCredential(String username, String credential);
    ValidationResult validateCredential(UUID uuid, String credential);
}
package net.nitrado.hytale.plugins.webserver.auth.store;

public class InvalidCredentialException extends RuntimeException {
    public InvalidCredentialException(String message) {
        super(message);
    }
}

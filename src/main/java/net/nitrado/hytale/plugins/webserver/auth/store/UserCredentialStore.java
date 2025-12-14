package net.nitrado.hytale.plugins.webserver.auth.store;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public interface UserCredentialStore {
    void setUserCredential(UUID uuid, String username, String password) throws IOException;
    void importUserCredential(UUID uuid, String username, String passwordHash) throws IOException;

    default void setUserCredential(UUID uuid, String password) throws IOException {
        setUserCredential(uuid, null, password);
    }
    void deleteUserCredential(String username) throws IOException;
    void deleteUserCredential(UUID uuid) throws IOException;
    UUID getUUIDByName(String name);
    String getNameByUUID(UUID uuid);

    Set<UUID> listUsers();
}

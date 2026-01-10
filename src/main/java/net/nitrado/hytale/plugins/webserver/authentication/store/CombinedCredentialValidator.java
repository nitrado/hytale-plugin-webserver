package net.nitrado.hytale.plugins.webserver.authentication.store;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CombinedCredentialValidator implements CredentialValidator {

    private final List<CredentialValidator> stores = new ArrayList<>();

    public void add(CredentialValidator store) {
        stores.add(store);
    }

    @Override
    public boolean hasUser(String username) {
        for  (CredentialValidator store : stores) {
            if (store.hasUser(username)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean hasUser(UUID uuid) {
        for  (CredentialValidator store : stores) {
            if (store.hasUser(uuid)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public UUID validateCredential(String username, String credential) {
        for  (CredentialValidator store : stores) {
            if (store.hasUser(username)) {
                return store.validateCredential(username, credential);
            }
        }

        return null;
    }

    @Override
    public UUID validateCredential(UUID uuid, String credential) {
        for  (CredentialValidator store : stores) {
            if (store.hasUser(uuid)) {
                return store.validateCredential(uuid, credential);
            }
        }

        return null;
    }
}

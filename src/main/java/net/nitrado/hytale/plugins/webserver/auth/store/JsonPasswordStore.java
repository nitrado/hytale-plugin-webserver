package net.nitrado.hytale.plugins.webserver.auth.store;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.hypixel.hytale.logger.HytaleLogger;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * JsonPasswordStore implements a JSON file based password store, with passwords being saved as
 * BCrypt hashes.
 */
public class JsonPasswordStore implements CredentialValidator, UserCredentialStore {

    protected final Path path;
    protected final HytaleLogger logger;

    protected final Map<String, UUID> nameToUUID = new ConcurrentHashMap<>();
    protected final Map<UUID, String> uuidToCredential = new ConcurrentHashMap<>();

    public JsonPasswordStore(Path path, HytaleLogger logger) {
        this.path = path;
        this.logger = logger;
    }

    public void load() throws IOException {
        var changes = false;
        var parent = this.path.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectory(parent);
        }

        if (!Files.exists(this.path)) {
            return;
        }

        String jsonString = Files.readString(this.path);
        Document document = Document.parse(jsonString);

        this.nameToUUID.clear();
        this.uuidToCredential.clear();

        Document usernames = document.get("users", Document.class);
        if (usernames != null) {
            for (Map.Entry<String, Object> entry : usernames.entrySet()) {
                String username = entry.getKey();
                UUID uuid = UUID.fromString(entry.getValue().toString());
                this.nameToUUID.put(username, uuid);
            }
        }

        Document credentials = document.get("credentials", Document.class);
        if (credentials != null) {
            for (Map.Entry<String, Object> entry : credentials.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                String hashedPassword = entry.getValue().toString();

                if (!isBcryptHash(hashedPassword)) {
                    BCrypt.withDefaults().hashToString(10 , hashedPassword.toCharArray());
                    changes = true;
                }

                this.uuidToCredential.put(uuid, hashedPassword);
            }
        }

        if (changes) {
            // We have replaced a plain text password with a bcrypt hash, so we flush those changes
            this.save();
        }
    }

    @Override
    public boolean hasUser(String username) {
        var uuid = nameToUUID.get(username);
        if (uuid == null) {
            return false;
        }

        return this.hasUser(uuid);
    }

    @Override
    public boolean hasUser(UUID uuid) {
        return this.uuidToCredential.containsKey(uuid);
    }

    @Override
    public UUID validateCredential(String username, String credential) {
        var uuid = this.nameToUUID.get(username);
        if (uuid == null) {
            return null;
        }

        return this.validateCredential(uuid, credential);
    }

    @Override
    public UUID validateCredential(UUID uuid, String credential) {
        var savedCredential = this.uuidToCredential.get(uuid);
        if (savedCredential == null) {
            return null;
        }

        if (BCrypt.verifyer().verify(credential.toCharArray(), savedCredential).verified) {
            return uuid;
        }

        return null;
    }

    @Override
    public void setUserCredential(UUID uuid, String username, String password) throws IOException {
        this.importUserCredential(uuid, username, BCrypt.withDefaults().hashToString(10 , password.toCharArray()));
    }

    @Override
    public void importUserCredential(UUID uuid, String username, String passwordHash) throws IOException, InvalidCredentialException {
        if (!this.isBcryptHash(passwordHash)) {
            throw new InvalidCredentialException("Given password is not a bcrypt hash");
        }

        var lastPassword = this.uuidToCredential.get(uuid);
        UUID lastUuid = null;
        if (username != null) {
            lastUuid = this.nameToUUID.get(username);
            this.nameToUUID.put(username, uuid);
        }

        this.uuidToCredential.put(uuid, passwordHash);

        try {
            this.save();
        } catch (IOException e) {
            this.uuidToCredential.put(uuid, lastPassword);
            this.nameToUUID.put(username, lastUuid);

            throw e;
        }
    }

    @Override
    public void deleteUserCredential(String username) throws IOException {
        var uuid = this.nameToUUID.get(username);

        this.deleteUserCredential(uuid);
    }

    @Override
    public void deleteUserCredential(UUID uuid) throws IOException {
        var lastCredential = this.uuidToCredential.get(uuid);
        String lastName = this.getNameByUUID(uuid);

        if (lastName != null) {
            this.nameToUUID.remove(lastName);
        }
        this.uuidToCredential.remove(uuid);

        try {
            this.save();
        } catch (IOException e) {
            this.nameToUUID.put(lastName, uuid);
            this.uuidToCredential.put(uuid, lastCredential);

            throw e;
        }
    }

    @Override
    public UUID getUUIDByName(String name) {
        return this.nameToUUID.get(name);
    }

    @Override
    public String getNameByUUID(UUID uuid) {
        for  (Map.Entry<String, UUID> entry : this.nameToUUID.entrySet()) {
            if (entry.getValue().equals(uuid)) {
                return entry.getKey();
            }
        }

        return null;
    }

    @Override
    public Set<UUID> listUsers() {
        return this.uuidToCredential.keySet();
    }

    protected void save() throws IOException {
        var document = new Document();

        var usernames = new Document();
        for (Map.Entry<String, UUID> entry : nameToUUID.entrySet()) {
            usernames.append(entry.getKey(), entry.getValue().toString());
        }

        var credentials = new Document();
        for (Map.Entry<UUID, String> entry : uuidToCredential.entrySet()) {
            credentials.append(entry.getKey().toString(), entry.getValue());
        }

        document.append("users", usernames);
        document.append("credentials", credentials);

        var jsonString = document.toJson(JsonWriterSettings.builder().indent(true).build());

        try {
            Files.writeString(this.path, jsonString);
        } catch (IOException e) {
            this.logger.atSevere().log("failed to save to %s: :%s", this.path.toString(), e.getMessage());
            throw e;
        }
    }

    private boolean isBcryptHash(String password) {
        // BCrypt format: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
        return password != null && password.matches("^\\$2[aby]?\\$\\d{1,2}\\$[./A-Za-z0-9]{53}$");
    }
}

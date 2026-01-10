package net.nitrado.hytale.plugins.webserver.authentication.store;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LoginCodeStore {
    private static final Duration VALIDITY = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Entry (long validUntil, UUID uuid, String displayName) {}

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    public synchronized String createCode(UUID uuid, String displayName) {
        Entry entry = new Entry(System.currentTimeMillis() + VALIDITY.toMillis(), uuid, displayName);

        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(e ->
            e.getValue().validUntil < now || e.getValue().uuid.equals(uuid)
        );

        var code = generateCode();
        entries.put(code, entry);

        return code;
    }

    public Entry getEntry(String code) {
        var entry = entries.remove(code);
        if (entry == null) {
            return null;
        }

        if (entry.validUntil < System.currentTimeMillis()) {
            return null;
        }

        return entry;
    }

    private String generateCode() {
        var characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        StringBuilder result = new StringBuilder();
        for  (int i = 0; i < 4; i++) {
            var randInt = RANDOM.nextInt(characters.length());

            result.append(characters.charAt(randInt));
        }

        result.append('-');

        for  (int i = 0; i < 4; i++) {
            var randInt = RANDOM.nextInt(characters.length());

            result.append(characters.charAt(randInt));
        }

        return result.toString();
    }


}

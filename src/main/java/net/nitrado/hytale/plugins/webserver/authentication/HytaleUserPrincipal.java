package net.nitrado.hytale.plugins.webserver.authentication;

import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import javax.annotation.Nonnull;
import java.security.Principal;
import java.util.UUID;

/**
 * Represents an authenticated user in the Hytale web server context.
 * <p>
 * This class implements both {@link Principal} and {@link PermissionHolder}, allowing
 * servlets to identify the authenticated user and check their permissions.
 * </p>
 * <p>
 * Example usage in a servlet:
 * <pre>{@code
 * var principal = req.getUserPrincipal();
 * if (principal instanceof HytaleUserPrincipal user) {
 *     if (user.hasPermission("my.custom.permission")) {
 *         // User has permission
 *     }
 * }
 * }</pre>
 * </p>
 */
public class HytaleUserPrincipal implements Principal, PermissionHolder {

    private final UUID uuid;

    public HytaleUserPrincipal(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * Returns the UUID of the authenticated user.
     * @return the user's UUID
     */
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String getName() {
        return this.uuid.toString();
    }

    @Override
    public boolean hasPermission(@Nonnull String s) {
        return PermissionsModule.get().hasPermission(uuid, s);
    }

    @Override
    public boolean hasPermission(@Nonnull String s, boolean b) {
        return PermissionsModule.get().hasPermission(uuid, s, b);
    }
}

package net.nitrado.hytale.plugins.webserver.authentication;

import com.hypixel.hytale.server.core.permissions.PermissionHolder;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.Principal;
import java.util.Set;
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

    static UUID anonymousUserUuid = new UUID(0,0);

    private final UUID uuid;
    private final String name;

    public HytaleUserPrincipal(@Nonnull UUID uuid, @Nullable String name) {
        this.uuid = uuid;
        this.name = name;
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
        return this.name;
    }

    public boolean isAnonymous() {
        return anonymousUserUuid.equals(uuid);
    }

    @Override
    public boolean hasPermission(@Nonnull String s) {
        return hasPermission(s, false);
    }

    @Override
    public boolean hasPermission(@Nonnull String s, boolean b) {
        if (isAnonymous()) {
            return anonymousHasPermission(s, b);
        }

        return PermissionsModule.get().hasPermission(uuid, s, b);
    }

    private boolean anonymousHasPermission(@Nonnull String id, boolean def) {
        var module = PermissionsModule.get();

        for (var provider : module.getProviders()) {
            var groupNodes = provider.getGroupPermissions("ANONYMOUS");

            final Boolean groupHasPerm = PermissionsModule.hasPermission(groupNodes, id);
            if (groupHasPerm != null) {
                return groupHasPerm;
            }
        }

        return def;
    }

    public static HytaleUserPrincipal getAnonymous() {
        return new HytaleUserPrincipal(anonymousUserUuid, "Anonymous");
    }
}

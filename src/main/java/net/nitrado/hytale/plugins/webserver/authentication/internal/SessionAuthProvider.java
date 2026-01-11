package net.nitrado.hytale.plugins.webserver.authentication.internal;

import com.hypixel.hytale.logger.HytaleLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.authentication.AuthProvider;
import net.nitrado.hytale.plugins.webserver.authentication.HytaleUserPrincipal;

import java.io.IOException;
import java.util.UUID;

/**
 * Authentication provider that handles session-based authentication via cookies.
 * <p>
 * <strong>Internal:</strong> Consumers receive a pre-configured instance via
 * {@link net.nitrado.hytale.plugins.webserver.WebServerPlugin#getDefaultAuthProviders()}.
 * </p>
 */
public final class SessionAuthProvider implements AuthProvider {

    private final HytaleLogger logger;

    public SessionAuthProvider(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public AuthProvider.AuthResult authenticate(HttpServletRequest req, HttpServletResponse res) {
        var session = req.getSession();

        var uuidObj = session.getAttribute("uuid");
        if (uuidObj == null) {
            return new AuthResult(AuthResultType.NONE, null);
        }

        if (!(uuidObj instanceof UUID uuid)) {
            return new AuthResult(AuthResultType.NONE, null);
        }

        var usernameObj  = session.getAttribute("username");
        String username = null;
        if (usernameObj instanceof String) {
            username = (String) usernameObj;
        }

        return new AuthResult(AuthResultType.SUCCESS, new HytaleUserPrincipal(uuid, username));
    }

    @Override
    public boolean challenge(HttpServletRequest req, HttpServletResponse res) {
        try {
            res.sendRedirect(res.encodeRedirectURL("/login?redirect_url=" + req.getRequestURI()));
        } catch (IOException e) {
            logger.atSevere().withCause(e).log("Failed to redirect to login page");
        }

        return true;
    }
}


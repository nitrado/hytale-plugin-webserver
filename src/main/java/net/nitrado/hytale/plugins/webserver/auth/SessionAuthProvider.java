package net.nitrado.hytale.plugins.webserver.auth;

import com.hypixel.hytale.logger.HytaleLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

public class SessionAuthProvider implements AuthProvider {

    private HytaleLogger logger;

    public SessionAuthProvider(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public AuthResult authenticate(HttpServletRequest req, HttpServletResponse res) {
        var session = req.getSession();

        var uuidObj = session.getAttribute("uuid");
        if (uuidObj == null) {
            return new AuthResult(AuthResultType.NONE, null);
        }

        if (!(uuidObj instanceof UUID uuid)) {
            return new AuthResult(AuthResultType.NONE, null);
        }

        return new AuthResult(AuthResultType.SUCCESS, new HytaleUserPrincipal(uuid));
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

package net.nitrado.hytale.plugins.webserver.login;

import com.hypixel.hytale.logger.HytaleLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.auth.store.CredentialValidator;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public class LogoutServlet extends HttpServlet {

    private HytaleLogger logger;
    private CredentialValidator validator;

    public LogoutServlet(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getSession().invalidate();
        resp.sendRedirect(resp.encodeRedirectURL("/login"));
    }
}

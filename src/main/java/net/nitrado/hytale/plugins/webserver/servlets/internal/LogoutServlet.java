package net.nitrado.hytale.plugins.webserver.servlets.internal;

import com.hypixel.hytale.logger.HytaleLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.authentication.store.CredentialValidator;

import java.io.IOException;

public final class LogoutServlet extends HttpServlet {

    private HytaleLogger logger;
    private CredentialValidator validator;

    public LogoutServlet(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getSession().invalidate();
        resp.sendRedirect(resp.encodeRedirectURL("/"));
    }
}


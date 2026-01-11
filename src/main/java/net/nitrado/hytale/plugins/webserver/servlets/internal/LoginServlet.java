package net.nitrado.hytale.plugins.webserver.servlets.internal;

import com.hypixel.hytale.logger.HytaleLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import net.nitrado.hytale.plugins.webserver.authentication.store.CredentialValidator;
import net.nitrado.hytale.plugins.webserver.authentication.store.LoginCodeStore;
import net.nitrado.hytale.plugins.webserver.authentication.store.UserCredentialStore;
import net.nitrado.hytale.plugins.webserver.servlets.TemplateServlet;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public final class LoginServlet extends TemplateServlet {

    private final HytaleLogger logger;
    private final CredentialValidator credentialValidator;
    private final UserCredentialStore credentialStore;
    private final LoginCodeStore loginCodeStore;

    public LoginServlet(WebServerPlugin plugin, HytaleLogger logger, UserCredentialStore credentialStore, CredentialValidator validator, LoginCodeStore loginCodeStore) {
        super(plugin);

        this.logger = logger;
        this.credentialStore = credentialStore;
        this.loginCodeStore = loginCodeStore;
        this.credentialValidator = validator;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=utf-8");
        var session = req.getSession();

        var m = new HashMap<String, Object>();
        m.put("CSRF_TOKEN", "abcd");
        var uuidString = session.getAttribute("uuid");
        if (uuidString != null) {
            m.put("UUID", uuidString.toString());
        }

        this.renderTemplate(req, resp, "nitrado.webserver.login", m);
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var session = req.getSession();

        var m = new HashMap<String, Object>();

        UUID loggedInUUID = null;
        String loggedInUsername = null;
        var loginMethod = req.getParameter("method");
        String username, password;
        LoginCodeStore.Entry entry;
        switch(loginMethod) {
            case "code":
                entry = getStoredEntryByLoginCode(req.getParameter("loginCode"));
                if (entry == null || entry.uuid() == null) {
                    m.put("ERROR", "Login code invalid or expired.");
                    break;
                }

                loggedInUUID = entry.uuid();
                loggedInUsername = entry.displayName();
                break;
            case "password":
                username = req.getParameter("username");
                password = req.getParameter("password");

                var loggedInUser = getUuidByPlayerPassword(username, password);
                if (loggedInUser == null) {
                    m.put("ERROR", "Invalid username or password.");
                    break;
                }

                loggedInUsername = loggedInUser.username();
                loggedInUUID = loggedInUser.uuid();
                break;
            case "passwordCreate":
                entry = getStoredEntryByLoginCode(req.getParameter("loginCode"));

                if  (entry == null || entry.uuid() == null) {
                    m.put("ERROR", "Login code invalid or expired.");
                    break;
                }

                password = req.getParameter("password");
                if (password.length() < 8) {
                    m.put("ERROR", "Password too short.");
                    break;
                }

                loggedInUUID = entry.uuid();
                loggedInUsername = entry.displayName();

                this.credentialStore.setUserCredential(loggedInUUID, entry.displayName(), password);
                break;
        }

        if (loggedInUUID != null) {
            session.setAttribute("uuid", loggedInUUID);
            session.setAttribute("username", loggedInUsername);

            var redirectTarget = "/";
            var redirectUrlParameter = req.getParameter("redirect_url");
            if (redirectUrlParameter != null && redirectUrlParameter.startsWith("/")) { // prevent open redirect
                redirectTarget = redirectUrlParameter;
            }

            resp.sendRedirect(resp.encodeRedirectURL(redirectTarget));
            return;
        }

        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        m.put("CSRF_TOKEN", "abcd");


        this.renderTemplate(req, resp, "nitrado.webserver.login", m);
    }

    private LoginCodeStore.Entry getStoredEntryByLoginCode(String loginCode) {
        return this.loginCodeStore.getEntry(loginCode);
    }

    private CredentialValidator.ValidationResult getUuidByPlayerPassword(String username, String password) {
        UUID uuidInput = null;
        try {
            uuidInput = UUID.fromString(username);

        } catch (IllegalArgumentException e) {}

        CredentialValidator.ValidationResult result;
        if (uuidInput != null) {
            result = credentialValidator.validateCredential(uuidInput, password);
        } else {
            result = credentialValidator.validateCredential(username, password);
        }

        return result;
    }
}


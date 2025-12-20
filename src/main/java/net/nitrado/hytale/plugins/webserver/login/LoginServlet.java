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

public class LoginServlet extends HttpServlet {

    private HytaleLogger logger;
    private CredentialValidator validator;

    public LoginServlet(HytaleLogger logger, CredentialValidator validator) {
        this.logger = logger;
        this.validator = validator;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=utf-8");
        var session = req.getSession();


        var m = new HashMap<String, String>();
        m.put("CSRF_TOKEN", "abcd");
        var uuidString = session.getAttribute("uuid");
        if (uuidString != null) {
            m.put("UUID", uuidString.toString());
        }

        resp.getWriter().println(TemplateEngine.render("login.html", m));
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        var session = req.getSession();
        this.logger.atInfo().log("Session ID: " + session.getId());

        if (session.isNew()) {
            this.logger.atInfo().log("new Session %s", session.getId());
        }

        // Access form fields using getParameter
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        UUID uuidInput = null;
        try {
            uuidInput = UUID.fromString(username);

        } catch (IllegalArgumentException e) {}

        UUID loggedInUUID;
        if (uuidInput != null) {
            loggedInUUID = validator.validateCredential(uuidInput, password);
        } else {
            loggedInUUID = validator.validateCredential(username, password);
        }

        if (loggedInUUID != null) {
            session.setAttribute("uuid", loggedInUUID);

            var redirectTarget = "/login";
            var redirectUrlParameter = req.getParameter("redirect_url");
            if (redirectUrlParameter != null && redirectUrlParameter.startsWith("/")) { // prevent open redirect
                redirectTarget = redirectUrlParameter;
            }

            resp.sendRedirect(resp.encodeRedirectURL(redirectTarget));
            return;
        }

        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        var m = new HashMap<String, String>();
        m.put("CSRF_TOKEN", "abcd");
        resp.getWriter().println(TemplateEngine.render("login.html", m));
    }
}

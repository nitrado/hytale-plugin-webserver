package net.nitrado.hytale.plugins.webserver.servlets.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import net.nitrado.hytale.plugins.webserver.servlets.TemplateServlet;

import java.io.IOException;

public class IndexServlet extends TemplateServlet {

    public IndexServlet(WebServerPlugin parentPlugin) {
        super(parentPlugin);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        this.renderTemplate(req, resp, "nitrado.webserver.index");
    }
}

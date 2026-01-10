package net.nitrado.hytale.plugins.webserver.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;
import net.nitrado.hytale.plugins.webserver.authentication.store.LoginCodeStore;

public final class WebServerCommand extends AbstractCommandCollection {

    public WebServerCommand(LoginCodeStore loginCodeStore) {
        super("webserver", "Manage webserver-related configuration, such as user credentials and service accounts");
        addAliases("web");

        addSubCommand(new CodeCommand(loginCodeStore));
    }
}

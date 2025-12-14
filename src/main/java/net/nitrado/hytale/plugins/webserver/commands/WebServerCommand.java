package net.nitrado.hytale.plugins.webserver.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.CommandCollectionBase;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;

public class WebServerCommand extends CommandCollectionBase {

    public WebServerCommand(WebServerPlugin webServerPlugin) {
        super("webserver", "Manage webserver-related configuration, such as user credentials and service accounts");
        addAliases("web");

        addSubCommand(new UserPasswordCommand(webServerPlugin.getUserCredentialStore()));
        addSubCommand(new ServiceAccountCommand(webServerPlugin));
    }
}

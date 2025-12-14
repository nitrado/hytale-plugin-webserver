package net.nitrado.hytale.plugins.webserver.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.CommandCollectionBase;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;

public class ServiceAccountCommand extends CommandCollectionBase {
    public ServiceAccountCommand(WebServerPlugin plugin) {
        super("serviceaccount", "Manage service accounts (bot users) for web access");
        addAliases("sa");

        addSubCommand(new ServiceAccountCreateCommand(plugin));
        addSubCommand(new ServiceAccountDeleteCommand(plugin));
        addSubCommand(new ServiceAccountListCommand(plugin));
    }
}

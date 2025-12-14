package net.nitrado.hytale.plugins.webserver.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import net.nitrado.hytale.plugins.webserver.Permissions;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class ServiceAccountListCommand extends AbstractCommand {

    private final WebServerPlugin plugin;

    public ServiceAccountListCommand(WebServerPlugin plugin) {
        super("list", "List service accounts");

        this.plugin = plugin;

    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        CommandUtil.requirePermission(context.sender(), Permissions.SERVICEACCOUNT_LIST);

        var store = this.plugin.getServiceAccountCredentialStore();
        var list = store.listUsers();

        if  (list.isEmpty()) {
            context.sendMessage(Message.raw("No Service Accounts found."));
        }

        for (UUID uuid : list) {
            context.sendMessage(Message.raw(String.format("%s: %s", store.getNameByUUID(uuid), uuid.toString())));
        }

        return CompletableFuture.completedFuture(null);
    }
}

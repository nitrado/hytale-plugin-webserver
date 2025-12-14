package net.nitrado.hytale.plugins.webserver.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import net.nitrado.hytale.plugins.webserver.Permissions;
import net.nitrado.hytale.plugins.webserver.WebServerPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class ServiceAccountDeleteCommand extends AbstractCommand {

    private final WebServerPlugin plugin;
    private final RequiredArg<String> nameArg = withRequiredArg("name", "The name or UUID of the service account", ArgTypes.STRING);

    public ServiceAccountDeleteCommand(WebServerPlugin plugin) {
        super("delete", "Delete a service account");

        this.plugin = plugin;

    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        CommandUtil.requirePermission(context.sender(), Permissions.SERVICEACCOUNT_DELETE);

        var name = nameArg.get(context);

        UUID uuid = null;
        try {
            uuid = UUID.fromString(name);
        } catch (IllegalArgumentException e) {}

        try {
            if (uuid != null) {
                this.plugin.deleteServiceAccount(uuid);
            } else {
                if (!name.startsWith("serviceaccount.")) {
                    name = "serviceaccount." + name;
                }
                this.plugin.deleteServiceAccount(name);
            }
        } catch (IOException e) {
            this.plugin.getLogger().atSevere().log("Failed to delete service account with name " + name, e);
            context.sendMessage(Message.raw("Service Account deletion failed"));
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw("Service Account deleted"));

        return CompletableFuture.completedFuture(null);
    }
}

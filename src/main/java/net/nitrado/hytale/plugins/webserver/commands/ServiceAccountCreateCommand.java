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
import java.util.concurrent.CompletableFuture;


public class ServiceAccountCreateCommand extends AbstractCommand {

    private final WebServerPlugin plugin;
    private final RequiredArg<String> nameArg = withRequiredArg("name", "The name of the service account (will be prefixed with \"serviceaccount.\".)", ArgTypes.STRING);
    private final RequiredArg<String> passwordArg = withRequiredArg("password", "The password for the service account", ArgTypes.STRING);

    public ServiceAccountCreateCommand(WebServerPlugin plugin) {
        super("create", "Create a service account");

        this.plugin = plugin;

    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        final var name = nameArg.get(context);
        final var password = passwordArg.get(context);

        CommandUtil.requirePermission(context.sender(), Permissions.SERVICEACCOUNT_CREATE);

        try {
            var uuid = this.plugin.createServiceAccount(name, password);

            context.sendMessage(Message.raw("Service Account created as UUID " + uuid.toString()));
        } catch (IOException e) {
            context.sendMessage(Message.raw("Service Account creation failed"));
        }

        return CompletableFuture.completedFuture(null);
    }
}

package net.nitrado.hytale.plugins.webserver.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import net.nitrado.hytale.plugins.webserver.Permissions;
import net.nitrado.hytale.plugins.webserver.authentication.store.LoginCodeStore;
import net.nitrado.hytale.plugins.webserver.authentication.store.UserCredentialStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;


public class CodeCreateCommand extends AbstractCommand {

    private final LoginCodeStore loginCodeStore;

    public CodeCreateCommand(LoginCodeStore loginCodeStore) {
        super("create", "Create a short-lived login code.");

        this.loginCodeStore = loginCodeStore;

    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        CommandUtil.requirePermission(context.sender(), Permissions.LOGIN_CODE_CREATE);

        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command can only be executed by players."));
            return CompletableFuture.completedFuture(null);
        }

        var code = this.loginCodeStore.createCode(context.sender().getUuid(), context.sender().getDisplayName());

        context.sendMessage(Message.raw("Your web server login code is: " + code));
        context.sendMessage(Message.raw("Do not share this code with anybody."));

        return CompletableFuture.completedFuture(null);
    }
}

package net.nitrado.hytale.plugins.webserver.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import net.nitrado.hytale.plugins.webserver.authentication.store.LoginCodeStore;
import net.nitrado.hytale.plugins.webserver.authentication.store.UserCredentialStore;

public final class CodeCommand extends AbstractCommandCollection {

    public CodeCommand(LoginCodeStore store) {
        super("code", "Manage login codes for web access");

        addSubCommand(new CodeCreateCommand(store));
    }


}

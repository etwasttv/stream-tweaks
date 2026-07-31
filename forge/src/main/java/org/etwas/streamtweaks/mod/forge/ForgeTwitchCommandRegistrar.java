package org.etwas.streamtweaks.mod.forge;

import java.util.concurrent.Executor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import org.etwas.streamtweaks.application.TwitchApplicationService;
import org.etwas.streamtweaks.presentation.commands.TwitchCommand;

public final class ForgeTwitchCommandRegistrar {
    private ForgeTwitchCommandRegistrar() {}

    public static void register(TwitchApplicationService applicationService, Executor clientExecutor) {
        TwitchCommand<CommandSourceStack> command = new TwitchCommand<>(applicationService, clientExecutor);
        RegisterClientCommandsEvent.BUS.addListener(event -> event.getDispatcher()
                .register(command.build(source -> feedback -> source.sendSuccess(() -> feedback, false))));
    }
}

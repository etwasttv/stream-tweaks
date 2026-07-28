package org.etwas.streamtweaks.presentation.commands;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.network.chat.Component;
import org.etwas.streamtweaks.application.TwitchApplicationService;
import org.etwas.streamtweaks.twitch.core.Login;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TwitchCommandTest {

    @Mock
    TwitchApplicationService applicationService;

    @Test
    void handleConnect_sendsAsyncFeedbackViaClientExecutor() throws CommandSyntaxException {
        RecordingExecutor executor = new RecordingExecutor();
        List<Component> feedbacks = new ArrayList<>();
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        TwitchCommand<Object> command = new TwitchCommand<>(applicationService, executor);
        CompletableFuture<Void> connectFuture = new CompletableFuture<>();

        dispatcher.register(command.build(source -> feedbacks::add));
        when(applicationService.isAuthenticated()).thenReturn(true);
        when(applicationService.connect(new Login("streamer"))).thenReturn(connectFuture);

        dispatcher.execute("twitch connect streamer", new Object());

        verify(applicationService, times(1)).connect(new Login("streamer"));
        verify(applicationService, never()).disconnect(any());
        org.junit.jupiter.api.Assertions.assertEquals(0, feedbacks.size());
        executor.runNext();
        org.junit.jupiter.api.Assertions.assertEquals(1, feedbacks.size());

        connectFuture.complete(null);

        org.junit.jupiter.api.Assertions.assertEquals(1, feedbacks.size());
        executor.runNext();
        org.junit.jupiter.api.Assertions.assertEquals(2, feedbacks.size());
    }

    private static final class RecordingExecutor implements Executor {
        private final Queue<Runnable> commands = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            commands.add(command);
        }

        void runNext() {
            commands.remove().run();
        }
    }
}

package org.etwas.streamtweaks.mod.forge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/**
 * このForgeバージョン (26.2-65.1.0) は EventBus 7 系を採用しており、各イベントは
 * {@code net.minecraftforge.eventbus.api.bus.EventBus} 型の静的 {@code BUS} フィールドを持つ。
 * 旧来の {@code @SubscribeEvent} + {@code MinecraftForge.EVENT_BUS.register(Class)}
 * は後方互換ヘルパー経由でのみ動作するため、ここでは新APIの {@code BUS.addListener(...)} を直接使う。
 */
public final class ForgeStreamTweaksClientEvents {
    private static final List<Runnable> END_CLIENT_TICK_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Runnable> CLIENT_DISCONNECT_LISTENERS = new CopyOnWriteArrayList<>();

    private ForgeStreamTweaksClientEvents() {}

    public static void registerEndClientTick(Runnable listener) {
        END_CLIENT_TICK_LISTENERS.add(listener);
    }

    public static void registerClientDisconnect(Runnable listener) {
        CLIENT_DISCONNECT_LISTENERS.add(listener);
    }

    static void register() {
        ClientTickEvent.Post.BUS.addListener(event -> END_CLIENT_TICK_LISTENERS.forEach(Runnable::run));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(
                event -> CLIENT_DISCONNECT_LISTENERS.forEach(Runnable::run));
    }
}

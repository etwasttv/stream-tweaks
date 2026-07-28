package org.etwas.streamtweaks.mod.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.etwas.streamtweaks.mod.StreamTweaksCommon;
import org.etwas.streamtweaks.mod.TwitchApplicationServices;
import org.etwas.streamtweaks.mod.TwitchClientBootstrap;

@Mod(value = NeoForgeStreamTweaksClient.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeStreamTweaksClient {
    public static final String MOD_ID = "stream_tweaks";

    public NeoForgeStreamTweaksClient(IEventBus modBus) {
        StreamTweaksCommon.LOGGER.info("Stream Tweaks initialized");
        TwitchApplicationServices.set(TwitchClientBootstrap.initialize(
                new NeoForgeClientPlatform(), NeoForgeTwitchCommandRegistrar::register));
        NeoForge.EVENT_BUS.register(NeoForgeStreamTweaksClientEvents.class);
    }
}

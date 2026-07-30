package org.etwas.streamtweaks.mod.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.etwas.streamtweaks.mod.StreamTweaksCommon;
import org.etwas.streamtweaks.mod.StreamTweaksConfigScreen;
import org.etwas.streamtweaks.mod.TwitchApplicationServices;
import org.etwas.streamtweaks.mod.TwitchClientBootstrap;

@Mod(ForgeStreamTweaksClient.MOD_ID)
public final class ForgeStreamTweaksClient {
    public static final String MOD_ID = "stream_tweaks";

    // context 自体は未使用だが、Forgeのコンストラクタインジェクション契約上
    // (FMLModContainer#constructMod がリフレクションで FMLJavaModLoadingContext 引数の
    // コンストラクタを探して呼び出す) 受け取る必要がある。イベントバス取得等は現時点で
    // 不要なため、NeoForge版 (IEventBus modBus を受け取るが使わない) と同様に引数は無視する。
    public ForgeStreamTweaksClient(FMLJavaModLoadingContext context) {
        StreamTweaksCommon.LOGGER.info("Stream Tweaks initialized");
        TwitchApplicationServices.set(
                TwitchClientBootstrap.initialize(new ForgeClientPlatform(), ForgeTwitchCommandRegistrar::register));
        ForgeStreamTweaksClientEvents.register();
        MinecraftForge.registerConfigScreen(parent -> new StreamTweaksConfigScreen(parent));
    }
}

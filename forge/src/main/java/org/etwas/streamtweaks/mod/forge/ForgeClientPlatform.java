package org.etwas.streamtweaks.mod.forge;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import org.etwas.streamtweaks.mod.platform.ClientPlatform;

public final class ForgeClientPlatform implements ClientPlatform {

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void executeOnClientThread(Runnable command) {
        Minecraft.getInstance().execute(command);
    }

    @Override
    public void registerEndClientTick(Runnable listener) {
        ForgeStreamTweaksClientEvents.registerEndClientTick(listener);
    }

    @Override
    public void registerClientDisconnect(Runnable listener) {
        ForgeStreamTweaksClientEvents.registerClientDisconnect(listener);
    }
}

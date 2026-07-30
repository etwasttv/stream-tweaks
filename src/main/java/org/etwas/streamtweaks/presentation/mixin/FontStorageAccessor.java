package org.etwas.streamtweaks.presentation.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import java.util.List;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.FontSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code remap = false} については {@link FontStorageMixin} のクラスコメントを参照
 * (Forge の Mixin AP が {@code official} マッピングチャンネルを認識しないための対処で、
 * Fabric/NeoForge には影響しない)。
 */
@Mixin(FontSet.class)
public interface FontStorageAccessor {

    @Accessor(value = "activeProviders", remap = false)
    List<GlyphProvider> getActiveProviders();

    @Accessor(value = "activeProviders", remap = false)
    void setActiveProviders(List<GlyphProvider> providers);

    @Accessor(value = "glyphCache", remap = false)
    CodepointMap<?> getGlyphCache();
}

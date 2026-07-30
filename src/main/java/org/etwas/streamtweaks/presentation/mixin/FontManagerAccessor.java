package org.etwas.streamtweaks.presentation.mixin;

import java.util.Map;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code remap = false} については {@link FontStorageMixin} のクラスコメントを参照
 * (Forge の Mixin AP が {@code official} マッピングチャンネルを認識しないための対処で、
 * Fabric/NeoForge には影響しない)。
 */
@Mixin(FontManager.class)
public interface FontManagerAccessor {

    @Accessor(value = "fontSets", remap = false)
    Map<Identifier, FontSet> getFontSets();
}

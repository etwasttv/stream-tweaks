package org.etwas.streamtweaks.presentation.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;
import org.etwas.streamtweaks.presentation.font.EmoteFontRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontManager.class)
public abstract class FontStorageMixin {

    /**
     * createFontSet() の戻り時に EmoteFont を注入する。
     * minecraft:default (チャットフォント) のみが対象。
     *
     * <p>{@code remap = false} について: このプロジェクトは Fabric / NeoForge / Forge の
     * 全ローダーで Mojang公式マッピング (Mojmap) を直接使い、SRG/intermediary へのリマップは
     * どのローダーでも行わない。ところが Forge (FG7) の Mixinアノテーションプロセッサ
     * (org.spongepowered:mixin:0.8.7:processor) が内蔵する {@code ObfuscationServiceMCP} は
     * {@code official} マッピングチャンネルを認識せず、{@code remap} 未指定 (デフォルト true)
     * のままだと SRG/notch 向けの obfuscation mapping を探しに行って解決できず
     * コンパイルエラーになる (公式の MinecraftForge/MDKExamples の mixins-only/fg7 サンプルでも
     * 同様に {@code remap = false} が明示されている)。Fabric/NeoForge 側はそもそもこの
     * ObfuscationServiceMCP の対象外なので影響を受けない。
     */
    @Inject(method = "createFontSet", at = @At("RETURN"), remap = false)
    private void onCreateFontSet(
            Identifier id,
            List<GlyphProvider.Conditional> providers,
            Set<FontOption> options,
            CallbackInfoReturnable<FontSet> cir) {
        if (!Minecraft.DEFAULT_FONT.equals(id)) {
            return;
        }
        EmoteFontRegistry.injectInto(cir.getReturnValue());
    }
}

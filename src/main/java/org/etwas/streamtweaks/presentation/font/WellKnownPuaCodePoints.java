package org.etwas.streamtweaks.presentation.font;

import java.util.Set;

/**
 * リソースパック「Redstone Tweaks」が独自フォント定義で使用しているUnicode私用領域(PUA)
 * コードポイント。
 *
 * <p>{@link PuaMapping}（絵文字用: U+F000〜U+F8FF）と {@link BadgePuaMapping}（バッジ用:
 * U+E000〜U+EFFF）の採番レンジと重なっており、Redstone Tweaksを併用した環境では対象のPUA文字が
 * 意図しないグリフ（GUIガイド画像や空白調整用の空グリフ）に上書きされて表示が壊れるため、
 * 両マッピングの採番時にこれらを避ける。
 */
final class WellKnownPuaCodePoints {

    static final Set<Integer> CODE_POINTS =
            Set.of(
                    // GUIガイド画像系（コンテナ操作説明のオーバーレイ等）
                    0xE001, 0xE002, 0xE003, 0xE004, 0xE005, 0xE006, 0xE007, 0xE008, 0xE009, 0xE010,
                    0xE011, 0xE012, 0xE013, 0xE014, 0xE015,
                    // アイコン系（コンパレータ/クロック）
                    0xE101, 0xE102,
                    // 幅調整用の空グリフ（negative-space padding）
                    0xF780, 0xF7C0, 0xF7E0, 0xF7F0, 0xF7F8, 0xF7FC, 0xF7FE, 0xF7FF, 0xF801, 0xF802,
                    0xF804, 0xF808, 0xF810, 0xF820, 0xF840, 0xF880);

    private WellKnownPuaCodePoints() {}
}

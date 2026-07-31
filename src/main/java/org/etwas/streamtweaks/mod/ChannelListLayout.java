package org.etwas.streamtweaks.mod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Connected Channelsリストのスクロール・レイアウトに関する純粋な計算ロジック。
 *
 * <p>{@link net.minecraft.client.gui.screens.Screen} や描画APIには依存せず、画面サイズ・購読数・
 * スクロール位置といったプリミティブ値だけを入力に取る。そのため {@code StreamTweaksConfigScreen}
 * を経由せずに単体テストできる（テストは {@code ChannelListLayoutTest} を参照）。
 *
 * <p>「No channels connected / 各チャンネル行」のテキストは{@code
 * StreamTweaksConfigScreen#extractRenderState}で{@code channelListVisibleLineCount() > 0}の
 * ときだけ描画される一方、Disconnectボタンは{@code addRenderableWidget}で個別に配置される別の
 * 描画パスを通るため、両者が同じY座標計算・同じ行の高さ・同じ「表示領域に収まるか」の判定を
 * 共有していることをここに集約して保証する。
 *
 * <p>以前はテキスト側を{@code enableScissor}/{@code disableScissor}でクリップしていたが、表示領域の
 * 高さが0になるケース（Minecraftのデフォルト起動解像度でも発生し得る）でクリップ矩形が空になり、
 * テキストが完全に不可視になる不具合があったため、クリッピングではなく本クラスの計算結果で
 * 描画可否を判定する方式に変更した。
 */
final class ChannelListLayout {

    /**
     * Disconnectボタンをテキスト行の基準Y座標からどれだけ上にずらして配置するか(px)。
     * {@code StreamTweaksConfigScreen}のボタン配置（{@code y - 4}）と同じ値をここに集約し、
     * テストがテキスト行とボタンの実際の位置関係（ボタンの下端が表示領域をはみ出さないか）を
     * 検証できるようにする。
     */
    static final int BUTTON_Y_OFFSET = -4;

    private ChannelListLayout() {}

    /** Connected Channelsリスト表示領域の上端Y座標。 */
    static int areaTop(int yChannelListStart) {
        return yChannelListStart - 2;
    }

    /** Connected Channelsリスト表示領域の下端Y座標。Backボタンの上に一定のマージンを確保する。 */
    static int areaBottom(int screenHeight, int backButtonYOffsetFromBottom, int bottomMargin, int areaTop) {
        int backY = screenHeight - backButtonYOffsetFromBottom;
        return Math.max(areaTop, backY - bottomMargin);
    }

    /** 表示領域内に収まる行数（画面サイズに応じて変動）。 */
    static int visibleLineCount(int areaTop, int areaBottom, int rowHeight) {
        return Math.max(0, (areaBottom - areaTop) / rowHeight);
    }

    /** 現在の購読数・表示領域から算出されるスクロールオフセットの最大値。 */
    static int maxScrollOffset(int totalEntryCount, int visibleLineCount) {
        return Math.max(0, totalEntryCount - visibleLineCount);
    }

    /** スクロールオフセットを有効範囲 [0, maxScrollOffset] にクランプする。 */
    static int clampScrollOffset(int scrollOffset, int maxScrollOffset) {
        return Mth.clamp(scrollOffset, 0, maxScrollOffset);
    }

    /**
     * 表示すべきエントリのY座標（テキスト描画基準）のリストを返す。
     *
     * <p>返る各Y座標は必ず {@code areaTop <= y} かつ {@code y + rowHeight <= areaBottom} を
     * 満たす（{@code visibleLineCount}がfloor除算で算出されているため）。Disconnectボタンは
     * {@code y + BUTTON_Y_OFFSET}を上端として高さ{@code rowHeight}で配置される想定であり、
     * この条件が保たれる限りボタンの下端が{@code areaBottom}をはみ出すことはない。
     */
    static List<Integer> visibleEntryYPositions(
            int totalEntryCount, int scrollOffset, int areaTop, int visibleLineCount, int rowHeight) {
        int clampedOffset = clampScrollOffset(scrollOffset, maxScrollOffset(totalEntryCount, visibleLineCount));
        int end = Math.min(totalEntryCount, clampedOffset + visibleLineCount);
        List<Integer> result = new ArrayList<>();
        int y = areaTop;
        for (int i = clampedOffset; i < end; i++) {
            result.add(y);
            y += rowHeight;
        }
        return result;
    }
}

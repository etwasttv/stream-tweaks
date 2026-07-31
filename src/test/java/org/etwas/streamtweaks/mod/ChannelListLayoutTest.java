package org.etwas.streamtweaks.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Connected Channelsリストのスクロール・レイアウト計算（{@link ChannelListLayout}）のテスト。
 *
 * <p>レビュー指摘: {@code extractRenderState()} 内のテキスト描画（かつて
 * {@code enableScissor}/{@code disableScissor} でクリップしていたが、表示領域の高さが0になると
 * クリップ矩形が空になりテキストが完全に不可視になる不具合があったため、現在は
 * {@code channelListVisibleLineCount() > 0} の判定で描画可否を制御する方式に変更した）と、
 * {@code addRenderableWidget} で追加される Disconnectボタン（別の描画パスを通る）は、
 * {@code channelListVisibleLineCount()}（floor除算）とボタンのY座標計算が数式上整合していることで
 * 範囲内に収まっているが、この整合はテストで保証されていなかった。
 *
 * <p>このテストは {@code StreamTweaksConfigScreen} が実際に使っている定数（{@code BUTTON_HEIGHT},
 * {@code Y_CHANNEL_LIST_START}, {@code CHANNEL_LIST_BOTTOM_MARGIN},
 * {@code BACK_BUTTON_Y_OFFSET_FROM_BOTTOM}）と {@link ChannelListLayout#BUTTON_Y_OFFSET}
 * を用いて、幅広い画面サイズ・購読チャンネル数・スクロール位置の組み合わせについて
 * 「表示される各エントリのDisconnectボタンの下端が表示領域(areaBottom)をはみ出さない」ことを
 * 検証する。将来 BUTTON_HEIGHT やマージン定数だけが変更された場合でも、この不変条件が破れれば
 * ここで失敗する。
 */
class ChannelListLayoutTest {

    private static final int BUTTON_HEIGHT = StreamTweaksConfigScreen.BUTTON_HEIGHT;
    private static final int Y_CHANNEL_LIST_START = StreamTweaksConfigScreen.Y_CHANNEL_LIST_START;
    private static final int CHANNEL_LIST_BOTTOM_MARGIN = StreamTweaksConfigScreen.CHANNEL_LIST_BOTTOM_MARGIN;
    private static final int BACK_BUTTON_Y_OFFSET_FROM_BOTTOM =
            StreamTweaksConfigScreen.BACK_BUTTON_Y_OFFSET_FROM_BOTTOM;

    @Test
    void areaTopIsYChannelListStartMinusTwo() {
        assertEquals(Y_CHANNEL_LIST_START - 2, ChannelListLayout.areaTop(Y_CHANNEL_LIST_START));
    }

    @Test
    void areaBottomNeverGoesAboveAreaTop() {
        int areaTop = ChannelListLayout.areaTop(Y_CHANNEL_LIST_START);
        // 極端に低い画面高さでも areaBottom < areaTop にはならない（負の表示領域を作らない）。
        for (int screenHeight = 0; screenHeight <= 300; screenHeight++) {
            int areaBottom = ChannelListLayout.areaBottom(
                    screenHeight, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM, CHANNEL_LIST_BOTTOM_MARGIN, areaTop);
            assertTrue(
                    areaBottom >= areaTop,
                    "areaBottom must never be below areaTop (screenHeight=" + screenHeight + ")");
        }
    }

    /**
     * 本命のテスト: 表示される全エントリについて、Disconnectボタンの実際の見た目上の下端
     * （{@code y + ChannelListLayout.BUTTON_Y_OFFSET + BUTTON_HEIGHT}）が
     * {@code areaBottom} を超えないことを、多数の画面サイズ・購読数・スクロールオフセットで検証する。
     */
    @Test
    void disconnectButtonsNeverExtendBeyondListAreaAcrossManyScreenSizesAndEntryCounts() {
        int areaTop = ChannelListLayout.areaTop(Y_CHANNEL_LIST_START);

        // 7刻み: BUTTON_HEIGHT(20)と互いに素にすることで、割り切れる/割り切れない境界条件の両方を
        // 幅広くカバーする。
        for (int screenHeight = 40; screenHeight <= 2000; screenHeight += 7) {
            int areaBottom = ChannelListLayout.areaBottom(
                    screenHeight, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM, CHANNEL_LIST_BOTTOM_MARGIN, areaTop);
            int visibleLineCount = ChannelListLayout.visibleLineCount(areaTop, areaBottom, BUTTON_HEIGHT);

            for (int totalEntryCount = 0; totalEntryCount <= 30; totalEntryCount++) {
                int maxScrollOffset = ChannelListLayout.maxScrollOffset(totalEntryCount, visibleLineCount);

                // 範囲内・範囲外（負・過大）いずれのスクロールオフセットを渡してもクランプされ、
                // 返るY座標が必ず領域内に収まることを確認する。
                int[] candidateOffsets = {
                    -100, -1, 0, 1, maxScrollOffset, maxScrollOffset + 1, maxScrollOffset + 100
                };
                for (int rawOffset : candidateOffsets) {
                    List<Integer> yPositions = ChannelListLayout.visibleEntryYPositions(
                            totalEntryCount, rawOffset, areaTop, visibleLineCount, BUTTON_HEIGHT);

                    assertTrue(
                            yPositions.size() <= visibleLineCount,
                            "visible entries must not exceed the visible line count");
                    assertTrue(yPositions.size() <= totalEntryCount, "visible entries must not exceed total entries");

                    for (int y : yPositions) {
                        assertTrue(y >= areaTop, "entry y=" + y + " must not start above areaTop=" + areaTop);
                        assertTrue(
                                y < areaBottom,
                                "entry text y=" + y + " must start within the visible area (areaBottom=" + areaBottom
                                        + ")");

                        int disconnectButtonBottom = y + ChannelListLayout.BUTTON_Y_OFFSET + BUTTON_HEIGHT;
                        assertTrue(
                                disconnectButtonBottom <= areaBottom,
                                "Disconnect button bottom=" + disconnectButtonBottom
                                        + " must not exceed areaBottom=" + areaBottom + " (screenHeight="
                                        + screenHeight + ", totalEntryCount=" + totalEntryCount + ", rawOffset="
                                        + rawOffset + ")");
                    }
                }
            }
        }
    }

    @Test
    void scrollOffsetIsClampedToValidRange() {
        int areaTop = ChannelListLayout.areaTop(Y_CHANNEL_LIST_START);
        int areaBottom = ChannelListLayout.areaBottom(
                400, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM, CHANNEL_LIST_BOTTOM_MARGIN, areaTop);
        int visibleLineCount = ChannelListLayout.visibleLineCount(areaTop, areaBottom, BUTTON_HEIGHT);
        int totalEntryCount = 50;
        int maxScrollOffset = ChannelListLayout.maxScrollOffset(totalEntryCount, visibleLineCount);

        assertTrue(maxScrollOffset > 0, "precondition: this screen size must require scrolling for 50 entries");
        assertEquals(0, ChannelListLayout.clampScrollOffset(-10, maxScrollOffset));
        assertEquals(maxScrollOffset, ChannelListLayout.clampScrollOffset(maxScrollOffset + 100, maxScrollOffset));
        assertEquals(3, ChannelListLayout.clampScrollOffset(3, maxScrollOffset));
    }

    @Test
    void noEntriesWhenAreaTooSmallToFitAnyRow() {
        // areaBottomがareaTopまで潰れる極端に小さい画面高さでもクラッシュせず0件を返す。
        int areaTop = ChannelListLayout.areaTop(Y_CHANNEL_LIST_START);
        int tinyScreenHeight = 10;
        int areaBottom = ChannelListLayout.areaBottom(
                tinyScreenHeight, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM, CHANNEL_LIST_BOTTOM_MARGIN, areaTop);
        int visibleLineCount = ChannelListLayout.visibleLineCount(areaTop, areaBottom, BUTTON_HEIGHT);

        assertEquals(0, visibleLineCount);
        assertTrue(ChannelListLayout
                .visibleEntryYPositions(10, 0, areaTop, visibleLineCount, BUTTON_HEIGHT)
                .isEmpty());
    }

    /**
     * リグレッションテスト: Minecraftのデフォルト起動解像度（854x480、GUI拡大率Auto）では
     * GUI論理座標の高さがちょうど240pxになる（{@code Window.calculateScale} /
     * {@code Window.setGuiScale} の仕様: 854/2=427, 480/2=240 で、これ以上拡大すると
     * 320x240の最小要件を満たせなくなるためscale=2で確定する）。
     *
     * <p>この画面高さでは現在の定数（Y_CHANNEL_LIST_START, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM,
     * CHANNEL_LIST_BOTTOM_MARGIN）の組み合わせにより areaBottom が areaTop まで潰れ、
     * visibleLineCount が0になる。かつてはこの状態でも {@code enableScissor}/{@code
     * disableScissor} を使ってテキストを無条件に描画しようとしており、
     * {@code GuiGraphicsExtractor.ScissorStack.push()} が交差matrix高さ0のクリップ矩形を
     * 空矩形（0,0,0,0）に変換してしまうため「No channels connected」等のテキストが完全に
     * 不可視になる不具合があった（物理ウィンドウサイズ自体は854x480であり、見た目上は
     * 「小さいウィンドウ」には見えないため気づきにくい）。
     *
     * <p>このテストは、そもそもこの画面高さで visibleLineCount が0になること自体を固定化し、
     * {@code StreamTweaksConfigScreen#extractRenderState} が
     * {@code channelListVisibleLineCount() > 0} を満たす場合にのみリスト部分を描画する
     * ことで、この既知の潰れケースでもBackボタンとの重なりが起きないことを保証する前提を
     * 明示する。
     */
    @Test
    void visibleLineCountIsZeroAtVanillaDefaultLaunchResolution() {
        int areaTop = ChannelListLayout.areaTop(Y_CHANNEL_LIST_START);
        int vanillaDefaultGuiScaledHeight = 240;
        int areaBottom = ChannelListLayout.areaBottom(
                vanillaDefaultGuiScaledHeight, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM, CHANNEL_LIST_BOTTOM_MARGIN, areaTop);

        assertEquals(areaTop, areaBottom, "the list area collapses to zero height at the vanilla default resolution");
        assertEquals(0, ChannelListLayout.visibleLineCount(areaTop, areaBottom, BUTTON_HEIGHT));
    }

    @Test
    void allSubscribedChannelsFitWithoutScrollingWhenCountIsWithinVisibleLineCount() {
        int areaTop = ChannelListLayout.areaTop(Y_CHANNEL_LIST_START);
        int areaBottom = ChannelListLayout.areaBottom(
                400, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM, CHANNEL_LIST_BOTTOM_MARGIN, areaTop);
        int visibleLineCount = ChannelListLayout.visibleLineCount(areaTop, areaBottom, BUTTON_HEIGHT);

        int totalEntryCount = Math.max(0, visibleLineCount - 1);
        assertEquals(0, ChannelListLayout.maxScrollOffset(totalEntryCount, visibleLineCount));

        List<Integer> yPositions =
                ChannelListLayout.visibleEntryYPositions(totalEntryCount, 0, areaTop, visibleLineCount, BUTTON_HEIGHT);
        assertEquals(totalEntryCount, yPositions.size());
        assertEquals(areaTop, yPositions.get(0));
    }
}

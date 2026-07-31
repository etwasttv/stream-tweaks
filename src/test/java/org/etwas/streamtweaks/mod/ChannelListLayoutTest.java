package org.etwas.streamtweaks.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Connected Channelsリストのスクロール・レイアウト計算（{@link ChannelListLayout}）のテスト。
 *
 * <p>レビュー指摘: {@code extractRenderState()} 内の {@code enableScissor}/{@code
 * disableScissor} はテキスト描画のみをクリップしており、{@code addRenderableWidget} で追加される
 * Disconnectボタン自体は別の描画パスを通るためクリップされない。現状は
 * {@code channelListVisibleLineCount()}（floor除算）とボタンのY座標計算が数式上整合しているため
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

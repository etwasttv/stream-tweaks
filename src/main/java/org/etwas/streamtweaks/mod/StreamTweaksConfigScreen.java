package org.etwas.streamtweaks.mod;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.etwas.streamtweaks.config.StreamTweaksSettingsStore;

/**
 * Stream Tweaksの表示設定を扱う画面。
 *
 * <p>{@link TwitchConnectionScreen} から独立した画面で、トグル操作は即座に
 * {@link StreamTweaksSettingsStore}（write-throughキャッシュ、変更は即座にディスクへ保存される）
 * へ反映される。フッターのBackボタンは単に画面を閉じるだけ。
 */
public class StreamTweaksConfigScreen extends Screen {

    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int LABEL_COLOR = 0xAAAAAA;
    private static final int LINE_HEIGHT = 12;

    private static final int BUTTON_HEIGHT = 20;
    private static final int LABEL_COLUMN_WIDTH = 170;
    private static final int SHOW_BADGES_BUTTON_WIDTH = 70;
    private static final int BACK_BUTTON_WIDTH = 100;

    private static final int ROW_SPACING = 8;
    private static final int BODY_SPACING = 6;

    private static final int CONTENT_MIN_WIDTH = 280;
    private static final int MIN_SCROLL_AREA_HEIGHT = BUTTON_HEIGHT * 2;

    private final Screen parent;

    private HeaderAndFooterLayout layout;
    private ScrollableLayout scrollArea;

    public StreamTweaksConfigScreen(Screen parent) {
        super(Component.literal("Stream Tweaks Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        StreamTweaksSettingsStore settingsStore = StreamTweaksSettingsServices.get();

        this.layout = new HeaderAndFooterLayout(this);
        this.layout.addTitleHeader(this.title, this.font);

        LinearLayout body = LinearLayout.vertical().spacing(BODY_SPACING);
        body.defaultCellSetting().alignHorizontallyLeft();

        body.addChild(sectionLabel("Display Settings:"));
        if (settingsStore != null) {
            StringWidget showBadgesLabel = new StringWidget(
                    LABEL_COLUMN_WIDTH,
                    LINE_HEIGHT,
                    Component.literal("Show Twitch Badges").withColor(TEXT_COLOR),
                    this.font);
            // "Show Twitch Badges" というラベルはStringWidgetとして描画し、ボタンには
            // displayOnlyValue() で値（Enabled/Disabled）のみを表示させる。name には
            // narration（読み上げ）用にラベルを渡す。
            CycleButton<Boolean> showBadgesBtn = CycleButton.booleanBuilder(
                            Component.literal("Enabled"), Component.literal("Disabled"), settingsStore.showBadges())
                    .displayOnlyValue()
                    .create(
                            0,
                            0,
                            SHOW_BADGES_BUTTON_WIDTH,
                            BUTTON_HEIGHT,
                            Component.literal("Show Twitch Badges"),
                            (button, value) -> settingsStore.setShowBadges(value));
            body.addChild(row(showBadgesLabel, showBadgesBtn));
        } else {
            body.addChild(new StringWidget(
                    Component.literal("  Settings unavailable").withColor(LABEL_COLOR), this.font));
        }

        this.scrollArea = new ScrollableLayout(this.minecraft, body, computeScrollAreaMaxHeight());
        this.scrollArea.setMinWidth(CONTENT_MIN_WIDTH);
        this.layout.addToContents(this.scrollArea);

        Button backBtn = Button.builder(Component.translatable("gui.back"), button -> this.onClose())
                .width(BACK_BUTTON_WIDTH)
                .build();
        LinearLayout footer = LinearLayout.horizontal().spacing(ROW_SPACING);
        footer.addChild(backBtn);
        this.layout.addToFooter(footer);

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    private int computeScrollAreaMaxHeight() {
        return Math.max(MIN_SCROLL_AREA_HEIGHT, this.layout.getContentHeight());
    }

    private StringWidget sectionLabel(String text) {
        return new StringWidget(Component.literal(text).withColor(LABEL_COLOR), this.font);
    }

    private LinearLayout row(LayoutElement left, LayoutElement right) {
        LinearLayout row = LinearLayout.horizontal().spacing(ROW_SPACING);
        row.defaultCellSetting().alignVerticallyMiddle();
        row.addChild(left);
        row.addChild(right);
        return row;
    }

    @Override
    protected void repositionElements() {
        // TwitchConnectionScreenと同じ理由でrebuildWidgets()を避け、再配置のみ行う。
        if (this.layout == null) {
            return;
        }
        if (this.scrollArea != null) {
            this.scrollArea.arrangeElements();
            this.scrollArea.setMaxHeight(computeScrollAreaMaxHeight());
        }
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}

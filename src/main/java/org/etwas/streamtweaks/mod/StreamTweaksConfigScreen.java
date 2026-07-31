package org.etwas.streamtweaks.mod;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.etwas.streamtweaks.application.TwitchApplicationService;
import org.etwas.streamtweaks.config.StreamTweaksSettingsStore;
import org.etwas.streamtweaks.twitch.core.Login;

public class StreamTweaksConfigScreen extends Screen {

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int LINE_HEIGHT = 12;

    private static final int LEFT_OFFSET = 100;
    // BUTTON_HEIGHT, Y_CHANNEL_LIST_START, CHANNEL_LIST_BOTTOM_MARGIN, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM は
    // ChannelListLayoutTest（同一パッケージのユニットテスト）から実際のレイアウト定数として参照するため、
    // あえて private にせずパッケージプライベートにしている。
    static final int BUTTON_HEIGHT = 20;
    private static final int Y_AUTH_LABEL = 48;
    private static final int Y_AUTH_STATUS = 62;
    private static final int Y_CONNECT_LABEL = 82;
    private static final int Y_CONNECT_FIELD = 96;
    private static final int Y_CONNECT_OWN_FIELD = 116;
    private static final int Y_DISPLAY_SETTINGS_LABEL = 148;
    private static final int Y_SHOW_BADGES_LABEL = 162;
    private static final int SHOW_BADGES_BUTTON_WIDTH = 70;
    private static final int Y_CHANNEL_LABEL = 194;
    static final int Y_CHANNEL_LIST_START = 208;
    /** Backボタン（画面下部）とスクロールリスト表示領域の間に確保する余白(px)。 */
    static final int CHANNEL_LIST_BOTTOM_MARGIN = 8;
    /** Connected Channelsリストの表示領域の左右幅（Disconnectボタンを含む）。 */
    private static final int CHANNEL_LIST_AREA_WIDTH = 240;
    /** Backボタンを画面下端からどれだけ上に配置するか(px)。 */
    static final int BACK_BUTTON_Y_OFFSET_FROM_BOTTOM = 30;

    private final Screen parent;

    private String channelInputText = "";

    /** True while an async operation is in progress; disables interactive widgets. */
    private boolean busy = false;

    private EditBox channelField;
    private Button connectBtn;
    private List<Login> subscribedLogins = List.of();

    /** Connected Channelsリストの縦スクロール位置（先頭から何行分スクロールしたか）。 */
    private int channelListScrollOffset = 0;

    /**
     * 現在追加済みのDisconnectボタン一覧。スクロール操作時にこのリストのウィジェットだけを
     * removeWidget/addRenderableWidgetで差し替えることで、channelFieldなど他のウィジェットの
     * フォーカスを保ったままリスト表示を更新できるようにする（画面全体のrebuildWidgetsを避ける）。
     */
    private final List<Button> channelListButtonWidgets = new ArrayList<>();

    public StreamTweaksConfigScreen(Screen parent) {
        super(Component.literal("Stream Tweaks"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int leftX = centerX - LEFT_OFFSET;

        TwitchApplicationService service = TwitchApplicationServices.get();
        // 認証情報ファイルの読み込みは init() あたり1回に抑える。読み取った値を使い回し、
        // 自チャンネル接続済みかどうかはメモリ上の subscribedLogins（ファイルI/Oなし）で判定する。
        String authenticatedLogin = service != null ? service.getAuthenticatedLogin() : null;
        boolean authenticated = authenticatedLogin != null;

        subscribedLogins = service != null ? List.copyOf(service.getSubscribedLogins()) : List.of();

        if (authenticated) {
            int logoutX = leftX + 160;
            Button logoutBtn = Button.builder(Component.literal("Logout"), button -> doLogout(service))
                    .bounds(logoutX, Y_AUTH_STATUS - 4, 60, BUTTON_HEIGHT)
                    .build();
            logoutBtn.active = !busy;
            this.addRenderableWidget(logoutBtn);
        } else {
            int loginX = leftX + 160;
            Button loginBtn = Button.builder(Component.literal("Login"), button -> doLogin(service))
                    .bounds(loginX, Y_AUTH_STATUS - 4, 60, BUTTON_HEIGHT)
                    .build();
            loginBtn.active = !busy && service != null;
            this.addRenderableWidget(loginBtn);
        }

        channelField =
                new EditBox(this.font, leftX, Y_CONNECT_FIELD, 150, BUTTON_HEIGHT, Component.literal("Channel name"));
        channelField.setValue(channelInputText);
        channelField.setEditable(authenticated && !busy);
        channelField.setResponder(text -> {
            channelInputText = text;
            updateConnectButton();
        });
        this.addRenderableWidget(channelField);

        int connectX = leftX + 154;
        connectBtn = Button.builder(Component.literal("Connect"), button -> doConnect(service))
                .bounds(connectX, Y_CONNECT_FIELD, 60, BUTTON_HEIGHT)
                .build();
        connectBtn.active = authenticated && !busy && isValidChannelName(channelInputText);
        this.addRenderableWidget(connectBtn);

        boolean connectedToOwnChannel = authenticatedLogin != null
                && subscribedLogins.stream().anyMatch(login -> login.value().equalsIgnoreCase(authenticatedLogin));
        Button connectOwnBtn = Button.builder(
                        Component.literal("Connect to My Channel"), button -> doConnectOwnChannel(service))
                .bounds(leftX, Y_CONNECT_OWN_FIELD, 214, BUTTON_HEIGHT)
                .build();
        connectOwnBtn.active = authenticated && !busy && !connectedToOwnChannel;
        this.addRenderableWidget(connectOwnBtn);

        StreamTweaksSettingsStore settingsStore = StreamTweaksSettingsServices.get();
        if (settingsStore != null) {
            int showBadgesButtonX = leftX + 160;
            // "Show Twitch Badges" というラベルは extractRenderState() 側で他のラベルと同様に
            // context.text(...) で描画し、ボタンには displayOnlyValue() で値（Enabled/Disabled）のみを
            // 表示させる。name には narration（読み上げ）用にラベルを渡す。
            CycleButton<Boolean> showBadgesBtn = CycleButton.booleanBuilder(
                            Component.literal("Enabled"), Component.literal("Disabled"), settingsStore.showBadges())
                    .displayOnlyValue()
                    .create(
                            showBadgesButtonX,
                            Y_SHOW_BADGES_LABEL - 4,
                            SHOW_BADGES_BUTTON_WIDTH,
                            BUTTON_HEIGHT,
                            Component.literal("Show Twitch Badges"),
                            (button, value) -> settingsStore.setShowBadges(value));
            // 他のウィジェットと異なり意図的に .active = !busy を設定していない。
            // このトグルはネットワークI/Oを伴わないローカル設定の変更のみのため、
            // ログイン/ログアウト/接続処理中（busy中）でも操作可能にしてよいという設計判断による。
            this.addRenderableWidget(showBadgesBtn);
        }

        // Disconnectボタンの生成・配置はrefreshChannelListButtons()に集約する。
        // mouseScrolled()からも同じメソッドを呼び出し、他のウィジェットのフォーカスに影響を与えず
        // このボタン群だけを差し替えられるようにするため。
        // （このinit()自体がrebuildWidgets経由で呼ばれた場合はclearWidgets()で既存ウィジェットは
        // 消えているが、channelListButtonWidgetsフィールドは自前管理のため、
        // refreshChannelListButtons()内でこのリストを都度クリアしてから作り直す。）
        refreshChannelListButtons();

        int backWidth = 100;
        int backX = (this.width - backWidth) / 2;
        int backY = this.height - BACK_BUTTON_Y_OFFSET_FROM_BOTTOM;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> this.onClose())
                .bounds(backX, backY, backWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int leftX = centerX - LEFT_OFFSET;

        context.centeredText(this.font, this.title, centerX, 16, TEXT_COLOR);

        context.text(this.font, Component.literal("Twitch Connection Status:"), leftX, Y_AUTH_LABEL, LABEL_COLOR);

        TwitchApplicationService service = TwitchApplicationServices.get();
        String loginName = service != null ? service.getAuthenticatedLogin() : null;

        if (loginName != null) {
            context.text(
                    this.font, Component.literal("  Authenticated: " + loginName), leftX, Y_AUTH_STATUS, 0xFF55FF55);
        } else {
            context.text(this.font, Component.literal("  Not authenticated"), leftX, Y_AUTH_STATUS, 0xFFFF5555);
        }

        context.text(this.font, Component.literal("Connect to Channel:"), leftX, Y_CONNECT_LABEL, LABEL_COLOR);

        context.text(this.font, Component.literal("Display Settings:"), leftX, Y_DISPLAY_SETTINGS_LABEL, LABEL_COLOR);
        context.text(this.font, Component.literal("Show Twitch Badges"), leftX, Y_SHOW_BADGES_LABEL, TEXT_COLOR);

        context.text(this.font, Component.literal("Connected Channels:"), leftX, Y_CHANNEL_LABEL, LABEL_COLOR);

        // Connected Channelsのリスト部分（No channels connected / 各チャンネル行）は
        // Backボタンと重ならない固定領域(channelListAreaTop〜channelListAreaBottom)に収める。
        //
        // 以前は context.enableScissor(...)/disableScissor() でこの領域をクリップしていたが、
        // GuiGraphicsExtractor.ScissorStack.push() は「新しい矩形と現在の矩形の交差」を計算する際、
        // ScreenRectangle.intersection() の判定が `top < bottom` という厳密不等号であるため、
        // channelListAreaBottom() == channelListAreaTop()（＝表示領域の高さが0）になった瞬間に
        // 交差がnull扱いとなり ScreenRectangle.empty()（0,0,0,0）が積まれ、以降このスコープで
        // 描画するテキストが完全に不可視になってしまうバグがあった。
        // このケースは決して極端な最小ウィンドウでのみ起きるわけではなく、Minecraftのデフォルト
        // 起動解像度（854x480、GUI拡大率Auto）でGUI論理座標の高さがちょうど240pxになる場合に
        // 現在の定数（Y_CHANNEL_LIST_START, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM,
        // CHANNEL_LIST_BOTTOM_MARGIN）の組み合わせで実際に発生し、物理ウィンドウサイズを見た目で
        // 判断すると「十分広い」と感じても再現し得る。
        //
        // 行の表示可否は既に visibleEntryYPositions()（channelListVisibleLineCount()内部で
        // 使われる同じfloor除算ロジック）が表示領域に収まる分だけを返す設計になっており、
        // scissorはあくまで「保険」的な位置づけだった。空リスト時のテキストにも同じ
        // channelListVisibleLineCount() > 0 の判定を適用することで、
        // - 表示領域に1行分でも収まるなら（ほとんどの実用的な画面サイズ）scissorに頼らず必ず表示し、
        // - 収まらないほど極端に低い画面では、Backボタンと重ならないよう描画自体を省略する
        // という、rowsと空リスト表示で一貫した挙動にする。
        if (channelListVisibleLineCount() > 0) {
            if (subscribedLogins.isEmpty()) {
                // チャンネル行と同じ channelListAreaTop() を基準にすることで、
                // 空リスト時のテキストと実際のチャンネル行のY座標が一致するようにする。
                context.text(
                        this.font,
                        Component.literal("  No channels connected"),
                        leftX,
                        channelListAreaTop(),
                        0xFF888888);
            } else {
                for (Map.Entry<Login, Integer> entry : visibleChannelEntries()) {
                    context.text(
                            this.font,
                            Component.literal("  - " + entry.getKey().value()),
                            leftX,
                            entry.getValue(),
                            TEXT_COLOR);
                }
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isInsideChannelListArea(mouseX, mouseY)) {
            int maxOffset = channelListMaxScrollOffset();
            if (maxOffset <= 0) {
                return false;
            }
            int newOffset = Mth.clamp(channelListScrollOffset - (int) Math.signum(scrollY), 0, maxOffset);
            if (newOffset != channelListScrollOffset) {
                channelListScrollOffset = newOffset;
                // Disconnectボタンの位置がスクロールオフセットに応じて変わるため再配置する。
                // reinitialize()（画面全体のrebuildWidgets）を呼ぶとchannelFieldなど他の
                // ウィジェットも作り直されてフォーカスを失うため、Disconnectボタンだけを
                // removeWidget/addRenderableWidgetで差し替える。
                refreshChannelListButtons();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // -------------------------------------------------------------------------
    // Async action handlers
    // -------------------------------------------------------------------------

    private void doLogin(TwitchApplicationService service) {
        if (service == null || busy) {
            return;
        }
        busy = true;
        reinitialize();
        service.login(uri -> Util.getPlatform().openUri(uri))
                .thenAccept(result -> Minecraft.getInstance().execute(() -> {
                    if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                        return;
                    }
                    busy = false;
                    reinitialize();
                }))
                .exceptionally(ex -> {
                    Minecraft.getInstance().execute(() -> {
                        if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                            return;
                        }
                        StreamTweaksCommon.LOGGER.error("Failed to login", ex);
                        busy = false;
                        reinitialize();
                    });
                    return null;
                });
    }

    private void doLogout(TwitchApplicationService service) {
        if (service == null || busy) {
            return;
        }
        busy = true;
        reinitialize();
        service.logout()
                .thenRun(() -> Minecraft.getInstance().execute(() -> {
                    if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                        return;
                    }
                    busy = false;
                    reinitialize();
                }))
                .exceptionally(ex -> {
                    Minecraft.getInstance().execute(() -> {
                        if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                            return;
                        }
                        StreamTweaksCommon.LOGGER.error("Failed to logout", ex);
                        busy = false;
                        reinitialize();
                    });
                    return null;
                });
    }

    private void doConnect(TwitchApplicationService service) {
        if (service == null || busy || !isValidChannelName(channelInputText)) {
            return;
        }
        busy = true;
        Login target = new Login(channelInputText.strip());
        reinitialize();
        service.connect(target)
                .thenRun(() -> Minecraft.getInstance().execute(() -> {
                    if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                        return;
                    }
                    busy = false;
                    channelInputText = "";
                    reinitialize();
                }))
                .exceptionally(ex -> {
                    Minecraft.getInstance().execute(() -> {
                        if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                            return;
                        }
                        StreamTweaksCommon.LOGGER.error("Failed to connect to channel", ex);
                        busy = false;
                        reinitialize();
                    });
                    return null;
                });
    }

    private void doConnectOwnChannel(TwitchApplicationService service) {
        if (service == null || busy) {
            return;
        }
        String ownLogin = service.getAuthenticatedLogin();
        if (ownLogin == null) {
            return;
        }
        busy = true;
        Login target = new Login(ownLogin);
        reinitialize();
        service.connect(target)
                .thenRun(() -> Minecraft.getInstance().execute(() -> {
                    if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                        return;
                    }
                    busy = false;
                    reinitialize();
                }))
                .exceptionally(ex -> {
                    Minecraft.getInstance().execute(() -> {
                        if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                            return;
                        }
                        StreamTweaksCommon.LOGGER.error("Failed to connect to own channel", ex);
                        busy = false;
                        reinitialize();
                    });
                    return null;
                });
    }

    private void doDisconnect(TwitchApplicationService service, Login login) {
        if (service == null || busy) {
            return;
        }
        busy = true;
        reinitialize();
        service.disconnect(login)
                .thenRun(() -> Minecraft.getInstance().execute(() -> {
                    if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                        return;
                    }
                    busy = false;
                    reinitialize();
                }))
                .exceptionally(ex -> {
                    Minecraft.getInstance().execute(() -> {
                        if (this.minecraft == null || this.minecraft.gui.screen() != this) {
                            return;
                        }
                        StreamTweaksCommon.LOGGER.error("Failed to disconnect from channel", ex);
                        busy = false;
                        reinitialize();
                    });
                    return null;
                });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void reinitialize() {
        this.rebuildWidgets();
    }

    private void updateConnectButton() {
        if (connectBtn == null) {
            return;
        }
        TwitchApplicationService service = TwitchApplicationServices.get();
        boolean authenticated = service != null && service.getAuthenticatedLogin() != null;
        connectBtn.active = authenticated && !busy && isValidChannelName(channelInputText);
    }

    /**
     * Disconnectボタンだけを removeWidget → addRenderableWidget で差し替える。
     *
     * <p>スクロールのたびに {@code reinitialize()}（画面全体の rebuildWidgets）を呼ぶと、
     * {@code channelField} など他のウィジェットも作り直されてしまい、入力中にチャンネルリストを
     * ホイール操作するとテキストフィールドのフォーカス（キャレット等）が失われる問題があった。
     * {@code Screen#removeWidget} はフォーカス中のウィジェット自身が削除対象のときだけ
     * フォーカスを解除する実装のため、ここで触れるのはDisconnectボタンのみに限定し、
     * channelField 等のフォーカス状態には一切影響を与えない。
     */
    private void refreshChannelListButtons() {
        for (Button existing : channelListButtonWidgets) {
            this.removeWidget(existing);
        }
        channelListButtonWidgets.clear();

        TwitchApplicationService service = TwitchApplicationServices.get();
        int centerX = this.width / 2;
        int leftX = centerX - LEFT_OFFSET;
        int disconnectX = leftX + 160;
        for (Map.Entry<Login, Integer> entry : visibleChannelEntries()) {
            final Login target = entry.getKey();
            int y = entry.getValue();
            Button disconnectBtn = Button.builder(
                            Component.literal("Disconnect"), button -> doDisconnect(service, target))
                    .bounds(disconnectX, y + ChannelListLayout.BUTTON_Y_OFFSET, 80, BUTTON_HEIGHT)
                    .build();
            disconnectBtn.active = !busy;
            this.addRenderableWidget(disconnectBtn);
            channelListButtonWidgets.add(disconnectBtn);
        }
    }

    /** Connected Channelsリスト表示領域の上端Y座標。 */
    private int channelListAreaTop() {
        return ChannelListLayout.areaTop(Y_CHANNEL_LIST_START);
    }

    /** Connected Channelsリスト表示領域の下端Y座標。Backボタンの上に一定のマージンを確保する。 */
    private int channelListAreaBottom() {
        return ChannelListLayout.areaBottom(
                this.height, BACK_BUTTON_Y_OFFSET_FROM_BOTTOM, CHANNEL_LIST_BOTTOM_MARGIN, channelListAreaTop());
    }

    /** 表示領域内に収まる行数（画面サイズに応じて変動）。 */
    private int channelListVisibleLineCount() {
        return ChannelListLayout.visibleLineCount(channelListAreaTop(), channelListAreaBottom(), BUTTON_HEIGHT);
    }

    /** 現在の購読数・表示領域から算出されるスクロールオフセットの最大値。 */
    private int channelListMaxScrollOffset() {
        return ChannelListLayout.maxScrollOffset(subscribedLogins.size(), channelListVisibleLineCount());
    }

    /** 画面リサイズや購読数の変化後でも channelListScrollOffset が範囲内に収まるよう補正する。 */
    private void clampChannelListScrollOffset() {
        channelListScrollOffset =
                ChannelListLayout.clampScrollOffset(channelListScrollOffset, channelListMaxScrollOffset());
    }

    private List<Map.Entry<Login, Integer>> visibleChannelEntries() {
        clampChannelListScrollOffset();
        int start = channelListScrollOffset;
        List<Integer> yPositions = ChannelListLayout.visibleEntryYPositions(
                subscribedLogins.size(),
                channelListScrollOffset,
                channelListAreaTop(),
                channelListVisibleLineCount(),
                BUTTON_HEIGHT);
        List<Map.Entry<Login, Integer>> result = new ArrayList<>();
        for (int i = 0; i < yPositions.size(); i++) {
            result.add(new AbstractMap.SimpleImmutableEntry<>(subscribedLogins.get(start + i), yPositions.get(i)));
        }
        return result;
    }

    /** マウス座標がConnected Channelsリストの表示領域内かどうか判定する。 */
    private boolean isInsideChannelListArea(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int leftX = centerX - LEFT_OFFSET;
        int areaLeft = leftX;
        int areaRight = leftX + CHANNEL_LIST_AREA_WIDTH;
        int areaTop = channelListAreaTop();
        int areaBottom = channelListAreaBottom();
        return mouseX >= areaLeft && mouseX < areaRight && mouseY >= areaTop && mouseY < areaBottom;
    }

    private static boolean isValidChannelName(String text) {
        return text != null && text.strip().matches("^[a-zA-Z0-9_]{1,25}$");
    }
}

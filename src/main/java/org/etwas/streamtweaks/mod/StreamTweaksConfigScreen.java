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
import net.minecraft.util.Util;
import org.etwas.streamtweaks.application.TwitchApplicationService;
import org.etwas.streamtweaks.config.StreamTweaksSettingsStore;
import org.etwas.streamtweaks.twitch.core.Login;

public class StreamTweaksConfigScreen extends Screen {

    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int LINE_HEIGHT = 12;

    private static final int LEFT_OFFSET = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int Y_AUTH_LABEL = 48;
    private static final int Y_AUTH_STATUS = 62;
    private static final int Y_CONNECT_LABEL = 82;
    private static final int Y_CONNECT_FIELD = 96;
    private static final int Y_CONNECT_OWN_FIELD = 116;
    private static final int Y_DISPLAY_SETTINGS_LABEL = 148;
    private static final int Y_SHOW_BADGES_TOGGLE = 162;
    private static final int Y_CHANNEL_LABEL = 194;
    private static final int Y_CHANNEL_LIST_START = 208;

    private final Screen parent;

    private String channelInputText = "";

    /** True while an async operation is in progress; disables interactive widgets. */
    private boolean busy = false;

    private EditBox channelField;
    private Button connectBtn;
    private List<Login> subscribedLogins = List.of();

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
            CycleButton<Boolean> showBadgesBtn = CycleButton.onOffBuilder(settingsStore.showBadges())
                    .create(
                            leftX,
                            Y_SHOW_BADGES_TOGGLE,
                            214,
                            BUTTON_HEIGHT,
                            Component.literal("Show Twitch Badges"),
                            (button, value) -> settingsStore.setShowBadges(value));
            // 他のウィジェットと異なり意図的に .active = !busy を設定していない。
            // このトグルはネットワークI/Oを伴わないローカル設定の変更のみのため、
            // ログイン/ログアウト/接続処理中（busy中）でも操作可能にしてよいという設計判断による。
            this.addRenderableWidget(showBadgesBtn);
        }

        int disconnectX = leftX + 160;
        for (Map.Entry<Login, Integer> entry : visibleChannelEntries()) {
            final Login target = entry.getKey();
            int y = entry.getValue();
            Button disconnectBtn = Button.builder(
                            Component.literal("Disconnect"), button -> doDisconnect(service, target))
                    .bounds(disconnectX, y - 4, 80, BUTTON_HEIGHT)
                    .build();
            disconnectBtn.active = !busy;
            this.addRenderableWidget(disconnectBtn);
        }

        int backWidth = 100;
        int backX = (this.width - backWidth) / 2;
        int backY = this.height - 30;
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

        context.text(this.font, Component.literal("Connected Channels:"), leftX, Y_CHANNEL_LABEL, LABEL_COLOR);

        if (subscribedLogins.isEmpty()) {
            context.text(
                    this.font, Component.literal("  No channels connected"), leftX, Y_CHANNEL_LIST_START, 0xFF888888);
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

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
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

    private List<Map.Entry<Login, Integer>> visibleChannelEntries() {
        List<Map.Entry<Login, Integer>> result = new ArrayList<>();
        int y = Y_CHANNEL_LIST_START;
        int maxY = this.height - 40;
        for (Login login : subscribedLogins) {
            if (y + BUTTON_HEIGHT > maxY) {
                break;
            }
            result.add(new AbstractMap.SimpleImmutableEntry<>(login, y));
            y += BUTTON_HEIGHT;
        }
        return result;
    }

    private static boolean isValidChannelName(String text) {
        return text != null && text.strip().matches("^[a-zA-Z0-9_]{1,25}$");
    }
}

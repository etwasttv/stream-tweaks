package org.etwas.streamtweaks.presentation.chat.twitch;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.etwas.streamtweaks.platform.PlatformId;
import org.etwas.streamtweaks.presentation.chat.IntegratedChatMessagePresenter;
import org.etwas.streamtweaks.presentation.chat.NormalizedChatMessage;
import org.etwas.streamtweaks.presentation.font.EmoteDownloader;
import org.etwas.streamtweaks.presentation.font.EmoteFontRegistry;
import org.etwas.streamtweaks.presentation.font.EmoteGlyph;
import org.etwas.streamtweaks.presentation.font.PuaMapping;
import org.etwas.streamtweaks.twitch.subscription.event.ChatMessageNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Twitchの channel.chat.message 通知を、絵文字解決・ユーザー名色決定といったTwitch固有の
 * 変換を経て {@link NormalizedChatMessage} に変換し、{@link IntegratedChatMessagePresenter} へ渡す。
 *
 * <p>Component組み立て後のチャット送信・削除索引への登録は {@link IntegratedChatMessagePresenter}
 * の責務であり、このクラスは行わない。
 */
public class TwitchChatMessagePresenter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchChatMessagePresenter.class);
    private static final String ANIMATED_FORMAT = "animated";

    private final PuaMapping puaMapping;
    private final EmoteDownloader emoteDownloader;
    private final Gson gson;
    private final IntegratedChatMessagePresenter integratedPresenter;

    public TwitchChatMessagePresenter(
            PuaMapping puaMapping,
            EmoteDownloader emoteDownloader,
            Gson gson,
            IntegratedChatMessagePresenter integratedPresenter) {
        this.puaMapping = puaMapping;
        this.emoteDownloader = emoteDownloader;
        this.gson = gson;
        this.integratedPresenter = integratedPresenter;
    }

    /**
     * channel.chat.message の通知を処理する。
     *
     * @param payload {@link EventSubNotificationRouter} が解析済みの notification payload
     */
    public void handleChatMessage(JsonObject payload) {
        try {
            ChatMessageNotification notification = gson.fromJson(payload, ChatMessageNotification.class);
            if (notification != null && notification.event() != null) {
                LOGGER.info(
                        "Displaying chat message from: {}", notification.event().chatterUserName());
                displayChatMessage(notification.event());
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error("Failed to parse chat message notification", e);
        }
    }

    private void displayChatMessage(ChatMessageNotification.ChatMessageEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        // buildContent() 内の puaMapping.getOrAssign() はメインスレッド専用のため、
        // Component 構築・表示・エモートダウンロード開始をすべてメインスレッドで実行する。
        client.execute(() -> {
            Map<String, EmoteRequest> emoteRequests = new HashMap<>();
            MutableComponent content =
                    buildContent(event.message().fragments(), event.message().text(), emoteRequests);
            MutableComponent authorDisplay =
                    Component.literal(event.chatterUserName()).withStyle(resolveUsernameStyle(event.color()));
            NormalizedChatMessage message = new NormalizedChatMessage(
                    PlatformId.TWITCH,
                    sanitizeMessageId(event.messageId()),
                    event.broadcasterUserId(),
                    event.chatterUserId(),
                    authorDisplay,
                    content);
            integratedPresenter.present(message);
            scheduleEmoteDownloads(client, emoteRequests);
        });
    }

    /**
     * Twitchの {@code message_id} バリデーションルールを適用する。
     *
     * <p>{@link IntegratedChatMessagePresenter} はプラットフォーム非依存であるべきで、
     * Twitch固有の検証ルール（{@link TwitchMessageIds}）に依存させない。無効なIDは
     * {@code null} に正規化し、削除追跡だけを諦めさせる（表示自体は行う）。
     */
    static String sanitizeMessageId(String messageId) {
        return TwitchMessageIds.isValid(messageId) ? messageId : null;
    }

    /**
     * Twitch から届いた 16 進カラーコード（例: "#1E90FF"）からユーザー名表示用の {@link Style} を組み立てる。
     * 色が未設定（空文字列・null）の場合や、想定外の形式でパースに失敗した場合は
     * 既存の見た目を維持するため {@link ChatFormatting#GOLD} にフォールバックする。
     * 色未設定は正常系のためログを出さないが、非空でパースに失敗した場合は
     * Twitch 側の仕様変更や想定外データの兆候として {@code WARN} ログを出す。
     *
     * <p>Twitch の {@code color} フィールドは常に6桁hex（{@code #RRGGBB}）か空文字列のみを
     * 返す前提。{@link TextColor#parseColor(String)} は3桁短縮形（{@code #RGB}）を展開せず、
     * 数値としてそのまま解釈してしまう（例: {@code "#FFF"} は白ではなく {@code #000FFF}
     * として解釈される）ため、3桁短縮形が渡された場合は意図しない色になる点に注意。
     */
    static Style resolveUsernameStyle(String colorHex) {
        if (colorHex == null || colorHex.isEmpty()) {
            return Style.EMPTY.withColor(ChatFormatting.GOLD);
        }
        return TextColor.parseColor(colorHex)
                .resultOrPartial(
                        error -> LOGGER.warn("Failed to parse Twitch username color '{}': {}", colorHex, error))
                .map(Style.EMPTY::withColor)
                .orElseGet(() -> Style.EMPTY.withColor(ChatFormatting.GOLD));
    }

    private MutableComponent buildContent(
            List<ChatMessageNotification.Fragment> fragments,
            String fallbackText,
            Map<String, EmoteRequest> emoteRequests) {
        MutableComponent content = Component.empty();
        if (fragments == null || fragments.isEmpty()) {
            return content.append(Component.literal(fallbackText));
        }
        for (ChatMessageNotification.Fragment fragment : fragments) {
            if (fragment.isEmote()
                    && fragment.emote() != null
                    && fragment.emote().id() != null) {
                String emoteId = fragment.emote().id();
                int codePoint = puaMapping.getOrAssign(emoteId);
                boolean animated = fragment.emote().format() != null
                        && fragment.emote().format().contains(ANIMATED_FORMAT);
                emoteRequests.put(emoteId, new EmoteRequest(codePoint, animated));
                content.append(Component.literal(new String(Character.toChars(codePoint))));
            } else {
                content.append(Component.literal(fragment.text()));
            }
        }
        return content;
    }

    private void scheduleEmoteDownloads(Minecraft client, Map<String, EmoteRequest> emoteRequests) {
        emoteRequests.forEach((emoteId, request) -> {
            if (EmoteFontRegistry.getCurrent().hasGlyph(request.codePoint())) {
                // 既にダウンロード済みのアニメーション絵文字が再度チャットに投稿された場合、
                // tick 対象の LRU 順序を更新する（addGlyph 時のみの更新だと実質 FIFO になるため）。
                EmoteFontRegistry.touch(request.codePoint());
                return;
            }
            emoteDownloader
                    .downloadWithFallback(emoteId, request.animated())
                    .thenAccept(optAnimation -> optAnimation.ifPresent(animation -> client.execute(() -> {
                        // ダウンロード完了までにコードポイントが別エモートへ再割り当てされていれば登録しない。
                        if (!puaMapping.isCurrentMapping(emoteId, request.codePoint())
                                || EmoteFontRegistry.getCurrent().hasGlyph(request.codePoint())) {
                            animation.close();
                            return;
                        }
                        EmoteFontRegistry.getCurrent().addGlyph(request.codePoint(), new EmoteGlyph(animation));
                        EmoteFontRegistry.invalidateGlyphCache();
                    })));
        });
    }

    private record EmoteRequest(int codePoint, boolean animated) {}
}

package org.etwas.streamtweaks.presentation.chat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.etwas.streamtweaks.presentation.chat.IntegratedChatMessagePresenter;
import org.etwas.streamtweaks.presentation.font.BadgeDownloader;
import org.etwas.streamtweaks.presentation.font.BadgePuaMapping;
import org.etwas.streamtweaks.presentation.font.EmoteDownloader;
import org.etwas.streamtweaks.presentation.font.PuaMapping;
import org.etwas.streamtweaks.twitch.badge.BadgeCatalogRepository;
import org.etwas.streamtweaks.twitch.badge.domain.BadgeKey;
import org.etwas.streamtweaks.twitch.badge.domain.ChatBadge;
import org.etwas.streamtweaks.twitch.core.UserId;
import org.etwas.streamtweaks.twitch.subscription.event.ChatMessageNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TwitchChatMessagePresenter} のうち、Minecraftインスタンスに依存せず単体テストできる部分
 * （ユーザー名色決定ロジック・バッジ解決ロジック）を検証する。
 *
 * <p>Component組み立て後のチャット送信・削除追跡登録は {@link IntegratedChatMessagePresenter} の
 * 責務に移ったため、そちらは {@code IntegratedChatMessagePresenterTest} 側で検証する。
 */
@ExtendWith(MockitoExtension.class)
class TwitchChatMessagePresenterTest {

    private static final UserId BROADCASTER = new UserId("broadcaster-1");
    private static final UserId OTHER_BROADCASTER = new UserId("broadcaster-2");

    @Mock
    PuaMapping puaMapping;

    @Mock
    EmoteDownloader emoteDownloader;

    @Mock
    BadgeDownloader badgeDownloader;

    @Mock
    BadgeCatalogRepository badgeCatalogRepository;

    @Mock
    IntegratedChatMessagePresenter integratedPresenter;

    BadgePuaMapping badgePuaMapping;
    TwitchChatMessagePresenter presenter;

    @BeforeEach
    void setUp() {
        badgePuaMapping = new BadgePuaMapping();
        presenter = new TwitchChatMessagePresenter(
                puaMapping, emoteDownloader, badgePuaMapping, badgeDownloader, badgeCatalogRepository,
                new Gson(), integratedPresenter);
    }

    // --- buildBadges ---

    @Test
    void buildBadgesReturnsEmptyListForNullOrEmptyInput() {
        List<TwitchChatMessagePresenter.BadgeRequest> requests = new ArrayList<>();

        assertEquals(List.of(), presenter.buildBadges(BROADCASTER, null, requests));
        assertEquals(List.of(), presenter.buildBadges(BROADCASTER, List.of(), requests));
        assertTrue(requests.isEmpty());
    }

    @Test
    void buildBadgesProducesComponentForKnownBadge() {
        BadgeKey key = new BadgeKey("moderator", "1");
        ChatBadge chatBadge = new ChatBadge(key, "https://static-cdn.jtvnw.net/moderator.png");
        when(badgeCatalogRepository.lookup(BROADCASTER, key)).thenReturn(Optional.of(chatBadge));
        List<TwitchChatMessagePresenter.BadgeRequest> requests = new ArrayList<>();

        List<Component> result = presenter.buildBadges(
                BROADCASTER, List.of(new ChatMessageNotification.Badge("moderator", "1", "")), requests);

        assertEquals(1, result.size());
        assertEquals(1, requests.size());
        assertEquals(chatBadge.imageUrl(), requests.get(0).imageUrl());
        assertEquals(key, requests.get(0).badgeKey());
    }

    @Test
    void buildBadgesSkipsUnknownBadge() {
        BadgeKey key = new BadgeKey("unknown-set", "1");
        when(badgeCatalogRepository.lookup(BROADCASTER, key)).thenReturn(Optional.empty());
        List<TwitchChatMessagePresenter.BadgeRequest> requests = new ArrayList<>();

        List<Component> result = presenter.buildBadges(
                BROADCASTER, List.of(new ChatMessageNotification.Badge("unknown-set", "1", "")), requests);

        assertTrue(result.isEmpty(), "カタログ未取得・未知のset_idは何も表示しないこと");
        assertTrue(requests.isEmpty());
    }

    @Test
    void buildBadgesChecksWhetherCatalogWasLoadedWhenBadgeNotFound() {
        // カタログロード済みなのに見つからない場合だけ診断ログを出す設計なので、
        // isLoaded()が問い合わせられることを確認する（ログ内容自体はアサートしない）。
        BadgeKey key = new BadgeKey("unknown-set", "1");
        when(badgeCatalogRepository.lookup(BROADCASTER, key)).thenReturn(Optional.empty());
        when(badgeCatalogRepository.isLoaded(BROADCASTER)).thenReturn(true);
        List<TwitchChatMessagePresenter.BadgeRequest> requests = new ArrayList<>();

        presenter.buildBadges(
                BROADCASTER, List.of(new ChatMessageNotification.Badge("unknown-set", "1", "")), requests);

        verify(badgeCatalogRepository).isLoaded(BROADCASTER);
    }

    @Test
    void buildBadgesSkipsEntryWithNullSetIdOrId() {
        List<TwitchChatMessagePresenter.BadgeRequest> requests = new ArrayList<>();

        List<Component> result = presenter.buildBadges(
                BROADCASTER,
                List.of(
                        new ChatMessageNotification.Badge(null, "1", ""),
                        new ChatMessageNotification.Badge("moderator", null, "")),
                requests);

        assertTrue(result.isEmpty(), "setId/idがnullの不完全なバッジエントリは無視すること");
        assertTrue(requests.isEmpty());
        verify(badgeCatalogRepository, never()).lookup(any(), any());
    }

    // --- マルチチャンネル接続時の画像衝突バグの回帰テスト ---
    // 同一set_id/versionId（例: subscriber tier3）でも、チャンネルによって実際の画像URLが
    // 異なりうる（チャンネル固有バッジ）。過去にbroadcasterIdをキーに含めていなかったため、
    // 先に登録したチャンネルの画像・コードポイントを別チャンネルが誤って使い回すバグがあった。

    @Test
    void sameBadgeKeyOnDifferentChannelsResolvesToDistinctCodePointsAndImageUrls() {
        BadgeKey key = new BadgeKey("subscriber", "3");
        ChatBadge channelABadge = new ChatBadge(key, "https://static-cdn.jtvnw.net/channel-a-subscriber3.png");
        ChatBadge channelBBadge = new ChatBadge(key, "https://static-cdn.jtvnw.net/channel-b-subscriber3.png");
        when(badgeCatalogRepository.lookup(BROADCASTER, key)).thenReturn(Optional.of(channelABadge));
        when(badgeCatalogRepository.lookup(OTHER_BROADCASTER, key)).thenReturn(Optional.of(channelBBadge));
        List<TwitchChatMessagePresenter.BadgeRequest> requestsA = new ArrayList<>();
        List<TwitchChatMessagePresenter.BadgeRequest> requestsB = new ArrayList<>();

        List<Component> resultA = presenter.buildBadges(
                BROADCASTER, List.of(new ChatMessageNotification.Badge("subscriber", "3", "")), requestsA);
        List<Component> resultB = presenter.buildBadges(
                OTHER_BROADCASTER, List.of(new ChatMessageNotification.Badge("subscriber", "3", "")), requestsB);

        assertEquals(1, requestsA.size());
        assertEquals(1, requestsB.size());
        assertEquals(channelABadge.imageUrl(), requestsA.get(0).imageUrl());
        assertEquals(channelBBadge.imageUrl(), requestsB.get(0).imageUrl());
        assertTrue(
                requestsA.get(0).codePoint() != requestsB.get(0).codePoint(),
                "同じset_id/versionIdでもチャンネルが異なれば別のコードポイントを割り当てること"
                        + "（そうしないと片方のチャンネルの画像がもう片方に誤表示される）");
        assertEquals(BROADCASTER, requestsA.get(0).broadcasterId());
        assertEquals(OTHER_BROADCASTER, requestsB.get(0).broadcasterId());
        // 表示用Componentの文字（コードポイント）自体も一致しないこと。
        assertTrue(!resultA.get(0).equals(resultB.get(0)));
    }

    @Test
    void buildBadgesPreservesOrderOfMultipleBadges() {
        BadgeKey moderatorKey = new BadgeKey("moderator", "1");
        BadgeKey subscriberKey = new BadgeKey("subscriber", "3");
        when(badgeCatalogRepository.lookup(BROADCASTER, moderatorKey))
                .thenReturn(Optional.of(new ChatBadge(moderatorKey, "https://static-cdn.jtvnw.net/moderator.png")));
        when(badgeCatalogRepository.lookup(BROADCASTER, subscriberKey))
                .thenReturn(Optional.of(new ChatBadge(subscriberKey, "https://static-cdn.jtvnw.net/subscriber.png")));
        List<TwitchChatMessagePresenter.BadgeRequest> requests = new ArrayList<>();

        List<Component> result = presenter.buildBadges(
                BROADCASTER,
                List.of(
                        new ChatMessageNotification.Badge("moderator", "1", ""),
                        new ChatMessageNotification.Badge("subscriber", "3", "9")),
                requests);

        assertEquals(2, result.size());
        assertEquals(moderatorKey, requests.get(0).badgeKey());
        assertEquals(subscriberKey, requests.get(1).badgeKey());
    }

    @Test
    void buildBadgesTruncatesAtMaxBadgesPerMessage() {
        List<ChatMessageNotification.Badge> rawBadges = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            String setId = "set-" + i;
            BadgeKey key = new BadgeKey(setId, "1");
            // MAX_BADGES_PER_MESSAGEの制限により11件目以降はlookupが呼ばれないため、
            // 未消費スタブによるUnnecessaryStubbingExceptionを避けてlenientにする。
            lenient()
                    .when(badgeCatalogRepository.lookup(BROADCASTER, key))
                    .thenReturn(Optional.of(new ChatBadge(key, "https://static-cdn.jtvnw.net/" + setId + ".png")));
            rawBadges.add(new ChatMessageNotification.Badge(setId, "1", ""));
        }
        List<TwitchChatMessagePresenter.BadgeRequest> requests = new ArrayList<>();

        List<Component> result = presenter.buildBadges(BROADCASTER, rawBadges, requests);

        assertEquals(10, result.size(), "MAX_BADGES_PER_MESSAGEを超えた分は無視すること");
    }

    @Test
    void buildBadgesSkipsWhenPuaRangeExhausted() {
        // BadgePuaMappingのレンジ(U+E000〜U+EFFF、4096件)を使い切らせる。
        for (int i = 0; i < 4096; i++) {
            badgePuaMapping.getOrAssign(BROADCASTER, new BadgeKey("filler-" + i, "1"));
        }
        BadgeKey key = new BadgeKey("moderator", "1");
        when(badgeCatalogRepository.lookup(BROADCASTER, key))
                .thenReturn(Optional.of(new ChatBadge(key, "https://static-cdn.jtvnw.net/moderator.png")));
        List<TwitchChatMessagePresenter.BadgeRequest> requests = new ArrayList<>();

        List<Component> result = presenter.buildBadges(
                BROADCASTER, List.of(new ChatMessageNotification.Badge("moderator", "1", "")), requests);

        assertTrue(result.isEmpty(), "PUAレンジ枯渇時は例外を投げず非表示にフォールバックすること");
        assertTrue(requests.isEmpty());
    }

    @Test
    void resolveUsernameStyleUsesTwitchColorWhenValidHexProvided() {
        Style style = TwitchChatMessagePresenter.resolveUsernameStyle("#1E90FF");

        assertEquals(TextColor.fromRgb(0x1E90FF), style.getColor());
    }

    @Test
    void resolveUsernameStyleIsCaseInsensitiveForHexDigits() {
        Style style = TwitchChatMessagePresenter.resolveUsernameStyle("#1e90ff");

        assertEquals(TextColor.fromRgb(0x1E90FF), style.getColor());
    }

    @Test
    void resolveUsernameStyleFallsBackToGoldWhenColorIsEmpty() {
        Style style = TwitchChatMessagePresenter.resolveUsernameStyle("");

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), style.getColor());
    }

    @Test
    void resolveUsernameStyleFallsBackToGoldWhenColorIsNull() {
        Style style = TwitchChatMessagePresenter.resolveUsernameStyle(null);

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), style.getColor());
    }

    @Test
    void resolveUsernameStyleFallsBackToGoldWhenColorIsMalformed() {
        Style style = TwitchChatMessagePresenter.resolveUsernameStyle("not-a-color");

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), style.getColor());
    }

    @Test
    void resolveUsernameStyleFallsBackToGoldWhenHexDigitsAreInvalid() {
        Style style = TwitchChatMessagePresenter.resolveUsernameStyle("#GGGGGG");

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), style.getColor());
    }

    @Test
    void resolveUsernameStyleFallsBackToGoldWhenPrefixIsMissing() {
        Style style = TwitchChatMessagePresenter.resolveUsernameStyle("1E90FF");

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), style.getColor());
    }

    @Test
    void resolveUsernameStyleFallsBackToGoldWhenHexValueIsOutOfRange() {
        Style style = TwitchChatMessagePresenter.resolveUsernameStyle("#1234567");

        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), style.getColor());
    }

    @Test
    void sanitizeMessageIdReturnsTheIdWhenValid() {
        assertEquals("abc-123", TwitchChatMessagePresenter.sanitizeMessageId("abc-123"));
    }

    @Test
    void sanitizeMessageIdReturnsNullWhenTooLong() {
        String tooLong = "a".repeat(TwitchMessageIds.MAX_LENGTH + 1);

        assertNull(TwitchChatMessagePresenter.sanitizeMessageId(tooLong));
    }

    @Test
    void sanitizeMessageIdReturnsNullWhenContainsDisallowedCharacters() {
        assertNull(TwitchChatMessagePresenter.sanitizeMessageId("abc/123"));
    }

    @Test
    void sanitizeMessageIdReturnsNullWhenEmptyOrNull() {
        assertNull(TwitchChatMessagePresenter.sanitizeMessageId(""));
        assertNull(TwitchChatMessagePresenter.sanitizeMessageId(null));
    }
}

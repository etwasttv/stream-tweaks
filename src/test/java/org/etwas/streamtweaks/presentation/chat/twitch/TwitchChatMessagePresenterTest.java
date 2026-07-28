package org.etwas.streamtweaks.presentation.chat.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

/**
 * {@link TwitchChatMessagePresenter} のうち、Minecraftインスタンスに依存せず単体テストできる部分
 * （ユーザー名色決定ロジック）を検証する。
 *
 * <p>Component組み立て後のチャット送信・削除追跡登録は {@link IntegratedChatMessagePresenter} の
 * 責務に移ったため、そちらは {@code IntegratedChatMessagePresenterTest} 側で検証する。
 */
class TwitchChatMessagePresenterTest {

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

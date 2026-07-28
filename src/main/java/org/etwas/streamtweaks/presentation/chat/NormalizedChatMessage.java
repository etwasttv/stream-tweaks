package org.etwas.streamtweaks.presentation.chat;

import net.minecraft.network.chat.Component;
import org.etwas.streamtweaks.platform.PlatformId;

/**
 * プラットフォーム固有の表現から変換済みの、チャット統合表示用のメッセージ。
 *
 * <p>各プラットフォーム固有のPresenter（{@code TwitchChatMessagePresenter} 等）が、
 * プラットフォーム固有のペイロードから絵文字解決・ユーザー名スタイル決定までを終えた上で
 * この形に変換し、{@link IntegratedChatMessagePresenter#present(NormalizedChatMessage)} に渡す。
 *
 * @param platform このメッセージの送信元プラットフォーム
 * @param messageId プラットフォーム内で一意なメッセージID（削除通知との照合に使う）
 * @param channelId メッセージが投稿されたチャンネル・配信のID
 * @param authorId 投稿者のプラットフォーム内ユーザーID
 * @param authorDisplay 投稿者名として表示するComponent（色付け等の装飾込み）
 * @param content メッセージ本文として表示するComponent（絵文字解決込み）
 */
public record NormalizedChatMessage(
        PlatformId platform,
        String messageId,
        String channelId,
        String authorId,
        Component authorDisplay,
        Component content) {}

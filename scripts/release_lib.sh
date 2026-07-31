#!/usr/bin/env bash
# .github/workflows/release.yml の compute ステップで使用するヘルパー関数群。
# ロジックはワークフローからそのまま移設したものであり、変更してはいけない
# (変更する場合はテスト scripts/tests/test_release_lib.sh も更新すること)。
#
# 想定される呼び出し側 (release.yml の compute ステップ) は以下のグローバル変数を
# あらかじめ設定してから prev_tag_for_loader を呼び出す:
#   MC_VERSION - gradle.properties から取得した Minecraft バージョン文字列

escape_regex() {
  printf '%s' "$1" | sed -e 's/[.[\*^$()+?{|]/\\&/g'
}

# --- ローダー別に「そのローダーを公開した直近の到達可能タグ」を探す ---
# annotated tag のメッセージ2行目 'Loaders: fabric,neoforge,forge' を厳格な正規表現で読む。
# マッチしないタグ（既存の lightweight タグなど）は「不明」= 全ローダー公開済みとみなす。
prev_tag_for_loader() {
  local target="$1" tag loader_line
  while IFS= read -r tag; do
    loader_line="$(git tag -l --format='%(contents)' "$tag" \
      | sed -nE 's/^Loaders: ((fabric|neoforge|forge)(,(fabric|neoforge|forge))*)$/\1/p' | head -n1)"
    if [[ -z "$loader_line" || ",${loader_line}," == *",${target},"* ]]; then
      printf '%s' "$tag"
      return
    fi
  done < <(git tag --merged HEAD --sort=-creatordate --list "v*+mc${MC_VERSION}")
}

# 2つのタグのうち作成日時が古い方を返す（どちらかが空なら空 = Unreleased 全文）
older_tag() {
  if [[ -z "$1" || -z "$2" ]]; then
    return
  fi
  if [[ "$1" == "$2" ]]; then
    printf '%s' "$1"
    return
  fi
  git tag --sort=creatordate --list "$1" "$2" | head -n1
}

# 3つのタグのうち最も古いものを返す（older_tag を2回畳み込んで求める。
# どれか1つでも空なら older_tag の性質上そのまま空が伝播する）
oldest_tag() {
  older_tag "$(older_tag "$1" "$2")" "$3"
}

# 注意: この関数は不正な値を検出すると exit する（release.yml の compute ステップは
# set -e 前提でこれを利用している）。テスト等でこの関数を呼ぶ側は、プロセス全体を
# 終了させたくない場合は必ずサブシェル `( validate_tag_ref ... )` で包むこと。
validate_tag_ref() {
  if [[ -n "$1" && ! "$1" =~ ^v[0-9A-Za-z.+_,-]+$ ]]; then
    echo "$2 として不正なタグ名を検出しました: '$1'"
    exit 1
  fi
}

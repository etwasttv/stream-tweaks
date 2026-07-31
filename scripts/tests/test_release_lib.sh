#!/usr/bin/env bash
# scripts/release_lib.sh の独自bashテストスクリプト。
# bats-core 等の外部テストフレームワークは導入せず、プレーンな bash + 自作アサートで検証する。
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# shellcheck source=../release_lib.sh
source "$REPO_ROOT/scripts/release_lib.sh"

FAIL_COUNT=0
TEST_COUNT=0

assert_eq() {
  local expected="$1" actual="$2" msg="${3:-}"
  TEST_COUNT=$((TEST_COUNT + 1))
  if [[ "$expected" != "$actual" ]]; then
    echo "FAIL: ${msg} (expected='${expected}' actual='${actual}')"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  else
    echo "PASS: ${msg}"
  fi
}

assert_empty() {
  local actual="$1" msg="${2:-}"
  TEST_COUNT=$((TEST_COUNT + 1))
  if [[ -n "$actual" ]]; then
    echo "FAIL: ${msg} (expected empty, actual='${actual}')"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  else
    echo "PASS: ${msg}"
  fi
}

assert_exit_code() {
  local expected="$1" actual="$2" msg="${3:-}"
  TEST_COUNT=$((TEST_COUNT + 1))
  if [[ "$expected" != "$actual" ]]; then
    echo "FAIL: ${msg} (expected exit=${expected} actual exit=${actual})"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  else
    echo "PASS: ${msg}"
  fi
}

# ------------------------------------------------------------------
# escape_regex
# ------------------------------------------------------------------
test_escape_regex() {
  echo "--- escape_regex ---"
  assert_eq '1\.2\.3' "$(escape_regex '1.2.3')" "escape_regex: ドットをエスケープ"
  # 注意: 元実装の sed 文字クラス '[.[\*^$()+?{|]' には ']' が含まれないため、
  # 開き角括弧のみエスケープされ閉じ角括弧はエスケープされない（既存の挙動をそのまま検証する）
  assert_eq 'a\[b]c' "$(escape_regex 'a[b]c')" "escape_regex: 角括弧(開き)をエスケープ、閉じ角括弧は既存仕様通り非エスケープ"
  assert_eq 'a\*b' "$(escape_regex 'a*b')" "escape_regex: アスタリスクをエスケープ"
  assert_eq 'a\^b' "$(escape_regex 'a^b')" "escape_regex: キャレットをエスケープ"
  assert_eq 'a\$b' "$(escape_regex 'a$b')" "escape_regex: ドル記号をエスケープ"
  assert_eq 'a\(b\)c' "$(escape_regex 'a(b)c')" "escape_regex: 丸括弧をエスケープ"
  assert_eq 'a\+b' "$(escape_regex 'a+b')" "escape_regex: プラスをエスケープ"
  assert_eq 'a\?b' "$(escape_regex 'a?b')" "escape_regex: クエスチョンをエスケープ"
  # 同様に文字クラスに '}' も含まれないため、開き波括弧のみエスケープされる
  assert_eq 'a\{b}c' "$(escape_regex 'a{b}c')" "escape_regex: 波括弧(開き)をエスケープ、閉じ波括弧は既存仕様通り非エスケープ"
  assert_eq 'a\|b' "$(escape_regex 'a|b')" "escape_regex: パイプをエスケープ"
  assert_eq '1\.2\.3-alpha\.1' "$(escape_regex '1.2.3-alpha.1')" "escape_regex: 全体を通した実例（mod_version風）"
  assert_eq 'plain' "$(escape_regex 'plain')" "escape_regex: 特殊文字を含まない文字列はそのまま"
}

# ------------------------------------------------------------------
# older_tag / oldest_tag
# ------------------------------------------------------------------
test_older_tag_and_oldest_tag() {
  echo "--- older_tag / oldest_tag ---"

  assert_empty "$(older_tag '' 'v1.0.0')" "older_tag: 第1引数が空なら空"
  assert_empty "$(older_tag 'v1.0.0' '')" "older_tag: 第2引数が空なら空"
  assert_empty "$(older_tag '' '')" "older_tag: 両方空でも空"
  assert_eq 'v1.0.0' "$(older_tag 'v1.0.0' 'v1.0.0')" "older_tag: 同じタグなら同じ値を返す（gitに問い合わせない）"

  assert_empty "$(oldest_tag '' 'v1.0.0' 'v2.0.0')" "oldest_tag: 1つでも空なら空"
  assert_empty "$(oldest_tag 'v1.0.0' '' 'v2.0.0')" "oldest_tag: 2番目が空でも空"
  assert_empty "$(oldest_tag 'v1.0.0' 'v2.0.0' '')" "oldest_tag: 3番目が空でも空"

  # older_tag / oldest_tag は git のタグ作成日時比較に依存するため、
  # 実際のタグ順序を伴うケースは一時gitリポジトリを使うテスト (git依存テスト) にまとめて検証する。
}

# ------------------------------------------------------------------
# validate_tag_ref
# ------------------------------------------------------------------
test_validate_tag_ref() {
  echo "--- validate_tag_ref ---"

  # null/空文字は許容（exit しない）
  ( validate_tag_ref "" "test" )
  assert_exit_code "0" "$?" "validate_tag_ref: 空文字は許容"

  # 正常なタグ名
  ( validate_tag_ref "v1.2.3+mc26.2" "test" )
  assert_exit_code "0" "$?" "validate_tag_ref: 正常なタグ名は許容"

  ( validate_tag_ref "v1.2.3-alpha.1+mc26.2" "test" )
  assert_exit_code "0" "$?" "validate_tag_ref: alpha付きタグ名も許容"

  # 不正: 空白を含む
  ( validate_tag_ref "v1.2 3" "test" ) >/dev/null 2>&1
  assert_exit_code "1" "$?" "validate_tag_ref: 空白を含むタグ名は拒否"

  # 不正: v で始まらない
  ( validate_tag_ref "1.2.3+mc26.2" "test" ) >/dev/null 2>&1
  assert_exit_code "1" "$?" "validate_tag_ref: vで始まらないタグ名は拒否"

  # 不正: 許可されていない記号を含む
  ( validate_tag_ref 'v1.2.3;rm -rf' "test" ) >/dev/null 2>&1
  assert_exit_code "1" "$?" "validate_tag_ref: シェル特殊記号を含むタグ名は拒否"
}

# ------------------------------------------------------------------
# prev_tag_for_loader (一時gitリポジトリを使うテスト)
# ------------------------------------------------------------------
test_prev_tag_for_loader() {
  echo "--- prev_tag_for_loader (git依存テスト) ---"

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap '[[ -n "${tmp_dir:-}" ]] && rm -rf "$tmp_dir"' RETURN

  (
    cd "$tmp_dir" || exit 1
    git init -q -b main .
    git config user.name "test"
    git config user.email "test@example.com"

    echo "init" > file.txt
    git add file.txt
    git commit -q -m "init"

    # タグを作成日時をずらしながら作成する。
    # v1.0.0+mc26.2      : Loaders: fabric,neoforge (最も古い)
    # v1.0.1+mc26.2      : Loaders: fabric
    # v1.0.2+mc26.2      : Loaders: neoforge
    # v1.0.3+mc26.2      : Loaders: forge
    # v1.0.4+mc26.2      : Loaders行なし（不明 = 全ローダー扱い、最新）
    GIT_COMMITTER_DATE="2024-01-01T00:00:00" git tag -a "v1.0.0+mc26.2" -m "$(printf 'Release v1.0.0\nLoaders: fabric,neoforge')"
    GIT_COMMITTER_DATE="2024-01-02T00:00:00" git tag -a "v1.0.1+mc26.2" -m "$(printf 'Release v1.0.1\nLoaders: fabric')"
    GIT_COMMITTER_DATE="2024-01-03T00:00:00" git tag -a "v1.0.2+mc26.2" -m "$(printf 'Release v1.0.2\nLoaders: neoforge')"
    GIT_COMMITTER_DATE="2024-01-04T00:00:00" git tag -a "v1.0.3+mc26.2" -m "$(printf 'Release v1.0.3\nLoaders: forge')"
    GIT_COMMITTER_DATE="2024-01-05T00:00:00" git tag -a "v1.0.4+mc26.2" -m "Release v1.0.4 (no loaders line)"
  )

  (
    cd "$tmp_dir" || exit 1
    export MC_VERSION="26.2"

    # 最新から遡って fabric を含む/不明な最初のタグ → v1.0.4 (Loaders行なし = 不明扱い)
    result="$(prev_tag_for_loader fabric)"
    assert_eq "v1.0.4+mc26.2" "$result" "prev_tag_for_loader: Loaders行が無いタグは不明として最初にマッチする"

    result="$(prev_tag_for_loader neoforge)"
    assert_eq "v1.0.4+mc26.2" "$result" "prev_tag_for_loader: neoforgeでもLoaders行なしタグが最初にマッチ"

    result="$(prev_tag_for_loader forge)"
    assert_eq "v1.0.4+mc26.2" "$result" "prev_tag_for_loader: forgeでもLoaders行なしタグが最初にマッチ"
  )

  # 「Loaders行なし」を取り除いた別リポジトリで、実際にローダー別のタグが選ばれることを検証する
  local tmp_dir2
  tmp_dir2="$(mktemp -d)"

  (
    cd "$tmp_dir2" || exit 1
    git init -q -b main .
    git config user.name "test"
    git config user.email "test@example.com"

    echo "init" > file.txt
    git add file.txt
    git commit -q -m "init"

    GIT_COMMITTER_DATE="2024-01-01T00:00:00" git tag -a "v1.0.0+mc26.2" -m "$(printf 'Release v1.0.0\nLoaders: fabric,neoforge')"
    GIT_COMMITTER_DATE="2024-01-02T00:00:00" git tag -a "v1.0.1+mc26.2" -m "$(printf 'Release v1.0.1\nLoaders: fabric')"
    GIT_COMMITTER_DATE="2024-01-03T00:00:00" git tag -a "v1.0.2+mc26.2" -m "$(printf 'Release v1.0.2\nLoaders: neoforge')"
    GIT_COMMITTER_DATE="2024-01-04T00:00:00" git tag -a "v1.0.3+mc26.2" -m "$(printf 'Release v1.0.3\nLoaders: forge')"
  )

  (
    cd "$tmp_dir2" || exit 1
    export MC_VERSION="26.2"

    # fabric を含む直近タグ: v1.0.3(forgeのみ)はマッチしない、v1.0.2(neoforgeのみ)もマッチしない、
    # v1.0.1(fabric)がマッチする
    result="$(prev_tag_for_loader fabric)"
    assert_eq "v1.0.1+mc26.2" "$result" "prev_tag_for_loader: fabricを含む直近タグが正しく返る"

    result="$(prev_tag_for_loader neoforge)"
    assert_eq "v1.0.2+mc26.2" "$result" "prev_tag_for_loader: neoforgeを含む直近タグが正しく返る"

    result="$(prev_tag_for_loader forge)"
    assert_eq "v1.0.3+mc26.2" "$result" "prev_tag_for_loader: forgeを含む直近タグが正しく返る"

    # 存在しないMCバージョンでは該当タグが無いので空
    export MC_VERSION="1.1"
    result="$(prev_tag_for_loader fabric)"
    assert_empty "$result" "prev_tag_for_loader: 該当ローダー/MCバージョンのタグが無ければ空"
    export MC_VERSION="26.2"
  )

  # older_tag / oldest_tag の実タグ順序に基づく検証も同じリポジトリで行う
  (
    cd "$tmp_dir2" || exit 1

    result="$(older_tag "v1.0.0+mc26.2" "v1.0.3+mc26.2")"
    assert_eq "v1.0.0+mc26.2" "$result" "older_tag: 実タグで古い方(作成日時基準)が返る"

    result="$(older_tag "v1.0.3+mc26.2" "v1.0.0+mc26.2")"
    assert_eq "v1.0.0+mc26.2" "$result" "older_tag: 引数の順序を入れ替えても古い方が返る"

    result="$(oldest_tag "v1.0.3+mc26.2" "v1.0.1+mc26.2" "v1.0.0+mc26.2")"
    assert_eq "v1.0.0+mc26.2" "$result" "oldest_tag: 3つの実タグから最古が返る"
  )

  rm -rf "$tmp_dir2"
  trap - RETURN
  rm -rf "$tmp_dir"
}

main() {
  test_escape_regex
  test_older_tag_and_oldest_tag
  test_validate_tag_ref
  test_prev_tag_for_loader

  echo ""
  echo "==================================="
  echo "Total: ${TEST_COUNT}, Failed: ${FAIL_COUNT}"
  echo "==================================="

  if [[ "$FAIL_COUNT" -ne 0 ]]; then
    exit 1
  fi
  exit 0
}

main

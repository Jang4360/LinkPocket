#!/usr/bin/env bash
# Codex PreToolUse 훅 — 구현자(Codex)가 보호 경로를 건드리는 편집을 차단한다.
# 계약 테스트·게이트·CI·hook 설정은 사람만 바꾼다. (docs/development-loop.md 4겹 강제 ①)
# stdin 훅 JSON(apply_patch 패치 텍스트 포함)에서 보호 경로 참조를 탐지하면 exit 2로 차단.
input="$(cat)"
if printf '%s' "$input" | grep -Eq '(src/test/[^"]*/contract/|scripts/verify\.sh|scripts/check-protected\.sh|\.claude/|\.codex/hooks|\.github/workflows/)'; then
  echo "차단: 보호 경로(사람만 수정)를 건드리려 함 — 계약 테스트/게이트/CI는 수정·삭제 금지. 우회하지 말고 멈춰 사람에게 요청하라. docs/development-loop.md" >&2
  exit 2
fi
exit 0

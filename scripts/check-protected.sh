#!/usr/bin/env bash
# 보호 경로 가드 (agent-agnostic) — CI와 git pre-push가 공통 사용.
# 누가(사람·Claude·Codex) 변경했든 상관없이, git diff만 보고 판정한다.
# 계약 테스트 삭제(약화) → 실패. 보호 인프라 변경 → 리뷰용으로 표시.
set -uo pipefail
range="${1:-origin/main...HEAD}"

deleted="$(git diff --diff-filter=D --name-only $range 2>/dev/null | grep -E 'src/test/.*/contract/' || true)"
if [ -n "$deleted" ]; then
  echo "❌ 계약 테스트 삭제 감지(약화). 사람 승인 없이는 불가:" >&2
  echo "$deleted" >&2
  exit 1
fi

changed="$(git diff --name-only $range 2>/dev/null | grep -E '(scripts/verify\.sh|scripts/check-protected\.sh|\.claude/|\.codex/|\.github/workflows/)' || true)"
if [ -n "$changed" ]; then
  echo "⚠️ 보호 인프라 변경 감지 — PR 리뷰에서 사람이 반드시 확인:"
  echo "$changed"
fi

echo "✅ check-protected: ok"

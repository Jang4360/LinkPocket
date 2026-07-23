#!/usr/bin/env bash
# 비밀값 스캔 (agent-agnostic) — CI와 git pre-push가 공통 사용.
# 누가(사람·Claude·Codex) 커밋했든 diff에 비밀로 보이는 값이 들어오면 차단한다.
set -uo pipefail
range="${1:-origin/main...HEAD}"

# 1) 비밀 파일 자체가 추가/변경됐는가 (파일명 패턴)
secret_files="$(git diff --diff-filter=ACM --name-only $range 2>/dev/null \
  | grep -E '(^|/)(\.env(\..+)?|.*\.pem|.*\.key|id_rsa.*|.*\.p12|.*\.pfx)$' || true)"
if [ -n "$secret_files" ]; then
  echo "❌ 비밀 파일로 의심되는 파일이 diff에 있습니다:" >&2
  echo "$secret_files" >&2
  echo "env/secret manager로 옮기고 .gitignore에 추가한 뒤 다시 커밋하라." >&2
  exit 1
fi

# 2) 추가된 라인에서 흔한 비밀 패턴 탐지 (대소문자 무시)
added_lines="$(git diff $range 2>/dev/null | grep -E '^\+' | grep -Ev '^\+\+\+' || true)"

patterns=(
  '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'
  'AKIA[0-9A-Z]{16}'
  'GOCSPX-[A-Za-z0-9_-]{20,}'
  '(api[_-]?key|secret|password|token)[[:space:]]*[:=][[:space:]]*[A-Za-z0-9+/_=-]{16,}'
)

hit=0
for p in "${patterns[@]}"; do
  if echo "$added_lines" | grep -iqE -e "$p"; then
    echo "❌ 비밀값으로 의심되는 패턴 발견: $p" >&2
    hit=1
  fi
done

if [ "$hit" -eq 1 ]; then
  echo "우회하지 말고 값을 제거하거나 env/secret manager로 옮긴 뒤 다시 커밋하라." >&2
  exit 1
fi

echo "✅ check-secrets: ok"

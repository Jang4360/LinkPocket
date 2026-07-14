#!/usr/bin/env bash
# Codex Stop 훅 — verify.sh(게이트)가 green일 때만 턴 종료를 허용한다.
# green이 아니면 exit 2로 종료를 막아 Codex가 계속 고치게 한다. (루프 종료 조건)
root="$(cd "$(dirname "$0")/../.." && pwd)"
if ! bash "$root/scripts/verify.sh" 1>&2; then
  echo "게이트 실패: ./scripts/verify.sh 통과 전에는 종료할 수 없다. 테스트를 무르게 고치지 말고 실패를 고쳐라." >&2
  exit 2
fi
exit 0

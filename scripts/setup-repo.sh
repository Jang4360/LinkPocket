#!/usr/bin/env bash
# LinkPocket 저장소 초기화 + 원격 연결 + agent-agnostic 훅 활성화.
# 사용: 프로젝트 루트에서  bash scripts/setup-repo.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# 0) 혹시 남아있을 수 있는 (버그였던) Claude 훅 정리 — Claude는 계약 테스트 작성자라 훅을 두지 않는다
rm -f .claude/settings.json 2>/dev/null || true
rm -rf .claude/hooks 2>/dev/null || true

# 1) git init + 원격 연결
[ -d .git ] || git init -b main
git remote get-url origin >/dev/null 2>&1 || \
  git remote add origin https://github.com/Jang4360/LinkPocket.git

# 2) 원격 README 채택 (우리 파일은 untracked라 보존됨)
git fetch origin
git reset --hard origin/main

# 3) agent-agnostic pre-push 훅 활성화 (누가 push하든 계약 테스트 삭제 차단)
git config core.hooksPath .githooks

# 4) 첫 커밋 + 푸시
git add -A
git commit -m "docs + AI harness scaffold: SDD/loop/harness engineering, verify gate, per-agent+CI enforcement"
git push -u origin main

echo
echo "== 다음(수동) =="
echo "1) 브랜치 보호: GitHub → Settings → Branches → Add branch ruleset(또는 rule):"
echo "   - branch: main"
echo "   - Require a pull request before merging (approvals: 1)"
echo "   - Require status checks to pass → 'verify'"
echo "2) Codex: 최초 1회 /hooks 로 .codex/hooks.json 을 신뢰(trust)."

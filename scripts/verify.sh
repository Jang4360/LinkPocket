#!/usr/bin/env bash
# LinkPocket 게이트 — 두 AI(Claude·Codex)와 CI가 공통으로 실행하는 단일 심판.
# 이 스크립트가 green(exit 0)이어야 루프가 멈춘다. (docs/development-loop.md)
# greenfield 단계에서는 아직 없는 단계를 건너뛰고, 스캐폴딩되면 자동으로 활성화된다.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
echo "▶ verify: 시작 ($root)"

# 1) 백엔드 테스트 (Gradle)
if [ -x "./gradlew" ]; then
  echo "▶ ./gradlew test"
  ./gradlew test
elif [ -f "build.gradle.kts" ] || [ -f "build.gradle" ]; then
  echo "▶ gradle test (system gradle)"
  gradle test
else
  echo "· gradle 프로젝트 없음 — 스캐폴딩 후 이 단계가 활성화됨"
fi

# 2) 린트/포맷 (스캐폴딩 후 주석 해제)
# if [ -x "./gradlew" ] && ./gradlew tasks --all 2>/dev/null | grep -q spotlessCheck; then
#   echo "▶ ./gradlew spotlessCheck"
#   ./gradlew spotlessCheck
# fi

# 3) 프론트/익스텐션 (해당 디렉터리 생기면 추가)
# (예) if [ -f web/package.json ]; then (cd web && npm test && npm run lint); fi

echo "✅ verify: 통과"

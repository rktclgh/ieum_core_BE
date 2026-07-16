# 강퇴 후 모임 재입장 영구 차단 — 실행 계획

> **Issue:** #155
> **Branch:** `155-test-kicked-rejoin-ban`
> **Base:** `develop`

1. `join`의 기존 participant 조회가 잠금 없이 이뤄지는 현재 경합을 재현하는 실패 테스트를 작성한다.
2. 기존 participant 조회를 `FOR UPDATE` repository method로 교체하고, `kicked`에 대한 `KICKED_MEMBER` 거부와 chat member 무복구를 유지한다.
3. host kick → 대상 HTTP join을 검증하는 통합 회귀 테스트를 추가한다.
4. 대상 단위/통합 테스트, 변경 범위 diff check를 실행하고 PR을 생성한다.

# 강퇴 후 모임 재입장 영구 차단 (#155) — 백엔드 설계

## 문제

`MeetingService.join`은 모임 행만 잠근 뒤 기존 participant를 일반 조회한다. 반면 `kick`은 participant 행만 잠근다. `left` participant를 join이 읽은 직후 host kick이 커밋되면, join flush가 `kicked` 상태를 `joined`로 덮어쓸 수 있다.

## 결정

- 기존 participant가 있는 join은 `MeetingParticipantRepository.findByIdMeetingIdAndIdUserIdForUpdate`로 같은 participant 행을 잠근다.
- participant를 잠근 뒤 `kicked`면 어떤 join 부수효과도 만들지 않고 `403 KICKED_MEMBER`를 반환한다.
- join과 kick은 같은 participant 행 잠금으로 직렬화한다. join이 먼저 끝나면 kick이 최종 `kicked`로 만들고, kick이 먼저 끝나면 join은 `kicked`를 읽어 거부한다.
- 신규 participant는 기존 행이 없으므로 기존 meeting-row 정원 잠금과 PK 보호를 유지한다.

## 범위와 검증

- 유일한 모임 진입점 `POST /meetings/{id}/join`만 대상이다. 초대·승인·별도 group-chat join API는 현재 없다.
- 단위 테스트는 join이 잠금 조회를 사용하고 `kicked`일 때 chat member 복구 없이 거부함을 고정한다.
- HTTP 통합 테스트는 host kick 뒤 대상 join이 `403 KICKED_MEMBER`, participant가 `kicked`, group chat member가 비활성으로 남음을 검증한다.

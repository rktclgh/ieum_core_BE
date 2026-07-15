# 채택 답변 기반 질문 1:1 채팅방 설계

## 1. 문서 목적

GitHub `ieum_BE#89`의 요구사항을 현재 `develop` 구현에 맞춰 재정의하고, 질문 작성자와 채택된 사람 답변자 사이의 질문 전용 1:1 채팅방 생성 규칙을 확정한다.

이 문서는 사용자가 제공한 QnA 워크플로우 이미지의 다음 흐름을 기준으로 한다.

`질문 내역 → 답변 목록 → 답변 채택 확인 → 채팅 시작 → 질문 제목 채팅방 → 더보기/채팅방 나가기`

채팅방은 답변 채택과 동시에 자동 생성하지 않는다. 채택 성공 후 노출되는 `채팅 시작` 버튼을 사용자가 눌렀을 때 생성하거나 기존 방에 재입장한다.

## 2. 문제 정의

현재 `develop`에는 이미 다음 기능이 있다.

- `POST /api/v1/chat/rooms/question`
- `RoomType.question`
- 질문과 두 사용자 조합의 멱등 키 `q:{questionId}:{minUserId}:{maxUserId}`
- 친구관계 없이 질문 작성자와 사람 답변자 사이의 방 생성
- 양방향 차단 검사
- 동시 요청 시 동일 방 수렴 및 나간 멤버 재입장
- 생성·목록·상세 응답의 `questionTitle`

그러나 현재 서버는 대상 사용자가 해당 질문에 사람 답변을 하나라도 작성했는지만 확인한다. 프론트엔드가 미채택 답변에는 `채팅 시작` 버튼을 숨기더라도, API를 직접 호출하면 미채택 답변자와도 방을 만들 수 있다.

서버 권한의 정본은 화면 버튼 노출 여부가 아니라 `answers.is_accepted`여야 한다.

## 3. 목표와 비목표

### 목표

- 질문 작성자만 질문방 생성 또는 재입장을 요청할 수 있다.
- 대상 사용자는 같은 질문의 채택된 사람 답변 작성자여야 한다.
- 일반 1:1 채팅의 친구관계 요구를 질문방에는 적용하지 않는다.
- 차단, 본인 채팅, 삭제된 질문·사용자 등 기존 안전 제약은 유지한다.
- 질문방의 표시 이름은 질문 제목을 사용한다.
- 반복·동시 요청은 하나의 질문방으로 수렴한다.
- 기존 FE 요청 계약을 깨지 않는다.

### 비목표

- 채택 시점의 채팅방 자동 생성
- 일반 `direct` 채팅의 친구관계 정책 변경
- 답변자 측의 질문방 선제 생성 권한
- 차단 이후 기존 방의 메시지 송수신 정책 변경
- 사용자 제재·정지 전반의 세션 정책 변경
- 질문 수정 또는 삭제 후 방 제목 보존 정책 변경
- 새로운 DB 테이블, room type 또는 room title 컬럼 추가

## 4. UX 워크플로우 반영

| 화면 | 사용자 행동 | 서버 동작 |
|---|---|---|
| 질문 내역 | 질문 선택 | 질문 상세와 답변 목록 조회 |
| 답변 목록 | 미채택 답변의 `답변 채택` 선택 | 채택 확인 다이얼로그 표시 |
| 채택 확인 | 확인 다이얼로그 승인 | 채택 API 호출, 성공 후 질문 상세 재조회 |
| 답변 목록 | 채택된 답변의 버튼이 `채팅 시작`으로 전환 | 아직 방을 만들지 않음 |
| 채팅 시작 | `채팅 시작` 선택 | `POST /api/v1/chat/rooms/question` 호출 |
| 질문 채팅 | 질문 제목을 상단 제목으로 표시 | `roomType=question`, `questionTitle` 사용 |
| 더보기 | 참여자 확인, 알림·고정·나가기 | 기존 질문방 상세 및 방 설정 API 재사용 |

질문방은 화면상 1:1이지만 일반 친구 채팅과 구분되는 독립 도메인이다. 같은 두 사용자라도 질문이 다르면 서로 다른 질문방을 만든다.

## 5. 검토한 접근

### A. 기존 계약 보강 — 채택

요청 `{questionId, targetUserId}`를 유지하고 서버의 답변자 predicate에 `accepted=true`를 추가한다.

- 장점: FE 계약, controller, DTO, schema, room lifecycle을 유지한다.
- 장점: 변경 범위가 작고 기존 동시성·멱등성 검증을 그대로 활용한다.
- 단점: 요청이 특정 답변 ID를 직접 가리키지는 않는다.
- 판단: 방의 정체성이 답변 단위가 아니라 `질문 + 두 사용자` 단위이므로 사용자 기반 요청이 현재 모델과 일치한다.

### B. `answerId` 기반 계약

요청을 `{questionId, answerId}`로 바꾸고 서버가 대상 사용자를 도출한다.

- 장점: 채택된 답변과 요청 사이의 연결이 더 직접적이다.
- 단점: FE와 공개 API 계약을 깨고, 동일 작성자의 복수 채택 답변에 불필요한 답변 단위 식별을 노출한다.
- 판단: 이 이슈에서는 채택하지 않는다.

### C. 채택 이벤트 기반 자동 생성

채택 이벤트를 구독해 채택된 사람 답변자별로 방을 즉시 만든다.

- 장점: 이후 채팅 시작 지연이 없다.
- 단점: 사용자가 채팅하지 않아도 빈 방이 생기며, 차단·재입장·실패 처리가 답변 채택 트랜잭션에 결합된다.
- 판단: 제공된 UX와 맞지 않아 채택하지 않는다.

## 6. API 계약

### 요청

```http
POST /api/v1/chat/rooms/question
Content-Type: application/json
Cookie: access_token=...; sid=...; XSRF-TOKEN=...
X-CSRF-Token: ...
```

```json
{
  "questionId": 5,
  "targetUserId": 2
}
```

- 두 필드는 필수 양의 정수다.
- `targetUserId`는 채택된 사람 답변 작성자의 사용자 ID다.

### 성공 응답

```json
{
  "roomId": 7,
  "roomType": "question",
  "meetingId": null,
  "questionId": 5,
  "questionTitle": "명동에서 길 잃었어요"
}
```

- 새 방과 기존 방 모두 `200 OK`를 반환한다.
- 질문방 생성 시 활성 질문을 이미 읽었으므로 `questionTitle`은 non-null이다.
- 목록·상세에서 삭제 또는 레거시 데이터로 제목을 얻지 못할 가능성에 대비해 FE는 nullable fallback을 유지할 수 있다.

### 실패 응답

| HTTP | code | 조건 |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | ID 누락 또는 양수가 아님 |
| 400 | `SELF_CHAT_ROOM` | 질문 작성자와 대상 사용자가 동일 |
| 401 | `AUTHENTICATION_REQUIRED` | 세션 없음 또는 만료 |
| 403 | `CSRF_FAILED` | CSRF 검증 실패 |
| 403 | `FORBIDDEN` | 요청자가 질문 작성자가 아니거나 대상이 채택된 사람 답변자가 아님 |
| 403 | `BLOCKED` | 두 사용자 사이에 차단 관계가 있음 |
| 404 | `QUESTION_NOT_FOUND` | 질문이 없거나 soft-delete됨 |
| 404 | `USER_NOT_FOUND` | 대상 사용자가 없거나 탈퇴함 |

미채택 여부를 별도 공개 오류로 구분하지 않고 기존 `FORBIDDEN`을 사용한다. 이를 통해 답변·채택 상태를 권한 없는 호출자에게 추가로 노출하지 않는다.

## 7. 권한과 제약

### 질문방 전용 권한

아래 조건을 모두 만족해야 한다.

1. 요청자는 인증된 사용자다.
2. 질문은 활성 상태다.
3. 요청자는 질문 작성자다.
4. 대상은 탈퇴하지 않은 사용자다.
5. 대상은 요청자와 다른 사용자다.
6. 대상은 같은 질문에 `is_ai=false AND is_accepted=true`인 답변을 하나 이상 작성했다.
7. 두 사용자 사이에 차단 관계가 없다.

채택 관계의 정본은 다음 predicate다.

```text
answers.question_id = :questionId
AND answers.author_id = :targetUserId
AND answers.is_ai = false
AND answers.is_accepted = true
```

`questions.is_resolved`, 사용자의 `accepted_count`, 알림 또는 채택 이벤트를 권한 판정에 사용하지 않는다. 한 질문에는 복수 채택 답변이 존재할 수 있고, 권한은 대상 사용자의 실제 채택 답변 존재 여부로 판단해야 한다.

### 일반 1:1 채팅과의 차이

| 제약 | direct | question |
|---|---:|---:|
| 두 사용자가 서로 달라야 함 | 적용 | 적용 |
| 대상 사용자 존재 | 적용 | 적용 |
| 친구관계 accepted | 적용 | 미적용 |
| 양방향 차단 없음 | 적용 | 적용 |
| 질문 작성자 ↔ 채택 답변자 관계 | 미적용 | 적용 |

친구 제약만 우회하고 나머지 채팅 안전 제약은 유지한다.

### 나가기와 재입장

현행 `direct/question` 채팅에서 `채팅방 나가기`는 영구적인 연락 거부가 아니라 해당 사용자의 목록 노출을 중단하는 상태다. 권한을 다시 충족한 질문 작성자가 같은 질문방을 요청하면 기존 roomId를 반환하고 양쪽 멤버의 `left_at`을 해제한다.

- 재입장 전에 채택 관계와 차단 관계를 다시 검증한다.
- 대상 답변자가 나간 상태여도 차단하지 않았다면 기존 정책에 따라 다시 참여 상태가 된다.
- 영구적으로 상대의 재접촉을 거부하는 수단은 `채팅방 나가기`가 아니라 사용자 차단이다.
- 이 이슈에서는 전역 `direct/question` 재노출 정책을 변경하지 않는다.

## 8. 컴포넌트별 변경

### `AnswerRepository`

채택된 사람 답변 작성자 존재 여부를 확인하는 repository method를 추가한다.

권고 시그니처:

```java
boolean existsByQuestionIdAndAuthorIdAndAiFalseAndAcceptedTrue(
    Long questionId,
    Long authorId
);
```

기존 `existsByQuestionIdAndAuthorIdAndAiFalse`가 다른 곳에서 사용되지 않으면 대체 후 삭제한다.

### `ChatService`

현재 질문 작성자·본인·대상 사용자 검증 뒤 실행하는 사람 답변자 검사를 채택된 사람 답변자 검사로 교체한다.

다음 로직은 유지한다.

- 질문의 pessimistic read lock
- 전체 작업의 `REQUIRES_NEW` 트랜잭션
- room-key unique race 시 전체 트랜잭션 1회 재시도
- 차단 검사
- `ChatRoomLifecycle.getOrCreateQuestionRoom`
- `questionTitle` 응답

### `ChatRoomLifecycle`와 공용 도메인

변경하지 않는다.

- `RoomType.question` 재사용
- 키 `q:{questionId}:{minUserId}:{maxUserId}` 재사용
- 방 생성 또는 기존 방 조회
- 양쪽 `chat_members` 생성 또는 `left_at=null` 재입장

### FE 계약 후속

BE 이슈와 별도 변경 묶음으로 처리한다.

- 생성·목록·상세 타입에 `questionTitle: string | null` 추가
- 질문방 제목은 `questionTitle`을 최우선 사용
- 제목이 없을 때만 기존 닉네임/질문 조회 fallback 사용
- mutation pending 동안 `채팅 시작` 버튼 비활성화
- 성공 시 canonical chat route로 이동

## 9. 트랜잭션과 동시성

질문방 생성은 기존과 동일하게 하나의 물리 트랜잭션에서 처리한다.

1. 요청자 조회
2. 질문 pessimistic read lock
3. 질문 작성자 권한과 본인 대상 여부 확인
4. 대상 사용자 조회
5. 채택된 사람 답변자 존재 확인
6. 차단 확인
7. room key 조회 또는 생성
8. 두 멤버 생성 또는 재입장
9. 방과 질문 제목 응답

답변 채택은 같은 질문에 pessimistic write lock을 사용한다. 따라서 채택과 방 생성이 경쟁하면 다음과 같이 동작한다.

- 채택 commit 이전에 생성 검증이 끝나면 미채택으로 거부한다.
- 채택 commit 이후에는 accepted predicate가 참이 되어 생성한다.
- FE는 채택 API 성공 응답 후 `채팅 시작`을 노출하므로 정상 UI 흐름에서는 채택 commit 이후 호출된다.

같은 요청이 동시에 들어오면 `chat_rooms.room_key` unique 제약으로 하나의 방만 남고, 실패한 트랜잭션은 기존 전체-operation retry 경로를 통해 같은 방을 반환한다.

## 10. 표시 이름 정책

질문방의 표시 이름은 질문 제목이다.

- 별도 `label` 또는 `title` 컬럼을 `chat_rooms`에 추가하지 않는다.
- 생성 응답, 방 목록, 방 상세의 `questionTitle`을 UI 표시 이름으로 사용한다.
- `roomType=question`은 필터와 아이콘·카테고리를 결정한다.
- `questionTitle`은 실제 방 제목을 결정한다.

질문이 soft-delete된 이후 기존 방의 제목 처리와 방 유지 여부는 별도 제품 정책이다. 현재 이슈에서는 기존 동작을 유지하며, hard purge 시 FK cascade로 질문방도 제거된다.

## 11. 테스트 전략

### 단위 테스트

- 질문 작성자는 채택된 사람 답변자와 친구가 아니어도 질문방 생성 성공
- 미채택 사람 답변자는 `FORBIDDEN`
- AI 답변만 채택된 경우 사람 대상 생성 거부
- 다른 질문에서만 채택된 답변자 거부
- 질문 작성자가 아닌 요청자 거부
- 본인 대상 거부
- 삭제된 질문·사용자 거부
- 양방향 차단 각각 거부
- room-key 충돌 retry 시 채택·차단·사용자 상태를 다시 검증

### repository/integration 테스트

- accepted predicate가 `questionId`, `authorId`, `ai=false`, `accepted=true`를 모두 적용
- 서로 다른 채택 답변자 두 명은 서로 다른 방 생성
- 한 사용자의 채택 답변이 여러 개여도 동일 방 반환
- 같은 tuple의 동시 요청은 방 하나, 멤버 두 명으로 수렴
- 기존 방에서 양쪽이 나간 뒤 재호출하면 동일 roomId로 재입장
- 대상이 나간 뒤 재호출하더라도 채택·차단 검증을 다시 수행하고, 차단이 없을 때만 양쪽을 재입장
- 채택 finalization과 방 생성 경쟁 결과가 lock 순서와 일치
- 채택 finalization만으로는 `chat_rooms`가 증가하지 않고, 이후 질문방 생성 API 호출 시에만 방이 생성됨

### controller/contract 테스트

- 요청 필드 validation
- 성공 응답의 `roomType=question`, `questionId`, `questionTitle`
- 인증·CSRF·권한·차단 오류 매핑

### 문서 검증

- 로컬 API SSOT의 조건을 `채택된 사람 답변자`로 수정
- 응답 예시에 `questionTitle` 추가
- Notion API 명세와 성공·실패 Response DB 동기화

## 12. 수용 기준

- [ ] 미채택 답변자의 user ID로 API를 직접 호출해도 방이 생성되지 않는다.
- [ ] 질문 작성자는 채택된 사람 답변자와 친구가 아니어도 질문방을 생성할 수 있다.
- [ ] 차단 관계에서는 친구 여부와 무관하게 생성할 수 없다.
- [ ] 같은 질문과 같은 두 사용자의 반복·동시 호출은 같은 roomId를 반환한다.
- [ ] 질문이 다르면 같은 두 사용자라도 별도 방이 생성된다.
- [ ] 응답과 방 목록·상세에서 `roomType=question`, `questionId`, `questionTitle`을 확인할 수 있다.
- [ ] 제공된 워크플로우처럼 채택 후 `채팅 시작`을 눌렀을 때만 방이 생성된다.
- [ ] 답변 채택 API만 호출했을 때는 질문방이 생성되지 않는다.
- [ ] 나간 대상이 차단한 상태에서는 기존 질문방 재입장이 거부된다.
- [ ] DB migration 없이 기존 schema로 동작한다.
- [ ] 관련 타깃 테스트, module test, 가능한 smoke test가 통과한다.
- [ ] 코드·로컬 API SSOT·Notion 계약이 일치한다.

## 13. 구현 작업 묶음

### Packet A — 백엔드 권한 강화

- 범위: `AnswerRepository`, `ChatService`, 관련 unit/repository/integration test
- 목표: 채택된 사람 답변자만 질문방 생성 허용
- 금지: controller path, request DTO, RoomType, schema 변경

### Packet B — 공개 계약 동기화

- 범위: 로컬 API SSOT, 기능 spec/memory, Notion API 명세
- 목표: 채택 조건과 `questionTitle`을 성공·실패 계약에 반영
- 선행 조건: Packet A의 계약 확정

### Packet C — FE 제목 소비 후속

- 범위: 별도 FE 이슈·브랜치
- 목표: `questionTitle`을 질문방 제목으로 직접 사용하고 불필요한 질문 상세 조회 축소
- 선행 조건: BE 응답 계약 확인

## 14. 결정 기록과 잔여 위험

### 확정 결정

- 채팅방은 채택 시 자동 생성하지 않고 `채팅 시작` 클릭 시 생성한다.
- 질문 작성자만 시작할 수 있다.
- 대상은 채택된 사람 답변 작성자여야 한다.
- 친구관계는 요구하지 않으며 차단은 유지한다.
- 기존 `question` room type과 question-scoped room key를 사용한다.
- 질문 제목은 `questionTitle`로 제공하고 새 title 컬럼은 만들지 않는다.
- 미채택·관계 불일치는 공통 `403 FORBIDDEN`으로 반환한다.

### 잔여 위험·후속 정책

- 사용자가 방 생성 후 상대를 차단했을 때 기존 방의 송수신을 막을지는 전역 채팅 정책으로 별도 결정해야 한다.
- 정지 사용자를 기존 방의 상대방으로 노출할지 여부는 사용자 제재 정책 범위다.
- soft-delete된 질문방의 제목 보존과 방 유지 정책은 삭제 정책 범위다.
- 현재 FE는 `questionTitle`을 타입에 반영하지 않아 추가 질문 조회와 fallback을 사용한다.

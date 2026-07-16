# 채팅 답장 메시지 (#161) — 백엔드 설계

## 1. 목표

사용자가 같은 채팅방의 다른 사용자 메시지에 답장할 수 있게 한다. 답장 메시지는 원문 전체를 복사하지 않고, 안정적인 한 단계 preview만 저장·전달한다. REST history, 채팅 목록 마지막 메시지, STOMP room event는 같은 reply 계약을 사용한다.

## 2. 범위와 비범위

### 범위

- `messages.reply_to_message_id` nullable self FK
- STOMP send payload의 optional `replyToMessageId`
- history/summary/room WebSocket의 flat `replyTo` preview
- 같은 room·현재 가시 이력·user message만 답장 대상으로 허용하는 서버 검증

### 비범위

- 스레드, 답장 수, 원문으로 jump하는 API, 답장 편집/삭제
- system message·자기 자신·삭제된 메시지에 답장
- 기존 메시지 body, image upload, push notification 또는 notice/report 정책 재설계

## 3. 데이터 모델 결정

```sql
ALTER TABLE messages
  ADD COLUMN reply_to_message_id BIGINT
  REFERENCES messages(message_id) ON DELETE SET NULL;
```

`Message.replyTo`는 nullable `@ManyToOne(fetch = LAZY)`다. Cascade는 사용하지 않는다. 원문 삭제가 발생하면 DB가 FK만 NULL로 바꾸고 답장 본문은 남는다.

응답의 reply는 재귀 relation이 아니라 전송 시점의 parent를 읽은 flat preview다.

```json
"replyTo": {
  "messageId": 123,
  "senderId": 45,
  "senderNickname": "김연두",
  "content": "떡볶이 먹을까?",
  "imageUrl": null
}
```

`replyTo` 안에는 다시 `replyTo`가 없다. 이미지 원문은 `content=null`, `imageUrl`만 제공한다. parent가 physical delete로 FK NULL이 되면 replyTo도 null이며 과거 답장 본문은 그대로 조회된다.

## 4. STOMP와 REST 계약

클라이언트는 기존 `/app/rooms/{roomId}/send` destination에 optional id만 추가한다.

```json
{
  "content": "전 다 좋아요",
  "imageFileId": null,
  "replyToMessageId": 123
}
```

`content`/`imageFileId` 상호배타·길이 제약은 그대로 유지한다. reply가 없는 기존 send body도 완전히 호환된다.

`ChatMessageResponse`, `ChatRoomSummaryResponse.lastMessage`, `WsMessageEvent`에는 optional nullable `replyTo`를 같은 의미로 넣는다. `replyTo` 필드가 없는 구 서버 이벤트는 프론트가 null로 정규화할 수 있게 한다.

## 5. 검증과 가시성 경계

`ChatMessageService.send`은 active ChatMember를 먼저 확인한 뒤 reply target을 조회한다. target이 다음을 모두 만족해야 한다.

1. 동일 `roomId`
2. `deletedAt == null`
3. `messageType == user`
4. `target.messageId > member.visibleAfterMessageId`

네 번째 조건은 1:1 방을 나갔다 재입장한 사용자가 이전 이력을 id로 추측해 답장하면서 원문/닉네임을 재노출하는 것을 막는다. 불일치·system·삭제·숨김 과거 target은 `400 INVALID_CHAT_MESSAGE`로 처리한다.

현재 사용자 본인 메시지는 프론트 메뉴에 나타나지 않지만, 서버는 self reply를 별도로 금지하지 않는다. 같은 room/current-visible user message라는 일반 규칙을 유지해 향후 UX 변화와 서버 계약을 불필요하게 결합하지 않는다.

## 6. 조회 성능과 이벤트 순서

이력과 room summary query는 message sender뿐 아니라 `replyTo`와 reply sender도 `LEFT JOIN FETCH` 한다. parent가 없는 메시지는 left join으로 유지한다. 페이지별 N+1을 만들지 않는다.

```text
active member 확인
  -> reply target visibility 검증
  -> user Message(text/image, replyTo) 저장
  -> room summary upsert 예약
  -> commit 후 WS message event + 기존 push
```

저장 실패 시 event/push는 발행하지 않는다. 답장은 일반 user message이므로 기존 push/room-list 순서는 유지한다.

## 7. migration과 통합 순서

일정 관리 #160이 v29~v31 migration을 예약했다. 이 이슈는 `v32_chat_message_reply.sql`을 추가하고, 최종 PR 생성 전에 #160 branch/develop을 rebase해 migration helper·workflow·schema 목록을 충돌 없이 통합한다.

`db/schema.sql`, `deploy/scripts/apply-admin-dashboard-migrations.sh`, deploy workflows와 deploy shell tests 모두 v32를 반영한다. migration은 additive이며 production DB에 이 이슈 과정에서 직접 적용하지 않는다.

## 8. 구현 경계

| 영역 | 책임 |
| --- | --- |
| `common/chat/domain/Message` | nullable replyTo association, text/image factory 확장 |
| `common/chat/repository/MessageRepository` | visibility-aware reply target lookup, reply/sender fetch joins |
| `main/chat/dto/SendChatMessageRequest` | optional `replyToMessageId` |
| `main/chat/dto/ChatMessageResponse`, `ChatReplyPreview` | REST/summary reply wire model |
| `main/chat/service/ChatMessageService` | target validation, message creation, existing after-commit event flow |
| `main/chat/service/WsMessageEvent` | REST와 같은 flat preview fanout |
| `db/schema.sql`, `db/migrations/v32_*`, deploy files | schema/운영 migration delivery |

## 9. 최소 검증

1. ChatMessageService: text/image reply의 저장·REST·WS preview, invalid room/system/deleted/hidden target 거절.
2. MessageRepository: history와 last-message summary가 reply/sender fetch join을 이용해 flat preview를 만든다.
3. v32 PostgreSQL migration과 schema contract: nullable FK 및 `ON DELETE SET NULL`.
4. existing WebSocket fixture/DTO test: replyTo가 없는 기존 message도 정상 직렬화.

변경 모듈 focused Gradle tests와 compile만 수행한다. 전체 test suite는 범위 밖이다.

## 10. 문서 완료 조건

구현과 최소 검증 후에만 `code/api/API-SPEC.md`의 chat history, STOMP send/event, room summary lastMessage 계약을 AS-BUILT 값으로 변경한다. 같은 내용을 Notion에 동기화하고 세 정본이 일치한 뒤 구현완료로 표시한다.

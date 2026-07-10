# App AI Design Spec

## 1. Goal

`app-ai`는 질문 답변 생성, 유사 질문 검색, 신고 판단, 임베딩과 Knowledge Graph(KG) 파생 데이터 생성을 담당한다. 모델 호출과 검색 파이프라인은 `app-ai`가 소유하지만, 사용자 권리와 서비스 상태를 바꾸는 업무 결정은 전부 `app-main`이 소유한다.

핵심 불변식은 다음과 같다.

> `app-ai`는 recommendation과 AI artifact를 만든다. `app-main`만 domain decision과 부수효과를 수행한다.

## 2. Fixed Decisions

- `app-main`, `app-ai`, `common`은 하나의 모노레포와 하나의 PostgreSQL 데이터베이스를 사용한다.
- 공유 DB는 공유 쓰기 권한을 의미하지 않는다. 테이블·행 종류별 쓰기 소유권을 분리한다.
- `app-ai`는 Spring Boot를 유지하고 Spring AI를 모델·임베딩·PGvector 어댑터로 사용한다.
- v1은 공식 LangGraph를 도입하지 않는다. 유스케이스별 명시적인 고정 워크플로를 Java 21 코드로 구현한다.
- Kotlin은 필수가 아니다. 도입한다면 `app-ai` 전체를 Kotlin으로 작성하고 같은 기능 안에서 Java/Kotlin을 혼용하지 않는다. v1 권장은 Java 21이다.
- 질문은 등록 후 제목·본문·이미지를 수정할 수 없다.
- 질문 삭제는 soft-delete이며, `questions.deleted_at`이 AI 파이프라인의 내구성 있는 취소 신호다.
- 질문 생성 시 `app-main`은 `app-ai`를 호출하거나 Redis 이벤트를 발행하지 않는다. `app-ai`가 활성 질문을 자율 스윕한다.
- 신고 판단과 유사 질문 검색은 `app-main -> app-ai` 동기 HTTP 호출이다.
- Redis pub/sub은 app-main↔app-ai 통신에 사용하지 않는다.

## 3. Responsibility Boundary

### 3.1 app-main owns

- 질문·핀 생성, 조회, soft-delete, 해결 상태
- 사람 답변 생성과 모든 답변 채택
- 신고 접수, 신고 상태 전이, AI 권고 저장
- AI 권고를 업무 정책에 적용할지 판단하는 `ReportDecisionPolicy`
- `user_sanctions` 생성과 `users.status` 변경
- 로그인 세션, Redis, JWT, force-logout, 열린 SSE 종료
- 외부 클라이언트 인증·인가·validation
- AI 호출 실패 시 사용자-facing 응답과 신고 재처리

### 3.2 app-ai owns

- 질문 임베딩 생성
- 질문 답변용 KG + Vector hybrid RAG
- AI 답변 `is_ai=true, author_id=NULL` 1회 생성
- 유사 질문 검색과 재정렬
- 신고 맥락 분석과 recommendation DTO 반환
- AI 작업 claim, lease, retry, cancellation, dead-letter 상태
- KG, vector knowledge chunk, 모델 실행 메타데이터
- prompt/model/policy version과 evidence provenance

### 3.3 app-ai must never do

- `users.status` 변경
- `user_sanctions` INSERT·UPDATE·DELETE
- `reports.status` 변경
- Redis 세션·refresh token 접근
- force-logout 또는 SSE close
- 사람 답변 생성·수정·삭제·채택
- 질문 제목·본문·이미지·삭제 상태 변경
- AI recommendation을 실제 정지 명령으로 취급

## 4. Shared Database Ownership

| Data | app-main | app-ai |
|---|---|---|
| `questions`, `pins` | write owner | active row read-only |
| human `answers` | write owner | trusted source read-only |
| AI `answers` | read/accept | `is_ai=true` row one-time insert |
| `reports` | write owner | no DML; request payload로 맥락 수신 |
| `users`, `user_sanctions` | write owner | no DML |
| `question_ai_jobs` | optional read | write owner |
| `question_embeddings` | optional read | write owner |
| `ai_answer_metadata`, `ai_answer_sources` | read | write owner |
| KG·knowledge chunk tables | read if needed | write owner |
| `ai_inference_runs` | correlation 조회 | write owner |

운영 환경은 같은 DB 안에서 `ieum_main`, `ieum_ai` DB role을 분리한다. `ieum_ai`에는 `users`, `user_sanctions`, `reports` UPDATE 권한을 부여하지 않는다. 기존 `answers`에 AI 답변을 저장해야 하므로 `ieum_ai`에는 `answers` 직접 INSERT 권한을 주지 않고, 활성 질문에 `is_ai=true, author_id=NULL` 행만 멱등 삽입하는 `insert_ai_answer_if_active(question_id, content)` SECURITY DEFINER 함수의 EXECUTE 권한만 부여한다.

## 5. Question Lifecycle

### 5.1 Create

`app-main`의 질문 생성 트랜잭션은 다음만 수행한다.

1. `pins` INSERT
2. `questions` INSERT
3. `question_images` INSERT
4. durable/ephemeral 알림의 app-main 처리
5. commit

AI HTTP 호출, Redis `question.created`, `question.updated` 발행은 없다.

`app-ai`의 discovery sweep은 다음 조건의 질문을 찾아 `question_ai_jobs`를 `INSERT ... ON CONFLICT DO NOTHING`으로 생성한다.

```sql
q.deleted_at IS NULL
AND p.deleted_at IS NULL
AND NOT EXISTS (
  SELECT 1 FROM question_ai_jobs j WHERE j.question_id = q.question_id
)
```

권장 discovery 주기는 5초, 처리 batch는 20건이다. 처리량은 설정값으로 조정하되 correctness에 영향을 주지 않는다.

### 5.2 Immutable after create

- `PATCH /questions/{id}`를 제공하지 않는다.
- `QuestionUpdateRequest`, `QuestionService.update`, `Question.update`를 폐기한다.
- `/reembed`, `question.updated`와 수정 기반 재임베딩 규칙을 폐기한다.
- 수정 대신 삭제 후 새 질문을 등록한다.

### 5.3 Delete and cancellation

`DELETE /api/v1/questions/{questionId}`는 작성자만 호출하며 멱등 `204`다. `app-main`은 하나의 트랜잭션에서 질문 row를 잠그고 `questions.deleted_at`, 연결된 `pins.deleted_at`을 함께 기록한다.

`app-ai`는 다음 경계마다 질문과 핀이 활성 상태인지 확인한다.

1. job claim 전
2. embedding/model 호출 전
3. KG·Vector retrieval 후
4. answer generation 전
5. 결과 저장 직전

삭제 취소의 정확한 보장은 다음과 같다.

> 질문 삭제가 커밋된 뒤에는 새 AI 단계를 시작하지 않으며, embedding·AI 답변·metadata를 커밋하지 않는다.

이미 전송된 외부 모델 요청의 네트워크 실행 완료까지 막는 것은 보장하지 않는다. 응답이 도착하면 폐기하고 job을 `CANCELLED`로 종료한다.

최종 저장 트랜잭션은 활성 질문을 `FOR SHARE`로 잠근 뒤 `question_embeddings`, AI answer, metadata, sources, job `COMPLETED`를 원자적으로 저장한다. 삭제 트랜잭션은 `FOR UPDATE`를 사용해 최종 저장과 직렬화한다.

## 6. Async Job Model

`question_ai_jobs` 상태 전이는 다음과 같다.

```text
PENDING -> PROCESSING -> COMPLETED
                    \-> RETRY -> PROCESSING
                    \-> CANCELLED
                    \-> DEAD
```

필드:

- `question_id`: PK, 질문별 job 하나
- `status`: `pending|processing|retry|completed|cancelled|dead`
- `stage`: `discovered|retrieving|generating|validating|persisting`
- `attempts`
- `next_attempt_at`
- `lease_until`
- `locked_by`
- `last_error_code`, `last_error_message`
- `created_at`, `started_at`, `completed_at`, `cancelled_at`

claim은 짧은 트랜잭션에서 `FOR UPDATE SKIP LOCKED`로 수행하고, 외부 AI 호출 전에 커밋한다. DB row lock을 유지한 채 모델을 호출하지 않는다.

기본 정책:

- lease: 2분
- 최대 시도: 5회
- backoff: 10초, 30초, 2분, 10분, 30분
- lease가 만료된 `PROCESSING`은 `RETRY`로 회수
- 재시도 불가능한 validation/policy 오류는 즉시 `DEAD`
- 삭제 질문은 시도 횟수를 소비하지 않고 `CANCELLED`

## 7. Explicit Orchestrators

범용 workflow DSL이나 자체 graph engine을 만들지 않는다. 다음 유스케이스 클래스가 흐름을 직접 소유한다.

- `QuestionAnswerWorkflow`
- `SimilarQuestionService`
- `ReportReviewWorkflow`
- `KnowledgeIngestionWorkflow`

Spring AI는 adapter 계층에서만 사용한다.

```text
QuestionEmbeddingGateway
AnswerGenerationGateway
ReportReviewGateway
VectorEvidenceRetriever
KnowledgeGraphRetriever
EvidenceFusionPolicy
GroundingValidator
```

오케스트레이터는 `ChatClient`, Bedrock SDK, Gemini SDK, `VectorStore` 같은 구체 타입에 직접 의존하지 않는다.

## 8. Question Answer Workflow

```text
Load active question
  -> embed query + extract entities
  -> Vector retrieval -----\
                           +-> fuse/rerank -> evidence budget
  -> KG retrieval ---------/
  -> active check
  -> grounded answer generation
  -> grounding validation
  -> active check
  -> atomic persist
```

v1 기본 검색값:

- Vector 후보: 20
- KG 후보: 20
- KG traversal: 기본 1-hop, 허용 관계만 최대 2-hop
- fusion: weighted Reciprocal Rank Fusion, `k=60`
- 초기 가중치: Vector 0.6, KG 0.4
- 최종 evidence: 최대 8개
- evidence가 기준 미달이면 사실을 추측하지 않고 근거 부족 안내 답변을 생성

Vector와 KG retriever는 서로 다른 evidence type을 반환하며, `EvidenceFusionPolicy`만 두 결과를 결합한다. 모델이 임의로 DB 도구를 호출하거나 검색 루프를 반복하게 하지 않는다.

## 9. Knowledge Sources

v1 신뢰 지식 소스:

- 운영자가 등록한 curated source
- 삭제되지 않은 질문의 채택된 사람 답변
- 별도 검증을 통과한 외부 source

AI 답변은 채택되더라도 KG나 Vector RAG 지식 소스로 다시 적재하지 않는다. AI 산출물이 자기 근거로 순환하는 feedback loop를 차단한다.

같은 PostgreSQL 안에 다음 app-ai 소유 테이블을 둔다.

- `knowledge_sources`
- `knowledge_chunks`
- `knowledge_chunk_embeddings`
- `kg_entities`
- `kg_entity_aliases`
- `kg_relations`

v1 KG는 관계형 adjacency 모델로 1~2 hop만 지원한다. Neo4j·Apache AGE 등 별도 graph runtime은 도입하지 않는다. source가 삭제되면 `knowledge_sources.active=false`로 만들고, 활성 source가 없는 chunk·relation은 retrieval에서 제외한다.

## 10. Synchronous APIs

### 10.1 Similar questions

```http
POST /ai/v1/questions/similar
```

```json
{
  "title": "string",
  "content": "string",
  "lat": 37.5,
  "lng": 127.0,
  "limit": 5
}
```

app-main이 인증·validation 후 호출한다. app-ai는 활성 질문의 `question_embeddings`를 검색하고 위치·컨텍스트 가중치를 적용한다. 삭제 질문과 embedding 미완료 질문은 제외한다.

응답:

```json
{
  "items": [
    {
      "questionId": 3,
      "title": "string",
      "similarity": 0.87,
      "isResolved": true,
      "acceptedAnswer": { "content": "string", "isAi": false }
    }
  ]
}
```

`limit`은 1~10이며 기본값은 5다. embedding provider timeout은 `503 AI_PROVIDER_UNAVAILABLE`, 잘못된 좌표·본문·limit은 `400 INVALID_AI_REQUEST`로 반환한다.

### 10.2 Report review

```http
POST /ai/v1/internal/reports/{reportId}/review
Idempotency-Key: report-review:{reportId}:{contextHash}:{policyVersion}
```

app-main은 DB row id만 넘기지 않고 신고 유형, 상세 사유, 대상 메시지와 전후 맥락의 immutable snapshot을 body에 포함한다. app-ai는 `reports`를 읽거나 수정하지 않는다.

요청:

```json
{
  "reportId": 900,
  "reporterId": 10,
  "reportedUserId": 20,
  "reason": "harassment",
  "detail": "string",
  "context": {
    "roomId": 7,
    "before": [
      { "messageId": 1, "senderId": 30, "content": "string", "imageFileId": null, "createdAt": "2026-07-10T10:00:00Z" }
    ],
    "reported":
      { "messageId": 2, "senderId": 20, "content": "string", "imageFileId": null, "createdAt": "2026-07-10T10:01:00Z" },
    "after": []
  }
}
```

응답:

```json
{
  "reviewId": "uuid",
  "recommendation": "temporary_suspend",
  "confidence": 0.94,
  "reasonCode": "harassment",
  "reason": "반복적인 위협 표현이 확인됨",
  "recommendedDurationMinutes": 1440,
  "modelVersion": "amazon.nova-micro-v1:0",
  "policyVersion": "report-policy-v1"
}
```

`recommendation`은 명령이 아니다. app-main의 `ReportDecisionPolicy`가 confidence threshold, 실제 sanction type/duration, report 상태 전이를 결정한다. app-main은 결정 트랜잭션 커밋 뒤에만 force-logout과 SSE close를 수행한다.

app-ai는 idempotency key별 inference 결과를 `ai_inference_runs`에 저장한다. 응답 유실 후 같은 key로 재호출되면 모델을 다시 호출하지 않고 기존 결과를 반환한다.

- 같은 idempotency key와 다른 input hash가 들어오면 `409 IDEMPOTENCY_KEY_REUSED`다.
- 요청 shape·reason enum이 잘못되면 `400 INVALID_AI_REQUEST`다.
- provider timeout·throttling·5xx는 `503 AI_PROVIDER_UNAVAILABLE`로 정규화하며 app-main이 재시도한다.
- structured output이 policy schema를 만족하지 않으면 1회 repair 후 `502 INVALID_AI_OUTPUT`을 반환한다.

## 11. Report Recovery Ownership

- app-main이 `pending` report를 소유한다.
- report commit 후 app-main worker가 review endpoint를 호출한다.
- 호출 실패 또는 timeout이면 report는 `pending`으로 남는다.
- app-main pending sweep이 같은 claim/idempotency 규칙으로 재시도한다.
- app-ai는 report backlog를 스윕하거나 sanction을 적용하지 않는다.

"후속 업무 부수효과가 있는 쪽이 복구를 소유한다"는 규칙을 적용한다. 신고의 후속효과는 정지·세션 파기이므로 app-main이 복구 주체다.

## 12. Model Separation

- answer/report generation model과 embedding model을 동일 모델로 간주하지 않는다.
- Nova Micro는 생성·판단 adapter 후보다.
- 현재 embedding 계약은 `gemini-embedding-001`, 768차원이다.
- embedding 모델을 바꾸어 차원이 달라지면 컬럼·HNSW index 재생성과 전체 재임베딩 migration이 필요하다.
- Bedrock direct region과 cross-region inference profile 선택은 configuration concern이며 workflow 계약을 바꾸지 않는다.

## 13. Timeouts and Failure Rules

- embedding call timeout: 5초
- Vector/KG DB retrieval timeout: 각 2초
- answer generation timeout: 30초
- report review timeout: 20초
- timeout·5xx·throttling은 retryable
- invalid structured output은 1회 repair 후 실패 처리
- prompt injection으로 system policy나 evidence boundary 변경을 허용하지 않는다.
- 모델 원문 응답에 개인정보·secret을 로그로 남기지 않는다.

## 14. Observability

필수 correlation:

- `jobId` 또는 `questionId`
- `reviewId`와 idempotency key hash
- model/version, prompt/policy version
- stage, attempt, latency, token usage

필수 metric:

- pending/processing/retry/dead/cancelled job 수
- stage별 latency·오류율
- model timeout·throttling
- Vector/KG 후보와 최종 evidence 수
- grounding score와 근거 부족 응답 비율
- 삭제 후 결과 폐기 수
- 모델·기능별 token/cost

## 15. Security

- app-ai inbound는 EC2-1 보안그룹에서만 허용한다.
- 클라이언트는 app-ai를 직접 호출하지 않는다.
- app-ai는 JWT·세션·Redis를 사용하지 않는다.
- app-main은 클라이언트가 보낸 `X-User-*`를 제거하고 필요한 내부 컨텍스트만 다시 주입한다.
- 운영 DB role을 분리해 책임 경계를 권한으로도 강제한다.
- report review payload와 model prompt에는 필요한 최소 개인정보만 포함한다.

## 16. Schema Migration Order

운영 DB가 이미 v9라면 한 번에 destructive 변경하지 않는다.

1. `questions.deleted_at`, AI job/embedding/metadata/KG/inference tables와 새 enum을 추가한다.
2. 기존 `questions.embedding` 값이 있으면 `question_embeddings`로 backfill한다.
3. app-ai reader/writer와 유사질문 쿼리를 새 테이블로 전환한다.
4. 질문 PATCH와 Redis AI publisher/consumer를 제거한다.
5. 신고 HTTP recommendation 경로와 app-main decision policy를 배포한다.
6. 기존 `ai_verdict='suspend'`는 `ai_recommendation='temporary_suspend'`로 변환하고 sanction source 컬럼을 migration한다.
7. 양쪽 앱이 새 계약으로 동작하는 것을 확인한 뒤 `questions.embedding`, 구 enum·구 컬럼을 제거한다.

각 단계는 app-main과 app-ai가 동시에 기동 가능한 expand-and-contract migration으로 수행한다.

## 17. Acceptance Criteria

1. 질문 수정 API와 `question.updated` 계약이 존재하지 않는다.
2. 활성 질문은 app-ai 자율 스윕으로 10초 이내 job이 생성된다.
3. 워커 2개가 동시에 처리해도 질문당 AI 답변은 하나다.
4. 삭제 커밋 후 후속 AI 호출과 결과 커밋이 발생하지 않는다.
5. 외부 호출 중 삭제되면 응답을 폐기하고 job은 `CANCELLED`다.
6. app-ai DB 계정으로 `users`, `user_sanctions`, `reports`를 변경할 수 없다.
7. report review 응답만으로 app-ai가 사용자를 정지시키지 않는다.
8. app-main만 report 결과 저장·sanction·status·force-logout을 수행한다.
9. 같은 report idempotency key 재호출은 같은 결과를 반환하고 모델을 다시 호출하지 않는다.
10. AI 답변은 KG/Vector knowledge source로 적재되지 않는다.
11. 삭제되거나 inactive source에서 나온 evidence는 검색 결과에 포함되지 않는다.
12. embedding·AI 답변·metadata·job 완료는 활성 질문에 대해서만 원자적으로 저장된다.

## 18. Non-Goals

- LangGraph/LangSmith Agent Server 도입
- 범용 workflow DSL 또는 agent framework 자체 개발
- 모델이 검색 도구와 반복 횟수를 동적으로 결정하는 agent loop
- AI 내부 human-in-the-loop pause/resume
- Neo4j·별도 graph database 도입
- app-ai의 사용자 인증·세션·알림 처리
- app-ai의 제재·정지·해제 실행
- 질문 수정·재임베딩 API

## 19. Revisit LangGraph When

다음 요구가 실제 제품 요구가 될 때만 별도 ADR로 재검토한다.

- 모델 주도 동적 routing과 반복 tool use
- 여러 agent hand-off
- 수분~수시간 실행의 단계별 resume
- graph 내부 human approval과 재개
- checkpoint replay/time travel이 운영 필수
- Python/TypeScript AI runtime 분리가 승인됨

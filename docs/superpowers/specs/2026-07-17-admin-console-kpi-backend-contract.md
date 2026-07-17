# 운영자 KPI Overview 백엔드 계약 설계

## 상태

- 결정일: 2026-07-17
- 브랜치: `feat/admin-console-kpi`
- 상위 UX 설계: 관리자 `/admin/` 대시보드의 7/30/90일 KPI 추세

## 계약

`GET /api/v1/admin/stats/overview?from=YYYY-MM-DD&to=YYYY-MM-DD&bucket=day`

이 endpoint는 기존 `users`, `content`, `reports` aggregate endpoint를 대체하지 않는다. 기존 세 endpoint는 호환성을 위해 유지하고, 새 endpoint만 summary·일별 series·현재 queue를 한 response로 반환한다.

```text
summary: 기간 전체 count와 acceptedRate
series: from..to의 모든 KST 날짜, 0값 날짜 포함
queues: pending report, retry report, dead report, pending inquiry의 현재 count
```

범위는 기본 30일, 최대 366일, KST 자정 기준이며 `bucket=day`만 지원한다. 역전된 범위·잘못된 날짜·다른 bucket은 `400`으로 거부한다. endpoint는 `/api/v1/admin/**` 보안 규칙을 그대로 따른다.

## 모듈 경계

- `app-main/main/admin/stats/controller`: HTTP validation
- `dto`: request/overview response/daily row/queue summary
- `service`: KST range resolution, summary/series/queue 조합
- `repository`: date spine, time-column별 group aggregation, queue snapshot

읽기 전용 transaction만 사용하며 `generate_series` 또는 동등한 date spine으로 0값 날짜를 보장한다. accepted rate는 사람 답변의 accepted/total을 같은 range에서 계산하며, 일별 rate를 평균하지 않는다.

## 검증

- KST 경계, default range, 366일 초과, zero-fill, 각 event time column, queue enum count
- controller 400/401/403 계약
- 구현 후 `docs/admin-dashboard-api-contract.md`와 `code/api/API-SPEC.md`를 AS-BUILT로 동기화

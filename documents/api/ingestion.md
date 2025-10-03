# 데이터 수집 API

SEC 데이터 수집을 제어하고 모니터링하는 API 그룹입니다.

## 엔드포인트

### POST /api/ingest/refresh
SEC 데이터 수집을 트리거합니다.

**Request Body:**
```json
{
  "symbols": ["AAPL", "NVDA", "MSFT"],  // 선택적: 특정 종목만 수집
  "mode": "latest"                      // "today" | "latest"
}
```

**Response:**
```json
{
  "logId": "uuid",
  "totalProcessed": 3,
  "totalInserted": 2244,
  "totalSkipped": 0,
  "warnings": [],
  "status": "completed"
}
```

**Mode 설명:**
- `today`: 오늘자 daily-index 기반 수집
- `latest`: 각 종목의 최신 submissions 수집

### GET /api/ingest/status/{requestId}
특정 수집 작업의 상태를 조회합니다.

**Response:**
```json
{
  "id": "uuid",
  "requestTimestamp": "2025-10-03T15:45:39.381Z",
  "mode": "latest",
  "symbols": ["AAPL", "NVDA", "MSFT"],
  "totalProcessed": 3,
  "totalInserted": 2244,
  "totalSkipped": 0,
  "completedAt": "2025-10-03T15:45:41.843Z",
  "status": "completed"
}
```

### GET /api/ingest/status
최근 수집 작업 목록을 조회합니다.

**Query Parameters:**
- `limit`: 반환할 항목 수 (기본값: 10)

### GET /api/ingest/health
수집 서비스 상태를 확인합니다.

**Response:**
```
Ingestion service is healthy
```

## 사용 예시

```bash
# 특정 종목 수집
curl -X POST http://localhost:8080/api/ingest/refresh \
  -H "Content-Type: application/json" \
  -d '{"symbols": ["AAPL"], "mode": "latest"}'

# 전체 종목 오늘자 수집
curl -X POST http://localhost:8080/api/ingest/refresh \
  -H "Content-Type: application/json" \
  -d '{"mode": "today"}'

# 수집 상태 확인
curl http://localhost:8080/api/ingest/status
```

## 주의사항

- SEC Fair Access 규정에 따라 10 rps 제한 적용
- 대용량 수집 시 완료까지 시간 소요 가능
- symbols가 없으면 캐시된 모든 종목 대상
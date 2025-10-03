# API 사용 예시

실제 사용 시나리오별 API 호출 예시입니다.

## 시나리오 1: 새로운 종목 추가 및 데이터 수집

```bash
# 1. 티커 확인
curl "http://localhost:8080/api/ticker/resolve?symbol=TSLA"

# 2. 해당 종목 최신 데이터 수집
curl -X POST http://localhost:8080/api/ingest/refresh \
  -H "Content-Type: application/json" \
  -d '{"symbols": ["TSLA"], "mode": "latest"}'

# 3. 수집 상태 확인
curl "http://localhost:8080/api/ingest/status"

# 4. 수집된 데이터 확인
curl "http://localhost:8080/api/filings/TSLA/latest?limit=10"
```

## 시나리오 2: 일일 모니터링 워크플로우

```bash
# 1. 모든 종목 오늘자 업데이트
curl -X POST http://localhost:8080/api/ingest/refresh \
  -H "Content-Type: application/json" \
  -d '{"mode": "today"}'

# 2. 최근 Form 4 (임원 거래) 확인
curl "http://localhost:8080/api/filings/recent?forms=4&days=1&limit=20"

# 3. 특정 종목 최신 동향
curl "http://localhost:8080/api/filings/AAPL/recent?days=7"
```

## 시나리오 3: 특정 이벤트 추적

```bash
# 1. 최근 8-K (중요 사건) 파일링
curl "http://localhost:8080/api/filings/recent?forms=8-K&days=3&limit=30"

# 2. 분기 보고서 업데이트 확인
curl "http://localhost:8080/api/filings/NVDA/latest?form=10-Q&limit=2"

# 3. 기관투자자 보유 변화
curl "http://localhost:8080/api/filings/recent?forms=13F-HR&days=45&limit=10"
```

## 시나리오 4: 데이터 품질 관리

```bash
# 1. 시스템 상태 확인
curl "http://localhost:8080/api/ingest/health"

# 2. 수집 통계 확인
curl "http://localhost:8080/api/filings/stats/AAPL"
curl "http://localhost:8080/api/filings/stats/NVDA"
curl "http://localhost:8080/api/filings/stats/MSFT"

# 3. 티커 매핑 갱신 (필요시)
curl -X POST "http://localhost:8080/api/ticker/refresh"

# 4. 최근 수집 이력 확인
curl "http://localhost:8080/api/ingest/status"
```

## 시나리오 5: 대량 데이터 처리

```bash
# 1. 다중 종목 배치 수집
curl -X POST http://localhost:8080/api/ingest/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "symbols": ["AAPL", "NVDA", "MSFT", "GOOGL", "AMZN", "META", "TSLA"],
    "mode": "latest"
  }'

# 2. 진행 상황 모니터링
TASK_ID=$(curl -s -X POST ... | jq -r '.logId')
curl "http://localhost:8080/api/ingest/status/$TASK_ID"

# 3. 완료 후 결과 확인
curl "http://localhost:8080/api/ingest/status" | head -1
```

## 에러 처리 예시

```bash
# 잘못된 티커 시도
curl "http://localhost:8080/api/ticker/resolve?symbol=INVALID"
# 응답: 404 Not Found

# 존재하지 않는 수집 작업 조회
curl "http://localhost:8080/api/ingest/status/invalid-uuid"
# 응답: 404 Not Found

# 잘못된 파라미터
curl "http://localhost:8080/api/filings/AAPL/latest?limit=abc"
# 응답: 400 Bad Request
```

## 응답 시간 최적화

```bash
# 큰 데이터셋 조회 시 페이지네이션 활용
curl "http://localhost:8080/api/filings/AAPL/latest?limit=50"

# 특정 Form만 필터링하여 부하 감소
curl "http://localhost:8080/api/filings/recent?forms=10-K,10-Q&days=90"

# 캐시 활용을 위한 반복 조회 최소화
curl "http://localhost:8080/api/ticker/list" # 캐시된 결과 활용
```

## 자동화 스크립트 예시

```bash
#!/bin/bash
# daily_update.sh - 일일 자동 업데이트 스크립트

echo "Starting daily SEC data update..."

# 오늘자 데이터 수집
RESPONSE=$(curl -s -X POST http://localhost:8080/api/ingest/refresh \
  -H "Content-Type: application/json" \
  -d '{"mode": "today"}')

LOG_ID=$(echo $RESPONSE | jq -r '.logId')
echo "Started ingestion job: $LOG_ID"

# 완료 대기
while true; do
  STATUS=$(curl -s "http://localhost:8080/api/ingest/status/$LOG_ID" | jq -r '.status')
  if [ "$STATUS" = "completed" ]; then
    echo "Ingestion completed successfully"
    break
  elif [ "$STATUS" = "failed" ]; then
    echo "Ingestion failed"
    exit 1
  fi
  echo "Status: $STATUS, waiting..."
  sleep 10
done

echo "Daily update completed"
```
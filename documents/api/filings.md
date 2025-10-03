# 파일링 조회 API

수집된 SEC 파일링 데이터를 조회하고 검색하는 API입니다.

## 엔드포인트

### GET /api/filings/{symbol}/latest
특정 종목의 최신 파일링을 조회합니다.

**Path Parameters:**
- `symbol`: 티커 심볼

**Query Parameters:**
- `form`: 파일링 유형 필터 (선택적)
- `limit`: 반환할 항목 수 (기본값: 10)

**Response:**
```json
[
  {
    "id": 3,
    "cik": "0000320193",
    "accessionNo": "0002050912-25-000006",
    "form": "4",
    "filedAt": "2025-09-30T00:00:00",
    "periodEnd": "2025-09-28",
    "primaryDocUrl": "https://www.sec.gov/Archives/...",
    "source": "submissions",
    "createdAt": "2025-10-03T15:45:40.194Z",
    "issuer": {
      "cik": "0000320193",
      "ticker": "AAPL",
      "name": "Apple Inc."
    }
  }
]
```

### GET /api/filings/{symbol}/recent
특정 기간 내 파일링을 조회합니다.

**Query Parameters:**
- `form`: 파일링 유형 필터 (선택적)
- `days`: 조회 기간 (기본값: 7일)

### GET /api/filings/recent
전체 종목의 최근 파일링을 조회합니다.

**Query Parameters:**
- `forms`: 파일링 유형 배열 (선택적)
- `days`: 조회 기간 (기본값: 1일)
- `limit`: 반환할 항목 수 (기본값: 50)

### GET /api/filings/search
파일링을 검색합니다.

**Query Parameters:**
- `symbol`: 티커 심볼 (선택적)
- `form`: 파일링 유형 (선택적)
- `accessionNo`: 접수번호 (선택적)

### GET /api/filings/stats/{symbol}
특정 종목의 파일링 통계를 조회합니다.

**Response:**
```json
{
  "symbol": "AAPL",
  "cik": "0000320193",
  "totalFilings": 1245
}
```

## DeltaMap API

### POST /api/filings/{filingId}/analyze-delta
파일링 변화 분석을 비동기로 트리거합니다.

**Response:**
```json
{
  "jobId": "abc-123",
  "status": "processing"
}
```

### GET /api/filings/{filingId}/sections
파일링의 특정 섹션 텍스트를 조회합니다.

**Query Parameters:**
- `section`: 조회할 섹션 (예: Item1A, Item7)

**Response:**
```json
{
  "sections": [
    {
      "section": "Item1A",
      "text": "...",
      "charCount": 15000,
      "hash": "abc123..."
    }
  ]
}
```

### GET /api/filings/{filingId}/deltas
파일링의 변화 분석 결과를 조회합니다.

**Query Parameters:**
- `section`: 섹션 필터 (선택적)

**Response:**
```json
{
  "current": {
    "filingId": 123,
    "form": "10-Q",
    "periodEnd": "2024-09-30"
  },
  "previous": {
    "filingId": 122,
    "form": "10-Q",
    "periodEnd": "2024-06-30"
  },
  "deltas": [
    {
      "operation": "INSERT",
      "snippet": "...",
      "score": 0.85,
      "context": "..."
    }
  ]
}
```

### GET /api/filings/{filingId}/xbrl-heatmap
XBRL 메트릭 변화율 히트맵 데이터를 조회합니다.

**Response:**
```json
{
  "metrics": [
    {
      "tag": "Revenue",
      "current": 100000000,
      "previous": 95000000,
      "change": 5.26,
      "basis": "QoQ",
      "zscore": 1.2
    }
  ]
}
```

### GET /api/tickers/{symbol}/delta-summary
특정 티커의 최신 변화 요약을 조회합니다.

**Response:**
```json
{
  "latestFiling": {
    "id": 123,
    "form": "10-Q",
    "periodEnd": "2024-09-30"
  },
  "changeBadges": [
    {
      "type": "section",
      "label": "MD&A 3문단 변경",
      "severity": "medium"
    },
    {
      "type": "xbrl",
      "label": "매출 +12% 급증",
      "severity": "high"
    }
  ]
}
```

## 사용 예시

```bash
# AAPL 최신 파일링 5건
curl "http://localhost:8080/api/filings/AAPL/latest?limit=5"

# AAPL 최근 30일 Form 10-Q만
curl "http://localhost:8080/api/filings/AAPL/recent?form=10-Q&days=30"

# 전체 최근 Form 4 파일링
curl "http://localhost:8080/api/filings/recent?forms=4&days=3&limit=20"

# 접수번호로 검색
curl "http://localhost:8080/api/filings/search?accessionNo=0002050912-25-000006"

# AAPL 파일링 통계
curl "http://localhost:8080/api/filings/stats/AAPL"
```

## 파일링 유형

지원하는 주요 SEC 파일링 유형:

- **10-K**: 연간 보고서
- **10-Q**: 분기 보고서
- **8-K**: 중요 사건 보고서
- **4**: 임원 거래 신고
- **13F-HR**: 기관투자자 보유 신고
- **13D/13G**: 5% 이상 보유 신고

## 데이터 구조

각 파일링은 다음 정보를 포함:

- **메타데이터**: CIK, 접수번호, 제출일
- **원문 링크**: SEC EDGAR 직접 링크
- **발행자 정보**: 회사명, 티커, 산업 분류
- **수집 정보**: 소스, 수집 시간

## 성능 최적화

- 인덱스 최적화로 빠른 조회
- 페이지네이션 지원
- 캐싱을 통한 응답 속도 향상
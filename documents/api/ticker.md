# 티커 관리 API

티커 심볼과 SEC CIK 간의 매핑을 관리하고 발행자 정보를 제공하는 API입니다.

## 엔드포인트

### GET /api/ticker/resolve
티커 심볼을 CIK로 해결합니다.

**Query Parameters:**
- `symbol`: 해결할 티커 심볼 (필수)

**Response:**
```json
{
  "symbol": "AAPL",
  "cik": "0000320193",
  "name": "Apple Inc.",
  "exchange": "NASDAQ"
}
```

### GET /api/ticker/list
캐시된 모든 티커 목록을 반환합니다.

**Response:**
```json
[
  {
    "cik": "0000320193",
    "ticker": "AAPL",
    "name": "Apple Inc.",
    "exchange": "Nasdaq",
    "sic": "3571",
    "industry": "Electronic Computers",
    "sector": null,
    "updatedAt": "2025-10-03T15:45:40.200Z"
  }
]
```

### POST /api/ticker/refresh
SEC에서 최신 티커 매핑을 가져와 캐시를 갱신합니다.

**Response:**
```
Ticker mappings refreshed successfully
```

### GET /api/ticker/{symbol}
특정 티커의 발행자 정보를 조회합니다.

**Response:**
```json
{
  "cik": "0000320193",
  "ticker": "AAPL",
  "name": "Apple Inc.",
  "exchange": "Nasdaq",
  "sic": "3571",
  "industry": "Electronic Computers",
  "sector": null,
  "updatedAt": "2025-10-03T15:45:40.200Z"
}
```

## 사용 예시

```bash
# 티커 해결
curl "http://localhost:8080/api/ticker/resolve?symbol=AAPL"

# 전체 티커 목록
curl http://localhost:8080/api/ticker/list

# 티커 매핑 갱신
curl -X POST http://localhost:8080/api/ticker/refresh

# 특정 발행자 정보
curl http://localhost:8080/api/ticker/AAPL
```

## 데이터 소스

- **SEC company_tickers.json**: 기본 티커-CIK 매핑
- **SEC company_tickers_exchange.json**: 거래소별 상세 정보
- **Company Submissions**: 발행자 메타데이터

## 캐싱 정책

- 티커 매핑: 24시간 캐시
- 데이터베이스 우선 조회 후 SEC API 호출
- 자동 매핑 업데이트 및 저장
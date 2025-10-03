# API 확장 가이드

현재 API의 확장 포인트와 향후 개발 방향을 안내합니다.

## 1. 인증 및 권한 (Authentication & Authorization)

### 현재 상태
- 인증 없음 (오픈 액세스)
- 모든 엔드포인트 공개

### 확장 계획
```yaml
# JWT 기반 인증 추가 예시
POST /api/auth/login
GET /api/auth/me
POST /api/auth/refresh

# 헤더 추가
Authorization: Bearer <jwt-token>

# 권한 레벨
- READ_ONLY: 조회만 가능
- DATA_COLLECT: 수집 트리거 가능
- ADMIN: 전체 관리 권한
```

## 2. 실시간 알림 (Real-time Notifications)

### WebSocket 엔드포인트
```javascript
// 실시간 수집 상태 구독
ws://localhost:8080/ws/ingestion

// 새 파일링 알림
ws://localhost:8080/ws/filings/{symbol}

// 시스템 상태 모니터링
ws://localhost:8080/ws/system
```

### Server-Sent Events
```bash
# 스트리밍 엔드포인트
curl "http://localhost:8080/api/stream/ingestion"
curl "http://localhost:8080/api/stream/filings?symbols=AAPL,NVDA"
```

## 3. 고급 분석 API (Analytics)

### 델타 분석
```bash
# 파일링 변화 분석
GET /api/analytics/delta/{symbol}?from=10-Q&to=10-Q

# XBRL 메트릭 비교
GET /api/analytics/metrics/{symbol}/compare?periods=4
```

### 임원 거래 분석
```bash
# 임원 거래 패턴
GET /api/analytics/insider/{symbol}/patterns

# 클러스터 매수/매도 감지
GET /api/analytics/insider/{symbol}/clusters?days=30
```

### 기관투자자 플로우
```bash
# 기관 보유 변화
GET /api/analytics/institutional/{symbol}/flow

# Top 홀더 변화
GET /api/analytics/institutional/{symbol}/top-holders
```

## 4. 데이터 내보내기 (Export)

### 형식별 내보내기
```bash
# CSV 내보내기
GET /api/export/filings/{symbol}.csv?form=10-K&years=3

# Excel 내보내기
GET /api/export/metrics/{symbol}.xlsx

# PDF 리포트
GET /api/export/report/{symbol}.pdf?template=quarterly
```

### 대량 내보내기
```bash
# 비동기 내보내기 작업
POST /api/export/bulk
{
  "symbols": ["AAPL", "NVDA", "MSFT"],
  "forms": ["10-K", "10-Q"],
  "format": "csv",
  "compression": "zip"
}

# 내보내기 상태 확인
GET /api/export/status/{exportId}

# 파일 다운로드
GET /api/export/download/{exportId}
```

## 5. 검색 및 필터링 고도화

### 전문 검색
```bash
# 텍스트 검색 (Elasticsearch 연동)
GET /api/search/filings?q="artificial intelligence"&forms=8-K

# 메트릭 기반 검색
GET /api/search/companies?revenue_growth=>0.2&sector=technology
```

### 고급 필터
```bash
# 복합 조건 필터
POST /api/filings/filter
{
  "symbols": ["AAPL", "NVDA"],
  "forms": ["10-K", "10-Q"],
  "dateRange": {
    "from": "2024-01-01",
    "to": "2024-12-31"
  },
  "metrics": {
    "revenue": {"min": 1000000000},
    "growth_rate": {"min": 0.1}
  }
}
```

## 6. API 버전 관리

### 버전 전략
```bash
# URL 기반 버전 관리
GET /api/v1/filings/{symbol}/latest
GET /api/v2/filings/{symbol}/latest

# 헤더 기반 버전 관리
Accept: application/vnd.stockdelta.v2+json
```

### 하위 호환성
- v1: 현재 API 유지
- v2: 고급 기능 추가
- 단계적 마이그레이션 지원

## 7. 성능 최적화

### 캐싱 전략 확장
```yaml
# Redis 캐시 레이어
- L1: Application Cache (30s)
- L2: Redis Cache (5min)
- L3: Database (persistent)

# CDN 캐시 (정적 데이터)
- Ticker mappings: 24h
- Historical filings: 7d
```

### 비동기 처리 확장
```bash
# 큐 기반 처리
POST /api/async/ingest/refresh
# 응답: { "taskId": "uuid", "status": "queued" }

GET /api/async/status/{taskId}
# 응답: { "status": "processing", "progress": 45 }
```

## 8. 모니터링 및 메트릭

### 상세 메트릭
```bash
# API 성능 메트릭
GET /api/metrics/performance

# 사용량 통계
GET /api/metrics/usage

# 시스템 상태
GET /api/metrics/system
```

### 알람 설정
```bash
# 임계값 기반 알람
POST /api/alerts/rules
{
  "name": "High error rate",
  "condition": "error_rate > 0.05",
  "actions": ["email", "slack"]
}
```

## 9. 배치 작업 (Batch Operations)

### 스케줄링
```bash
# 크론 작업 관리
POST /api/scheduler/jobs
{
  "name": "daily_ingestion",
  "schedule": "0 2 * * *",
  "task": "ingest_all",
  "enabled": true
}

GET /api/scheduler/jobs
PUT /api/scheduler/jobs/{jobId}/enable
DELETE /api/scheduler/jobs/{jobId}
```

## 10. 플러그인 아키텍처

### 커스텀 파서
```java
// 새로운 파일링 타입 지원
@Component
public class CustomFilingParser implements FilingParser {
    @Override
    public boolean supports(String formType) {
        return "CUSTOM-FORM".equals(formType);
    }

    @Override
    public ParsedFiling parse(String content) {
        // 커스텀 파싱 로직
    }
}
```

### 데이터 소스 확장
```java
// 새로운 데이터 소스 추가
@Component
public class AlternativeDataSource implements DataSource {
    @Override
    public List<Filing> fetchFilings(String symbol) {
        // 대안 데이터 소스에서 수집
    }
}
```

이러한 확장을 통해 시스템을 점진적으로 발전시킬 수 있습니다.
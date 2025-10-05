# Arelle + XULE 정규화 엔진 마이그레이션 가이드

## 📋 개요

이 가이드는 기존 XBRL 파싱 방식에서 **Arelle + XULE 기반 자체 정규화 엔진**으로 전환하는 과정을 설명합니다.

### 주요 변경사항

- ✅ **Python FastAPI 정규화 서비스** 추가 (Arelle 기반)
- ✅ **FAC (Fundamental Accounting Concepts) 매핑** 시스템 구축
- ✅ **DQC (Data Quality Committee) 검증** 통합
- ✅ **새로운 DB 스키마**: normalized_financials, xbrl_tag_mapping, data_quality_validations
- ✅ **기존 xbrl_facts, xbrl_metrics 테이블 대체**

---

## 🏗️ 아키텍처

```
┌─────────────────┐
│  SEC EDGAR API  │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Java Backend (Spring Boot)        │
│  ┌───────────────────────────────┐  │
│  │ NormalizationPipelineService  │  │
│  └──────────┬────────────────────┘  │
│             │                        │
│             ▼                        │
│  ┌───────────────────────────────┐  │
│  │ ArelleNormalizationClient     │  │
│  │ (HTTP Client)                 │  │
│  └──────────┬────────────────────┘  │
└─────────────┼────────────────────────┘
              │
              │ HTTP
              ▼
┌─────────────────────────────────────┐
│  Normalization Service (Python)     │
│  ┌───────────────────────────────┐  │
│  │  Arelle XBRL Processor        │  │
│  │  + FAC Mapper                 │  │
│  │  + DQC Validator              │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│  PostgreSQL Database                │
│  - normalized_financials            │
│  - xbrl_tag_mapping                 │
│  - data_quality_validations         │
│  - normalized_metrics               │
└─────────────────────────────────────┘
```

---

## 🚀 설치 및 실행

### 1. DB 마이그레이션

```bash
# PostgreSQL에 접속
psql -U stock_user -d stock_delta

# 마이그레이션 스크립트 실행
\i scripts/migration_to_normalized.sql
```

**주요 작업:**
- ✅ 4개 신규 테이블 생성 (normalized_financials, xbrl_tag_mapping, etc.)
- ✅ 인덱스 생성
- ✅ 핵심 FAC 매핑 데이터 로드 (Revenue, Assets, Liabilities 등)

### 2. Python 정규화 서비스 빌드 및 실행

```bash
# Docker Compose로 전체 스택 실행
docker-compose up -d normalization-service

# 또는 로컬에서 개발 모드로 실행
cd normalization-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 5000
```

**Health Check:**
```bash
curl http://localhost:5000/health
```

**Expected Response:**
```json
{
  "status": "healthy",
  "arelle_version": "2.20.3",
  "timestamp": "2025-10-05T..."
}
```

### 3. Java Backend 업데이트 및 재시작

```bash
# 전체 스택 재빌드
docker-compose down
docker-compose up -d --build

# 또는 API만 재시작
docker-compose restart api
```

---

## 📊 새로운 API 엔드포인트

### 1. Filing 정규화 트리거

```http
POST /api/deltamap/filings/{filingId}/normalize?calculateMetrics=true
```

**Response:**
```json
{
  "filingId": 123,
  "normalizedConceptCount": 245,
  "errorCount": 0,
  "warningCount": 3,
  "status": "completed",
  "processingTimeMs": 12500
}
```

### 2. 정규화된 히트맵 조회

```http
GET /api/deltamap/filings/{filingId}/normalized-heatmap
```

**Response:**
```json
{
  "filingId": 123,
  "rows": [
    {
      "metric": "Revenue",
      "values": {
        "Abs": 383285000000,
        "QoQ": 15.2,
        "YoY": 8.7
      },
      "zScore": 1.52
    },
    {
      "metric": "NetIncome",
      "values": {
        "Abs": 96995000000,
        "QoQ": 12.3,
        "YoY": 5.9
      },
      "zScore": 1.23
    }
  ]
}
```

### 3. DQC 검증 결과 조회

```http
GET /api/deltamap/filings/{filingId}/data-quality
```

**Response:**
```json
{
  "filingId": 123,
  "errorCount": 0,
  "warningCount": 2,
  "infoCount": 5,
  "errors": [],
  "warnings": [
    {
      "ruleId": "DQC_0001",
      "severity": "warning",
      "message": "Assets reported but Liabilities missing",
      "affectedConcept": "Assets/Liabilities"
    }
  ],
  "info": [...]
}
```

### 4. 정규화 통계 조회

```http
GET /api/deltamap/filings/{filingId}/normalization-stats
```

**Response:**
```json
{
  "filingId": 123,
  "normalizedConceptCount": 245,
  "distinctConcepts": ["Revenue", "Assets", "Liabilities", "NetIncome", ...],
  "errorCount": 0,
  "warningCount": 2,
  "hasErrors": false
}
```

---

## 🔄 워크플로우 예시

### 시나리오: AAPL 10-K 정규화 및 분석

```bash
# 1. Filing 조회
curl http://localhost:8080/api/filings?cik=0000320193&form=10-K | jq

# Response에서 filingId 확인 (예: 456)

# 2. 정규화 트리거
curl -X POST http://localhost:8080/api/deltamap/filings/456/normalize?calculateMetrics=true | jq

# 3. 정규화 상태 확인
curl http://localhost:8080/api/deltamap/filings/456/normalization-stats | jq

# 4. 히트맵 데이터 조회
curl http://localhost:8080/api/deltamap/filings/456/normalized-heatmap | jq

# 5. DQC 검증 결과 확인
curl http://localhost:8080/api/deltamap/filings/456/data-quality | jq
```

---

## 🧪 테스트

### Python 서비스 테스트

```bash
cd normalization-service

# Arelle이 설치되었는지 확인
python -c "import arelle; print(arelle.__version__)"

# FastAPI 앱 테스트
curl http://localhost:5000/concepts | jq

# 정규화 테스트 (AAPL 10-K 예시)
curl -X POST http://localhost:5000/normalize \
  -H "Content-Type: application/json" \
  -d '{
    "filing_url": "https://www.sec.gov/Archives/edgar/data/320193/000032019323000106/aapl-20230930_htm.xml",
    "cik": "0000320193"
  }' | jq
```

### Java 서비스 테스트

```bash
# 통합 테스트 (Docker 환경에서)
docker-compose exec api curl http://normalization-service:5000/health

# End-to-end 테스트
# 1. DB에서 Filing ID 확인
docker-compose exec postgres psql -U stock_user -d stock_delta \
  -c "SELECT id, cik, form, filed_at FROM filings WHERE form='10-K' LIMIT 1;"

# 2. 정규화 실행
curl -X POST http://localhost:8080/api/deltamap/filings/{filing_id}/normalize
```

---

## 📦 주요 구성 요소

### Python 서비스 (normalization-service/)

| 파일 | 설명 |
|------|------|
| `app/main.py` | FastAPI 메인 앱, 엔드포인트 정의 |
| `app/arelle_processor.py` | Arelle XBRL 프로세서 래퍼 |
| `app/fac_mapper.py` | FAC 개념 매핑 로직 |
| `app/models.py` | Pydantic 데이터 모델 |
| `Dockerfile` | Python 서비스 컨테이너 이미지 |
| `requirements.txt` | Python 의존성 |

### Java 서비스 (common/src/main/java/)

| 컴포넌트 | 설명 |
|---------|------|
| `entity/NormalizedFinancial` | 정규화된 재무 데이터 Entity |
| `entity/NormalizedMetric` | QoQ/YoY 메트릭 Entity |
| `entity/XbrlTagMapping` | XBRL → FAC 매핑 테이블 |
| `entity/DataQualityValidation` | DQC 검증 결과 |
| `client/ArelleNormalizationClient` | Python 서비스 HTTP 클라이언트 |
| `service/NormalizationPipelineService` | 정규화 파이프라인 오케스트레이션 |
| `service/NormalizedMetricsService` | 메트릭 계산 서비스 |
| `service/FacMappingService` | FAC 매핑 관리 서비스 |

---

## 🗄️ 데이터베이스 스키마

### normalized_financials
정규화된 재무 데이터 (FAC 기반)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGSERIAL | Primary Key |
| filing_id | BIGINT | Filing 참조 |
| concept | VARCHAR(100) | FAC 개념 (Revenue, Assets 등) |
| value | NUMERIC(20,2) | 값 |
| period_type | VARCHAR(20) | instant / duration |
| quality_score | DECIMAL(3,2) | 데이터 품질 점수 (0.0~1.0) |
| source | VARCHAR(50) | 정규화 소스 |
| start_date, end_date | DATE | 기간 |

### xbrl_tag_mapping
XBRL 태그 → FAC 개념 매핑

| 컬럼 | 타입 | 설명 |
|------|------|------|
| source_tag | VARCHAR(255) | 원본 XBRL 태그 |
| taxonomy | VARCHAR(100) | us-gaap, ifrs-full 등 |
| fundamental_concept | VARCHAR(100) | FAC 개념 |
| confidence_score | DECIMAL(3,2) | 매핑 신뢰도 |
| rule_source | VARCHAR(100) | fac-standard, pattern-match 등 |

### normalized_metrics
계산된 메트릭 (QoQ, YoY)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| filing_id | BIGINT | Filing 참조 (PK) |
| metric | VARCHAR(100) | FAC 개념 (PK) |
| basis | VARCHAR(10) | QoQ, YoY, Abs (PK) |
| value | NUMERIC(20,2) | 계산된 값 |
| quality_score | DECIMAL(3,2) | 품질 점수 |

---

## 🔧 환경 변수

### docker-compose.yml

```yaml
normalization-service:
  environment:
    - ARELLE_CACHE_DIR=/cache  # Arelle 캐시 디렉토리

api:
  environment:
    - NORMALIZATION_SERVICE_URL=http://normalization-service:5000
```

### application.properties (Spring)

```properties
# Normalization Service URL
normalization.service.url=http://localhost:5000

# WebClient timeout
spring.webflux.timeout.connect=30000
spring.webflux.timeout.read=300000
```

---

## 🐛 트러블슈팅

### 1. Python 서비스가 시작되지 않음

**문제:** `ModuleNotFoundError: No module named 'arelle'`

**해결:**
```bash
docker-compose logs normalization-service
docker-compose exec normalization-service pip install arelle-release
docker-compose restart normalization-service
```

### 2. Normalization 타임아웃

**문제:** `WebClient timeout`

**해결:** application.properties 타임아웃 증가
```properties
spring.webflux.timeout.read=600000  # 10분
```

### 3. DB 마이그레이션 오류

**문제:** `relation "normalized_financials" already exists`

**해결:**
```sql
-- 테이블 삭제 후 재생성
DROP TABLE IF EXISTS normalized_metrics CASCADE;
DROP TABLE IF EXISTS data_quality_validations CASCADE;
DROP TABLE IF EXISTS normalized_financials CASCADE;
DROP TABLE IF EXISTS xbrl_tag_mapping CASCADE;

-- 마이그레이션 재실행
\i scripts/migration_to_normalized.sql
```

### 4. FAC 매핑이 작동하지 않음

**문제:** 모든 concept가 null로 표시됨

**해결:** FAC 매핑 데이터 로드
```java
// FacMappingService에서
facMappingService.loadCoreMappings();
```

---

## 📈 성능 최적화

### 1. Arelle 캐싱
- Docker 볼륨 `arelle_cache`가 마운트되어 있는지 확인
- 반복 처리 시 캐시 활용으로 속도 향상

### 2. 병렬 처리
```java
// 여러 Filing 동시 정규화
Flux.fromIterable(filingIds)
    .flatMap(filingId -> normalizationPipelineService.processFiling(filingId))
    .subscribe();
```

### 3. DB 인덱스 확인
```sql
-- 성능 확인
EXPLAIN ANALYZE
SELECT * FROM normalized_financials
WHERE filing_id = 123 AND concept = 'Revenue';
```

---

## 🎯 다음 단계

### 단기 (완료 후)
- [ ] 기존 XbrlFact, XbrlMetric 테이블 제거
- [ ] Frontend API 엔드포인트 업데이트 (/xbrl-heatmap → /normalized-heatmap)
- [ ] 통합 테스트 작성

### 중기 (1-2개월)
- [ ] 전체 Filing 재처리 스크립트 작성
- [ ] DQC 규칙 확장 (현재 기본 검증만 구현)
- [ ] XULE normalization rules 추가

### 장기 (3개월+)
- [ ] 머신러닝 기반 커스텀 태그 매핑
- [ ] 실시간 정규화 파이프라인
- [ ] 성능 모니터링 대시보드

---

## 📚 참고 자료

- [Arelle Documentation](https://arelle.org/arelle/documentation/)
- [XBRL US FAC Taxonomy](http://xbrl.squarespace.com/fundamental-accounting-concept/)
- [DQC Rules](https://xbrl.us/dqc-rules/)
- [SEC EDGAR XBRL](https://www.sec.gov/structureddata/osd-inline-xbrl.html)

---

## ✅ 체크리스트

### 구현 완료
- [x] DB 스키마 마이그레이션 SQL
- [x] Python 정규화 서비스 (FastAPI + Arelle)
- [x] Java Entity 및 Repository
- [x] ArelleNormalizationClient
- [x] NormalizationPipelineService
- [x] NormalizedMetricsService
- [x] FacMappingService
- [x] Controller 엔드포인트 추가
- [x] Docker Compose 통합

### 남은 작업
- [ ] 기존 코드 제거 (XbrlFact, XbrlMetricsService)
- [ ] Frontend 업데이트
- [ ] 통합 테스트
- [ ] 문서화 완료

---

**작성일:** 2025-10-05
**버전:** 1.0.0

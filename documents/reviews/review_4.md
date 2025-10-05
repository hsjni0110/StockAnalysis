# XBRL 데이터 파싱 안정성 문제 분석 및 해결 전략

## 문제 인식

현재 구현한 SEC XBRL 파싱 방식은 다음과 같은 근본적인 한계를 가지고 있습니다:

1. **회사별 XBRL 구조 차이**: 모든 회사가 동일한 태그와 구조를 사용하지 않음
2. **Custom Extension Tags**: 평균 13.5%의 태그가 회사별 커스텀 태그 (범위: 1.9% ~ 42.7%)
3. **데이터 품질 문제**: 스케일 오류, 부적절한 커스텀 태그 사용, 일관성 결여
4. **파싱 로직의 불안정성**: 하나의 파싱 로직으로 모든 회사의 데이터를 동일하게 처리할 수 없음

---

## 심층 리서치 결과

### 1. XBRL 데이터 품질 문제의 본질

#### 회사별 태그 사용의 불일치
- **US GAAP Taxonomy**: 20,000개 이상의 표준 태그 제공
- **실제 사용 패턴**:
  - 약 6,500개 SEC 공시 기업 중 90%가 단 10개의 보고 스타일만 사용
  - 동일한 회계 항목에 대해 여러 개의 서로 다른 태그 사용 가능
  - 표준 태그가 있음에도 불구하고 커스텀 태그 생성

#### 주요 데이터 품질 이슈 (SEC 확인)
1. **Scaling Errors**: 같은 데이터를 서로 다른 스케일로 표시
   - 예: Public Float, Common Shares Outstanding

2. **Inappropriate Custom Tags**: 표준 태그가 존재함에도 커스텀 태그 사용

3. **태그 일관성 결여**:
   - 분기 간 동일 항목에 대해 다른 태그 사용
   - 회사 간 비교 불가능

4. **컨텍스트 및 Dimension 불일치**

### 2. 현재 파싱 방식의 문제점

review_3.md에서 구현한 방식:
```java
// /api/xbrl/companyfacts/CIK{cik}.json 직접 파싱
XbrlFact fact = new XbrlFact();
fact.setTaxonomy(taxonomy);
fact.setTag(tag);
fact.setLabel(label);
fact.setValue(value);
```

**문제**:
- 태그 매핑 로직 없음 → 커스텀 태그 처리 불가
- 데이터 검증만으로는 구조적 차이 해결 불가
- 회사 간 비교 가능성 보장 못함
- 메트릭 계산 시 태그 불일치로 데이터 누락 가능

---

## 해결 전략

### 전략 A: XBRL 정규화(Normalization) 레이어 구축 (권장)

SEC XBRL US의 **Standardized Statement Taxonomy (XUSSS)** 활용

#### 1. XBRL-US Normalized Data 사용

**개요**:
- XBRL US가 FASB, DQC와 협업하여 개발한 정규화 프레임워크
- **XULE (XBRL Rules and Query Language)** 기반 정규화 엔진
- 모든 SEC 공시를 정규화된 뷰로 변환

**접근 방법**:

##### Option A-1: XBRL US Public Database API 사용
```
GET https://xbrl.us/api/v1/report?report.source-name=NORM&entity.cik=0000320193
```

**장점**:
- 정규화된 데이터 직접 접근
- 회사 간 완벽한 비교 가능성
- 커스텀 태그 자동 매핑

**단점**:
- API 접근 권한 및 비용 확인 필요
- XBRL US 커뮤니티/멤버십 요구 가능성

##### Option A-2: Arelle + XULE 자체 구축
```bash
# Arelle 설치 및 DQC Rules + XULE Normalization 플러그인
pip install arelle

# XULE Normalization 실행
arelle --plugins xule --xule-run normalization_rules.xule \
       --xule-filing instance.xml --xule-output normalized.json
```

**구현 접근**:
1. Arelle XBRL 프로세서를 Java에서 호출 (ProcessBuilder 또는 REST Wrapper)
2. XULE normalization ruleset 적용
3. JSON 출력을 파싱하여 DB 저장

**장점**:
- 완전한 자체 제어
- XBRL US DQC Rules (166개 검증 규칙) 동시 적용 가능
- 무료 오픈소스

**단점**:
- Python 의존성 (Arelle)
- 초기 구축 복잡도 높음
- 유지보수 필요

#### 2. Fundamental Accounting Concepts (FAC) 매핑

Charles Hoffman의 **FAC Taxonomy** 활용

**개념**:
- 회계의 근본 개념(Assets, Liabilities, Revenue 등)을 정의
- US GAAP 태그를 FAC로 매핑하는 룰셋 제공
- 회사별 태그 차이를 FAC로 정규화

**매핑 예시**:
```
us-gaap:Revenues → FAC:Revenue
us-gaap:SalesRevenueNet → FAC:Revenue
us-gaap:RevenueFromContractWithCustomerExcludingAssessedTax → FAC:Revenue
{custom}:TotalRevenues → FAC:Revenue
```

**구현**:
1. FAC 매핑 테이블 다운로드:
   - http://xbrl.squarespace.com/fundamental-accounting-concept/
   - https://accounting.auditchain.finance/reporting-scheme/us-gaap/fac/Rules_Mapping/

2. DB에 태그 매핑 테이블 구축:
```sql
CREATE TABLE xbrl_tag_mapping (
    id BIGSERIAL PRIMARY KEY,
    source_tag VARCHAR(255) NOT NULL,
    taxonomy VARCHAR(100) NOT NULL,
    fundamental_concept VARCHAR(100) NOT NULL,
    confidence_score DECIMAL(3,2),
    UNIQUE(source_tag, taxonomy)
);
```

3. 파싱 시 매핑 적용:
```java
@Service
public class XbrlNormalizationService {

    public String normalizeToConcept(String taxonomy, String tag) {
        // 1. Direct mapping lookup
        String concept = tagMappingRepository
            .findBySourceTagAndTaxonomy(tag, taxonomy)
            .map(TagMapping::getFundamentalConcept)
            .orElse(null);

        // 2. Pattern matching for common prefixes/suffixes
        if (concept == null) {
            concept = inferConceptFromPattern(tag);
        }

        return concept;
    }

    private String inferConceptFromPattern(String tag) {
        if (tag.matches(".*Revenue.*|.*Sales.*")) return "Revenue";
        if (tag.matches(".*Asset.*")) return "Assets";
        if (tag.matches(".*Liability.*|.*Liabilities.*")) return "Liabilities";
        // ... more patterns
        return null;
    }
}
```

### 전략 B: 상용 정규화 API 사용 (빠른 구축)

#### Option B-1: Financial Modeling Prep (FMP)

**특징**:
- SEC XBRL 데이터를 자체 정규화하여 제공
- JSON/CSV 형식, 30년+ 히스토리
- 표준화된 재무제표 (Income Statement, Balance Sheet, Cash Flow)

**가격**:
- **Free Tier**: 250 requests/day (PoC 충분)
- **Starter**: $29/month (500 requests/day)
- **Professional**: $99/month (750 requests/day)

**API 예시**:
```bash
# 표준화된 재무제표
GET https://financialmodelingprep.com/api/v3/income-statement/AAPL?apikey=XXX

# 응답 (정규화됨):
{
  "date": "2023-09-30",
  "symbol": "AAPL",
  "revenue": 383285000000,
  "costOfRevenue": 214137000000,
  "grossProfit": 169148000000,
  "operatingIncome": 114301000000,
  "netIncome": 96995000000,
  ...
}
```

**통합 방안**:
```java
@Service
public class FmpFinancialDataService {

    private final WebClient webClient;

    public FinancialStatement fetchStandardizedFinancials(String symbol,
                                                          FilingType type) {
        String endpoint = switch(type) {
            case INCOME_STATEMENT -> "/income-statement/{symbol}";
            case BALANCE_SHEET -> "/balance-sheet-statement/{symbol}";
            case CASH_FLOW -> "/cash-flow-statement/{symbol}";
        };

        return webClient.get()
            .uri(endpoint, symbol)
            .retrieve()
            .bodyToMono(FinancialStatement.class)
            .block();
    }
}
```

**장점**:
- 즉시 사용 가능
- 정규화 보장
- SEC 공시와 1:1 매핑 제공
- 무료 티어로 PoC 가능

**단점**:
- 외부 의존성
- API 요청 제한
- 커스터마이징 불가

#### Option B-2: Intrinio US Fundamentals

**특징**:
- **Standardized Financials**: SEC 10-Q/10-K 기반 정규화
- **As-Reported Financials**: 원본 XBRL 태그 그대로 제공
- 두 가지 모두 제공하여 비교 가능

**가격**:
- **$9,600/year** (월 $800)
- 15년+ 히스토리
- 실시간 업데이트 (SEC 공시 후 20-30분)

**API 예시**:
```bash
# Standardized Financials
GET https://api-v2.intrinio.com/fundamentals/AAPL/income_statement?api_key=XXX

# As-Reported (원본 XBRL)
GET https://api-v2.intrinio.com/fundamentals/AAPL/reported_financials?api_key=XXX
```

**장점**:
- 기관급 데이터 품질
- Standardized + As-Reported 동시 제공
- 메트릭 및 비율 계산 포함

**단점**:
- 높은 비용 (PoC 단계에서는 부담)
- 무료 트라이얼 기간 제한적

#### Option B-3: Polygon.io

**특징**:
- XBRL 기반 재무제표 추출
- 초저지연 (<20ms) 실시간 시장 데이터 (주가/옵션)
- 인프라 직접 소유 (데이터센터-거래소 직접 연결)

**가격**:
- Stocks Starter: $99/month
- Stocks Advanced: $199/month

**장점**:
- 시장 데이터 + 펀더멘탈 통합
- 개발자 친화적 API
- 낮은 레이턴시

**단점**:
- 재무제표 정규화 수준이 FMP/Intrinio보다 낮을 수 있음

---

## 권장 아키텍처

### Phase 1: PoC (즉시 구현 가능)

**하이브리드 접근법**:

```
┌─────────────────────────────────────────────────────────────┐
│                      Data Ingestion Layer                    │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐          ┌─────────────────┐          │
│  │ SEC EDGAR API    │          │ FMP API         │          │
│  │ (Filing Metadata)│          │ (Standardized)  │          │
│  └────────┬─────────┘          └────────┬────────┘          │
│           │                              │                   │
│           ▼                              ▼                   │
│  ┌────────────────────────────────────────────────┐         │
│  │       Normalization & Validation Service       │         │
│  │  - FAC Mapping Table                           │         │
│  │  - DQC Validation Rules (core subset)          │         │
│  │  - Data Quality Scoring                        │         │
│  └────────────────────┬───────────────────────────┘         │
│                       │                                      │
└───────────────────────┼──────────────────────────────────────┘
                        ▼
         ┌──────────────────────────────┐
         │  Unified Financial Data DB   │
         │  (Normalized + Metadata)     │
         └──────────────────────────────┘
```

**구현 단계**:

1. **Primary Source: FMP API**
   - 표준화된 재무제표 데이터 수집
   - Free tier로 PoC 시작 (250 req/day)
   - 주요 메트릭: Revenue, Operating Income, Net Income, Assets, Liabilities, Equity, Cash Flow

2. **Fallback: SEC EDGAR Direct**
   - FMP에서 누락된 데이터 보완
   - Form 4, 13F/13D/G는 SEC에서 직접 파싱 (FMP 미지원)
   - Filing 원본 링크 및 메타데이터

3. **Normalization Layer**
   ```java
   @Service
   public class FinancialDataNormalizationService {

       public NormalizedFinancials normalize(Filing filing) {
           // 1. Try FMP first
           var fmpData = fmpService.fetchIfAvailable(filing.getTicker());
           if (fmpData != null) {
               return NormalizedFinancials.fromFmp(fmpData)
                   .withSourceFiling(filing)
                   .withQualityScore(0.95); // High confidence
           }

           // 2. Fallback to SEC XBRL with FAC mapping
           var xbrlFacts = xbrlParser.parse(filing.getXbrlUrl());
           var mapped = facMapper.mapToFundamentalConcepts(xbrlFacts);

           return NormalizedFinancials.fromXbrl(mapped)
               .withSourceFiling(filing)
               .withQualityScore(calculateQualityScore(mapped));
       }

       private double calculateQualityScore(List<MappedFact> facts) {
           long mappedCount = facts.stream()
               .filter(f -> f.getFundamentalConcept() != null)
               .count();
           return (double) mappedCount / facts.size();
       }
   }
   ```

4. **FAC Mapping Table 구축**
   ```sql
   INSERT INTO xbrl_tag_mapping (source_tag, taxonomy, fundamental_concept, confidence_score) VALUES
   ('Revenues', 'us-gaap', 'Revenue', 1.0),
   ('SalesRevenueNet', 'us-gaap', 'Revenue', 1.0),
   ('RevenueFromContractWithCustomerExcludingAssessedTax', 'us-gaap', 'Revenue', 1.0),
   ('OperatingIncomeLoss', 'us-gaap', 'OperatingIncome', 1.0),
   ('NetIncomeLoss', 'us-gaap', 'NetIncome', 1.0),
   ('Assets', 'us-gaap', 'Assets', 1.0),
   ('AssetsCurrent', 'us-gaap', 'CurrentAssets', 1.0),
   ('Liabilities', 'us-gaap', 'Liabilities', 1.0),
   ('StockholdersEquity', 'us-gaap', 'Equity', 1.0);
   -- ... (핵심 200~300개 태그 매핑)
   ```

5. **데이터 품질 검증**
   ```java
   @Component
   public class DataQualityValidator {

       private static final Set<String> CRITICAL_METRICS = Set.of(
           "Revenue", "OperatingIncome", "NetIncome",
           "Assets", "Liabilities", "Equity"
       );

       public ValidationResult validate(NormalizedFinancials financials) {
           var result = new ValidationResult();

           // Check critical metrics presence
           for (String metric : CRITICAL_METRICS) {
               if (!financials.hasMetric(metric)) {
                   result.addWarning("Missing critical metric: " + metric);
               }
           }

           // Check for anomalies
           if (financials.getRevenue() < 0) {
               result.addError("Negative revenue");
           }

           // Check balance sheet equation
           var assets = financials.getAssets();
           var liabilities = financials.getLiabilities();
           var equity = financials.getEquity();

           if (Math.abs(assets - (liabilities + equity)) > 1000) {
               result.addWarning("Balance sheet doesn't balance");
           }

           return result;
       }
   }
   ```

### Phase 2: Production (중장기)

**Option 1: Arelle + XULE 자체 정규화 엔진**

장기적으로 외부 API 의존성 제거하려면:

```
┌──────────────────────────────────────────────────────────┐
│               SEC EDGAR Ingestion Service                 │
│  - Submissions API (metadata)                             │
│  - Full XBRL Instance Documents                           │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────────────┐
│           Arelle XBRL Processing Pipeline                 │
│  ┌────────────────────────────────────────────────────┐  │
│  │  1. DQC Rules Validation (166 rules)              │  │
│  │  2. XULE Normalization (XUSSS)                    │  │
│  │  3. FAC Mapping                                   │  │
│  └────────────────────────────────────────────────────┘  │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │  Normalized Data DB   │
         └───────────────────────┘
```

**구현**:

1. **Arelle REST Wrapper 구축**
   ```python
   # arelle_service.py
   from arelle import Cntlr
   from flask import Flask, request, jsonify

   app = Flask(__name__)

   @app.route('/normalize', methods=['POST'])
   def normalize_filing():
       filing_url = request.json['filing_url']

       ctrl = Cntlr.Cntlr()
       ctrl.webCache.workOffline = False

       # Load XBRL instance
       modelXbrl = ctrl.modelManager.load(filing_url)

       # Run DQC validation
       ctrl.runDQCRules(modelXbrl)

       # Run XULE normalization
       normalized = ctrl.runXuleNormalization(modelXbrl)

       return jsonify(normalized)

   if __name__ == '__main__':
       app.run(port=5000)
   ```

2. **Java에서 호출**
   ```java
   @Service
   public class ArelleNormalizationService {

       private final WebClient arelleClient;

       public NormalizedFinancials normalize(String filingUrl) {
           return arelleClient.post()
               .uri("/normalize")
               .bodyValue(Map.of("filing_url", filingUrl))
               .retrieve()
               .bodyToMono(NormalizedFinancials.class)
               .block();
       }
   }
   ```

**Option 2: XBRL US API 구독**

- XBRL US Public Database 직접 사용
- 이미 정규화된 데이터 제공
- 비용 및 접근 권한 확인 필요

---

## 비용 비교 및 의사결정 매트릭스

### PoC 단계 (3개월)

| 옵션 | 초기 비용 | 월 비용 | 구축 시간 | 데이터 품질 | 유지보수 |
|-----|---------|--------|---------|----------|---------|
| **FMP Free Tier** | $0 | $0 | 1주 | 높음 | 낮음 |
| **FMP Starter** | $0 | $29 | 1주 | 높음 | 낮음 |
| **SEC + FAC Mapping** | $0 | $0 | 3-4주 | 중간 | 높음 |
| **Arelle + XULE** | $0 | $0 | 6-8주 | 매우높음 | 중간 |
| **Intrinio** | $0 (trial) | $800 | 1주 | 매우높음 | 낮음 |

### Production 단계

| 옵션 | 연간 비용 | 확장성 | 커스터마이징 | 독립성 |
|-----|---------|--------|------------|--------|
| **FMP Professional** | $1,188 | 높음 | 낮음 | 낮음 |
| **Intrinio** | $9,600 | 매우높음 | 중간 | 낮음 |
| **Arelle + XULE (자체)** | $0 | 매우높음 | 매우높음 | 완전 |
| **하이브리드** | $1,188 | 높음 | 높음 | 중간 |

---

## 최종 권장사항

### PoC 단계 (즉시 시작)

**하이브리드 접근법**:
1. **Primary**: FMP Free Tier (250 req/day)
   - 표준화된 재무제표 데이터
   - 즉시 사용 가능

2. **Secondary**: SEC EDGAR Direct
   - Form 4, 13F/13D/G 직접 파싱
   - Filing 메타데이터 및 원본 링크

3. **Normalization Layer**:
   - 핵심 200-300개 태그 FAC 매핑 테이블 구축
   - 데이터 품질 검증 로직
   - Quality Score 기반 신뢰도 표시

**구현 우선순위**:
```
Week 1-2:
- FMP API 통합
- 핵심 메트릭 데이터 수집 (Revenue, Operating Income, Net Income, Assets, Liabilities, Equity)
- 히트맵 계산 로직 FMP 데이터로 전환

Week 3-4:
- FAC Mapping Table 구축 (핵심 200개 태그)
- SEC EDGAR Form 4, 13F 파싱 (ExecPACE, InstiFlow용)
- 데이터 품질 검증 로직

Week 5-6:
- Normalization Service 고도화
- Fallback 로직 (FMP → SEC XBRL)
- Quality Score 계산 및 UI 표시
```

### Production 단계 (3-6개월 후)

**단계적 전환**:

1. **Short-term (3-6개월)**: FMP Professional 구독 ($99/month)
   - 안정적인 서비스 제공
   - 사용자 확보 및 피드백 수집

2. **Mid-term (6-12개월)**: Arelle + XULE 자체 엔진 병행
   - FMP를 메인으로 유지하면서 Arelle 파이프라인 구축
   - 점진적 전환 (리스크 최소화)

3. **Long-term (12개월+)**: 완전 자체 정규화
   - 비용 절감
   - 완전한 커스터마이징
   - 독립성 확보

---

## 구현 체크리스트

### 즉시 실행 (이번 주)

- [ ] FMP API 키 발급 (Free Tier)
- [ ] FMP API 클라이언트 구현 (Income Statement, Balance Sheet, Cash Flow)
- [ ] 기존 XBRL 파싱 로직을 FMP 우선으로 변경
- [ ] 히트맵 계산 로직 FMP 데이터로 전환 테스트 (AAPL)

### 1-2주 내

- [ ] FAC Mapping CSV/JSON 다운로드 (Charles Hoffman's site)
- [ ] `xbrl_tag_mapping` 테이블 생성 및 데이터 로드
- [ ] NormalizationService 구현
- [ ] DataQualityValidator 구현
- [ ] Quality Score UI 표시

### 2-4주 내

- [ ] SEC Form 4 파싱 로직 유지 (FMP 미지원)
- [ ] 13F/13D/G 파싱 로직 검토 및 개선
- [ ] Fallback 로직 테스트 (FMP 실패 시 SEC 직접)
- [ ] 다양한 회사 테스트 (Tech, Finance, Manufacturing 등)

### 장기 (3개월+)

- [ ] Arelle Docker 이미지 구축
- [ ] XULE normalization ruleset 다운로드 및 테스트
- [ ] Arelle REST API wrapper 구현
- [ ] DQC Rules 통합 (166개 검증 규칙)
- [ ] 성능 최적화 (캐싱, 병렬 처리)

---

## 결론

**핵심 인사이트**:
1. **XBRL 파싱의 복잡성은 과소평가할 수 없음** - 단순 태그 파싱으로는 안정적인 서비스 불가능
2. **정규화(Normalization)는 필수** - 회사 간 비교, 일관된 메트릭 계산을 위해 반드시 필요
3. **단계적 접근이 현실적** - PoC에서 상용 API 활용 후 점진적으로 자체 엔진 구축

**즉시 실행 사항**:
- FMP API로 전환하여 데이터 안정성 확보
- FAC 매핑 테이블로 최소한의 정규화 레이어 구축
- Quality Score로 데이터 신뢰도 투명하게 공개

**장기 목표**:
- Arelle + XULE 기반 자체 정규화 엔진 구축
- 완전한 독립성과 커스터마이징 확보
- 기관급 데이터 품질 달성

이 전략을 통해 초기에는 빠르게 PoC를 완성하고, 중장기적으로는 안정적이고 확장 가능한 데이터 파이프라인을 구축할 수 있습니다.

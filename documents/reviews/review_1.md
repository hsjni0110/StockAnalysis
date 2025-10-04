diff_plan.md의 Phase 1 백엔드 작업을 구현한 커밋입니다. 각
  코드의 역할을 리뷰해드리겠습니다.

  📦 의존성 추가 (build.gradle)

  implementation 'org.jsoup:jsoup:1.17.2'
  implementation 'org.bitbucket.cowwoc:diff-match-patch:1.2'

  - Jsoup: HTML 파싱 및 정제용 (계획 1.1)
  - diff-match-patch: 텍스트 diff 연산용 (계획 1.2)

  ---
  🗄️ Entity & Repository

  XbrlMetric.java

  - XBRL 메트릭 변화율을 저장하는 엔티티 (계획 1.3)
  - 복합키 구조: (filingId, metric, basis)
  - basis: "QoQ", "YoY", "Abs" 구분
  - value: 변화율 또는 절대값 저장

  FilingDeltaRepository.java

  - Filing 섹션 간 차이(delta) 조회
  - findByFilingIdAndSectionOrderByScoreDesc: 중요도 점수 기준
   정렬 (계획 1.2의 3번)

  FilingSectionRepository.java

  - Filing 섹션 텍스트 저장/조회
  - findByFilingIdAndSections: 특정 섹션(Item1A, Item7 등)
  다중 조회

  XbrlFactRepository.java

  - XBRL Raw Fact 조회
  - findByFilingIdAndTags: 특정 태그(Revenue,
  OperatingIncomeLoss 등) 조회 (계획 1.3)

  XbrlMetricRepository.java

  - XbrlMetric 조회
  - findByFilingIdAndBasis: QoQ/YoY 필터링

  ---
  🔧 Core Services

  1. FilingSectionExtractor.java (계획 1.1)

  역할: SEC Filing HTML에서 Item 1A, 7, 7A 섹션 추출

  핵심 로직:
  // Item 1A (Risk Factors), Item 7 (MD&A), Item 7A (Market 
  Risk) 정규식 매칭
  ITEM_1A_PATTERN = "ITEM 1A.* RISK FACTORS"
  ITEM_7_PATTERN = "ITEM 7.* MANAGEMENT'S DISCUSSION"

  - Jsoup으로 HTML 정제 → <script>, <style> 제거
  - 문단 단위 토큰화 → 최소 50자 이상 (MIN_PARAGRAPH_LENGTH)
  - SHA-256 해시 생성 → 중복 방지 (calculateHash())
  - 중요도 점수 계산 → 키워드("risk", "litigation", "lawsuit")
   가중치 부여

  계획 대비 구현:
  ✅ Jsoup 활용 HTML 정제✅ 문단 단위 토큰화 (50자 이상)✅
  SHA-256 해시✅ filing_sections 테이블 저장

  ---
  2. FilingDiffService.java (계획 1.2)

  역할: 동일 Form의 직전 분기/연도 파일링과 텍스트 diff 연산

  핵심 로직:
  // 1. 직전 Filing 조회
  findPreviousFiling(current) → 10-Q면 직전 분기, 10-K면 직전
  연도

  // 2. diff-match-patch로 문단별 비교
  DiffMatchPatch dmp = new DiffMatchPatch();
  LinkedList<Diff> diffs = dmp.diffMain(previous, current);
  dmp.diffCleanupSemantic(); // 의미론적 정리

  // 3. Operation 분류
  INSERT → 새로 추가된 문단 (초록)
  DELETE → 삭제된 문단 (빨강)
  MODIFY → 수정된 문단 (노랑) - 현재 미구현

  // 4. 중요도 점수 부여
  calculateImportanceScore() → 키워드 기반 0.0~1.0 점수

  계획 대비 구현:
  ✅ 직전 분기/연도 조회✅ INSERT/DELETE 연산⚠️ MODIFY 연산 
  미구현 (유사도 점수 계산 필요)✅ 중요도 점수 부여✅
  filing_deltas 저장

  ---
  3. XbrlMetricsService.java (계획 1.3)

  역할: XBRL 태그 간 QoQ/YoY 변화율 계산 및 이상치 탐지

  핵심 로직:
  // 1. 핵심 태그 정의
  CORE_METRICS = ["Revenue", "OperatingIncomeLoss",
  "Inventory", "CapEx", "Cash"]

  // 2. QoQ/YoY 계산
  calculateMetrics(current, previous) {
      if (10-Q) → QoQ = (current - previous) / previous * 100
      if (10-K) → YoY = (current - previous_year) /
  previous_year * 100
  }

  // 3. Z-score 이상치 탐지
  zscore = (value - mean) / stddev
  if (|zscore| > 2) → 통계적 이상치 플래그

  계획 대비 구현:
  ✅ 동일 기업 직전 분기/연도 XBRL 조회✅ QoQ/YoY 변화율
  계산✅ Z-score 이상치 탐지 (threshold=2)✅ xbrl_metrics 캐시

  ---
  4. SecApiClient.java

  추가 메서드: fetchDocument(String url)

  역할: SEC 원문 HTML 문서 페칭
  - 캐싱 지원 (doc_{hashCode})
  - Rate limiting 적용
  - 50MB 대용량 문서 처리 (maxInMemorySize)

  ---
  ✅ 계획 대비 달성도

  | 항목                         | 계획  | 구현  | 비고
                 |
  |----------------------------|-----|-----|------------------
  -------|
  | 1.1 FilingSectionExtractor | ✅   | ✅   | 완료
             |
  | 1.2 FilingDiffService      | ✅   | ⚠️  | MODIFY 연산 미완
              |
  | 1.3 XbrlMetricsService     | ✅   | ✅   | 완료
             |
  | 의존성 추가                     | ✅   | ✅   | jsoup,
  diff-match-patch |

  ---
  🔴 개선 필요 사항

  1. FilingDiffService의 MODIFY 연산 미구현

  // 현재: INSERT/DELETE만 처리
  // 필요: MODIFY 연산 + 유사도 점수 계산
  if (similarity > 0.5 && similarity < 0.95) {
      delta.setOperation(MODIFY);
      delta.setSimilarity(similarity);
  }

  2. FilingSectionExtractor의 섹션 분리 정확도

  - 현재: 정규식 기반 단순 매칭
  - 개선: 다음 Item 시작 지점까지 정확히 추출 (현재는 2000자
  고정)

  3. 에러 핸들링

  // SecApiClient.fetchDocument()에서 404/500 에러 시
  // 빈 결과 반환 → 로그만 남김
  // 개선: Retry 로직 또는 fallback 데이터 제공

  4. 성능 최적화

  - computeDeltas(): 현재 동기 처리 → 비동기 처리 권장
  - 대용량 문서(>10MB) 처리 시 OOM 위험 → 스트리밍 파싱 고려

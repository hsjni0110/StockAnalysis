🎨 Phase 1: 백엔드 - 데이터 분석 엔진

    1.1 Filing 섹션 파서 (FilingSectionExtractor)

    // Item 1A (Risk Factors), Item 7 (MD&A) HTML→텍스트 추출
    - Jsoup 활용 HTML 정제
    - 문단 단위 토큰화 (최소 50자 이상)
    - SHA-256 해시로 중복 방지
    - filing_sections 테이블 저장

    1.2 Diff 엔진 (FilingDiffService)

    // Google diff-match-patch 라이브러리 활용
    1. 동일 Form의 직전 분기/연도 파일링 조회
       - 10-Q: 직전 분기 10-Q
       - 10-K: 직전 연도 10-K
    2. 문단별 diff 연산
       - INSERT: 새로 추가된 문단 (초록)
       - DELETE: 삭제된 문단 (빨강)
       - MODIFY: 수정된 문단 (노랑), 유사도 점수 계산
    3. 중요도 점수 부여 (0.0~1.0)
       - 키워드: "risk", "uncertainty", "lawsuit" 등 가중치
       - 길이: 긴 변화일수록 높은 점수
    4. filing_deltas 저장

    1.3 XBRL 변화율 계산 (XbrlMetricsService)

    // 핵심 태그: Revenue, OperatingIncomeLoss, Inventory, 
    CapEx, Cash
    1. 동일 기업의 직전 분기/연도 XBRL 조회
    2. QoQ (Quarter-over-Quarter) / YoY 변화율 계산
    3. 통계적 이상치 탐지 (Z-score > 2)
    4. xbrl_metrics 테이블 캐시

    ---
    🔌 Phase 2: 백엔드 - DeltaMap API

    API 엔드포인트

    // 1. 변화 분석 트리거 (비동기)
    POST /api/filings/{filingId}/analyze-delta
    → 백그라운드 작업 시작, job_id 반환

    // 2. 섹션 텍스트 조회
    GET /api/filings/{filingId}/sections?section=Item1A,Item7
    → { sections: [{ section, text, charCount, hash }] }

    // 3. Diff 결과 조회
    GET /api/filings/{filingId}/deltas?section=Item7
    → { 
      current: { filingId, form, periodEnd },
      previous: { filingId, form, periodEnd },
      deltas: [{ operation, snippet, score, context }] 
    }

    // 4. XBRL 히트맵 데이터
    GET /api/filings/{filingId}/xbrl-heatmap
    → {
      metrics: [
        { tag: "Revenue", current: 100M, previous: 95M, 
          change: 5.26%, basis: "QoQ", zscore: 1.2 }
      ]
    }

    // 5. 티커별 최신 변화 요약
    GET /api/tickers/{symbol}/delta-summary
    → {
      latestFiling: { ... },
      changeBadges: [
        { type: "section", label: "MD&A 3문단 변경", severity: 
    "medium" },
        { type: "xbrl", label: "매출 +12% 급증", severity: 
    "high" }
      ]
    }

    ---
    🎨 Phase 3: 프론트엔드 - DeltaMap UI (UX 최적화)

    3.1 페이지 구조: /ticker/:symbol/delta

    레이아웃: Split Panel (60/40 비율, 반응형)

    ┌─────────────────────────────────────────────────────┐
    │ 헤더: AAPL - 2024 Q3 10-Q vs 2024 Q2 10-Q          │
    │ [Item 1A] [Item 7] [Item 7A] 탭                    │
    ├──────────────────────────┬──────────────────────────┤
    │ 좌측 (60%): Diff View    │ 우측 (40%): Context     │
    │                          │                          │
    │ ┌──────────────────────┐ │ ┌──────────────────────┐│
    │ │ @git-diff-view/react │ │ │ XBRL 변화 히트맵     ││
    │ │ - Side-by-Side Mode  │ │ │ (Nivo HeatMap)       ││
    │ │ - 구버전 | 신버전    │ │ │                      ││
    │ │ - 초록(추가)         │ │ │ Revenue  ▲ +5.2%    ││
    │ │ - 빨강(삭제)         │ │ │ OpIncome ▼ -2.1%    ││
    │ │ - 노랑(수정)         │ │ │ Inventory▲ +18.3% ⚠││
    │ │                      │ │ │                      ││
    │ │ [원문보기 SEC]       │ │ │ [상세보기]           ││
    │ └──────────────────────┘ │ └──────────────────────┘│
    │                          │ ┌──────────────────────┐│
    │ 스크롤 동기화 옵션 ☑     │ │ 주요 변화 요약       ││
    │                          │ │ • 법적리스크 3건 추가││
    │                          │ │ • 재고 급증 이상치   ││
    │                          │ └──────────────────────┘│
    └──────────────────────────┴──────────────────────────┘

    3.2 컴포넌트 설계

    A. <DeltaMapPage> (메인 컨테이너)

    - useParams()로 symbol, filingId 추출
    - useQuery: /api/tickers/:symbol/delta-summary
    - Tab 상태 관리 (Item1A/7/7A)
    - Split Panel 비율 조정 (react-split-pane)

    B. <DiffViewPanel> (좌측)

    import { DiffView, DiffModeEnum } from 
    '@git-diff-view/react';

    // Features:
    - Side-by-side (split) 모드
    - 다크/라이트 테마 토글
    - 줄번호 표시
    - 스크롤 동기화
    - 변화 하이라이트 (operation별 색상)
    - 클릭 시 원문 SEC 링크 열기
    - 검색 기능 (Ctrl+F)

    C. <XbrlHeatmapPanel> (우측 상단)

    import { ResponsiveHeatMap } from '@nivo/heatmap';

    // Data Format:
    [
      { metric: 'Revenue', 'QoQ': 5.2, 'YoY': 12.1 },
      { metric: 'OpIncome', 'QoQ': -2.1, 'YoY': 3.5 },
      ...
    ]

    // Design:
    - 색상: 빨강(-) → 회색(0) → 초록(+)
    - 셀 클릭 시 상세 모달 (전체 히스토리 차트)
    - 툴팁: 절대값, 변화율, Z-score
    - 이상치(|Z|>2) 경고 아이콘

    D. <ChangeSummaryCard> (우측 하단)

    // 주요 변화 요약 (LLM 생성 예정, 지금은 규칙 기반)
    - "Item 1A에서 소송 관련 리스크 3개 문단 추가"
    - "재고자산 +18.3% 급증 (Z-score 2.4)"
    - "현금성자산 -12% 감소"

    // 클릭 시 해당 위치로 스크롤
    // SEC 원문 링크 아이콘

    3.3 Dashboard 통합

    기존 Dashboard 개선

    // components/Dashboard.tsx 수정

    1. 새 탭 추가: "변화 분석 (DeltaMap)"
    2. FilingCard에 "변화보기" 버튼 추가
       → 클릭 시 /ticker/:symbol/delta/:filingId로 이동
    3. 메인 화면 상단에 "최근 중요 변화" 배지
       - "AAPL: MD&A 주요 변경 3건 🔴"
       - "NVDA: 재고 +22% 이상치 ⚠️"

    ---
    🎯 Phase 4: UX 세부 사항

    인터랙션 디자인

    1. 로딩 상태: Skeleton UI (diff 분석 중)
    2. 빈 상태: "이전 분기 데이터 없음" 안내
    3. 에러 처리: SEC API 장애 시 캐시된 마지막 데이터 표시
    4. 반응형: 모바일에서는 탭 전환 (Split → Stacked)

    접근성 (WCAG 2.1 AA)

    - 고대비 모드 지원
    - 키보드 네비게이션 (Tab, Arrow keys)
    - 스크린리더 alt text
    - 색맹 대응 (색상 + 패턴 병행)

    성능 최적화

    - Diff 결과 캐싱 (React Query)
    - 가상 스크롤 (긴 문서 대응)
    - 이미지 lazy loading
    - Code splitting (DeltaMap 페이지 별도 번들)

    ---
    📦 라이브러리 설치

    # 프론트엔드
    pnpm add @git-diff-view/react
    pnpm add @nivo/heatmap @nivo/core
    pnpm add react-split-pane
    pnpm add diff-match-patch (타입: @types/diff-match-patch)

    # 백엔드
    // build.gradle
    implementation 'org.jsoup:jsoup:1.17.2'
    implementation 'org.bitbucket.cowwoc:diff-match-patch:1.2'

    ---
    ✅ 수용 기준

    1. DeltaMap 정확도: 10-Q 3개 샘플 대비 95% 이상 변화 탐지
    2. UI 로딩 속도: 첫 렌더링 < 2초 (캐시 없음), < 500ms (캐시 
    적중)
    3. XBRL 히트맵: 최소 10개 주요 태그 QoQ/YoY 표시
    4. 원문 링크: 모든 델타에 SEC URL 첨부
    5. 반응형: 모바일(375px)~데스크탑(1920px) 대응

    ---
    🚀 구현 순서 (3주 예상)

    Week 1: 백엔드 엔진
    - Day 1-2: FilingSectionExtractor (Item 1A, 7 파싱)
    - Day 3-4: FilingDiffService (diff-match-patch 통합)
    - Day 5: XbrlMetricsService (QoQ/YoY 계산)

    Week 2: API & 프론트 기초
    - Day 1-2: DeltaMap API 엔드포인트 4개 구현
    - Day 3-4: DeltaMapPage 라우팅 및 레이아웃
    - Day 5: DiffViewPanel 컴포넌트 (@git-diff-view 통합)

    Week 3: 시각화 & 통합
    - Day 1-2: XbrlHeatmapPanel (Nivo 히트맵)
    - Day 3: ChangeSummaryCard & Dashboard 통합
    - Day 4-5: UX 개선, 반응형, 접근성 테스트
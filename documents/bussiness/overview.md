0) 제품 개요

제품 목적(요약)
내 보유/관심 종목의 **“새로 바뀐 점(Delta)”**을 가장 빠르게 잡고(SEC 실시간 공시·임원거래·기관보유·옵션 변동성), 이를 **다각도(가치/퀄리티/성장/모멘텀/리스크/경영진·기관)**로 LLM이 근거와 함께 정리해 즉시 행동 가능한 인사이트로 제공한다.

핵심 KPI
	•	변화 감지 TTI(Time-to-Insight): 공시·신호 발생 후 분 단위 요약 카드 생성
	•	“지난 접속 이후 변경” 정확 알림(거짓 양성 < 5%)
	•	사용자 1명당 워치리스트 종목별 주 1회 이상 유의미한 변화 탐지

⸻

1) 주요 사용자 시나리오 (PoC 범위)
	1.	아침 브리핑: 내 워치리스트 기준, 지난 24h 내 8-K/10-Q/10-K, Form 4, 13F/13D/G, 옵션 IV 급등 요약 카드 제공 + 원문 출처.
	2.	종목 상세
	•	DeltaMap: 전기 대비 문장/표 변화 Diff(Item 1A, MD&A), XBRL 수치 변화 히트맵
	•	ExecPACE: 임원 동시 매수/매도 클러스터·규모·가격대
	•	InstiFlow: 분기 13F 순변화·주요 보유기관 교체, 13D/G(활동가) 타임라인
	•	Options Pulse: 만기·행사가별 IV 스파이크/스큐 변화
	•	LLM 멀티-렌즈 요약(근거 문장 인용 포함)
	3.	알림: Form 4 다건 동시 매수, 8-K 특정 아이템(가이던스·경고), IV 급등

⸻

2) 데이터 소스 / 신뢰성 / 획득 방법 (실행 상세)

2.1 SEC EDGAR (공시·XBRL) — 1차 신뢰 원천
	•	공식 REST API: data.sec.gov (Submissions, XBRL companyfacts/companyconcept/frames). 인증키 불필요, 단 User-Agent 헤더 필수(앱/연락처 명시). 실시간 업데이트(서브미션 <1s, XBRL <1분 지연), **야간(ET 03:00)**에 벌크 ZIP 제공. CORS 미지원(백엔드 프록시 필수).  ￼
	•	Submissions 예: .../submissions/CIK##########.json – 회사 기본정보·최근 1,000건 이상 압축 배열 · 티커/거래소 메타 포함.  ￼
	•	XBRL Company Facts/Concept/Frames(분기·연간·스냅샷 집계 지원). 프레임 API는 달력 분기/연도에 맞춘 집계 특성 유의.  ￼
	•	페어 액세스(레이트 리밋): 초당 10요청 이하. 과도 시 차단. 큐·백오프·리트라이 필요.  ￼
	•	User-Agent 요구 및 예시 포맷(회사명·이메일 포함).  ￼

활용 설계
	•	DeltaMap:
	1.	Submissions로 최근 10-Q/10-K/8-K 목록 → 이전분기/작년 동형식 파일과 문단 Diff(Item 1A/7/7A 우선).
	2.	XBRL Company Facts/Frames로 핵심 지표(매출, 영업이익, 재고, CapEx 등) 전기/전년 대비 변화율 계산 → 히트맵.  ￼
	•	타임라인: Submissions 스트림(실시간) + 8-K 아이템 키워드 매핑.

2.2 내부자 거래 (Forms 3/4/5) — C-레벨 신호
	•	규정·데드라인: Form 4 = 거래 후 2 영업일 내 제출 의무. (임원·10% 이상 보유자, 옵션 등 파생 포함) → 신속 신호로 적합.  ￼
	•	획득: EDGAR Submissions에서 Form 4 필터링 → 트랜잭션 라인 파싱(코드·수량·단가).
	•	지표화: 직책/임기 가중, 52주 구간 위치, 동시성(클러스터)·규모 Z-score → ExecPACE 게이지.

2.3 기관 보유 / 대주주 공시 (13F/13D/13G)
	•	13F: 분기 종료 후 45일 이내 제출(기관투자매니저). 보유 지분 순변화 파악.  ￼
	•	13D/13G: 5% 이상 보유 시 제출. 2023 개정으로 13G 마감기한 단축(2024-09-30 발효) – 스케줄별 상이. 활동가·대주주 이벤트 신호로 사용.  ￼
	•	획득: Submissions에서 13F/13D/G 검색 → 보유내역/변화 추출 → InstiFlow 타임라인.

2.4 옵션 데이터(IV/Greeks/OI) — 단기 온도계
	•	**OPRA(미국 옵션 공용피드)**는 실시간·지연 데이터 모두 라이선스/수수료 체계가 존재(재배포·실사용 시 규정 준수 필요). 직결 연동 비용 높음 → PoC는 상업 API(Polygon/Intrinio)로 대체 권장.  ￼
	•	대체 공급자
	•	Polygon.io: 옵션 스냅샷/체인/Greeks/IV/OI 엔드포인트 제공.  ￼
	•	Intrinio: 실시간 옵션 통계(IV/Greeks)·체인/과거가격 API.  ￼
	•	Cboe DataShop: 고품질 히스토릭 옵션 트레이드/쿼트·Calcs(IV/Greeks) 제공(유료, 재배포 제약).  ￼
	•	설계: PoC는 지연 또는 스냅샷 중심으로 IV 급등/스큐 변화 탐지(비용 최소화). 실시간 전환 시 OPRA 라이선스 검토.  ￼

2.5 주가/체결(차트용) — PoC 실무 선택지
	•	IEX Cloud는 2024-08-31 서비스 종료 → 대체 필요.  ￼
	•	대체:
	•	Alpha Vantage: 무료 티어 일 25건 제한(프리) + 프리미엄 확대.  ￼
	•	Finnhub/Tiingo/Polygon 등 유료 플랜으로 분당/초당 제한 상향.  ￼
	•	설계: PoC는 EOD/지연 시세로 차트 구현(분당콜 제한 내). 알림/이상치 탐지는 SEC/옵션 신호에 우선 의존.

2.6 기업 IR/보도자료/콜 트랜스크립트
	•	근거 신뢰성: 회사 IR 웹사이트(보도자료/이벤트·웹캐스트)는 1차 출처. 8-K(가이던스·경고)와 함께 교차검증.
	•	EDGAR 풀텍스트 검색은 웹 UI 및 상업 API에서 제공되며, 키워드 기반 이벤트 탐지에 유용(공식 웹 UI는 2001년 이후 텍스트 검색).  ￼
	•	주의: 일부 상업 트랜스크립트(예: 알파센스 등)는 재배포 라이선스 제약. PoC는 IR/PR·SEC 원문 우선. (알파센스는 엔터프라이즈 도구)  ￼

⸻

3) 규정·제약 체크리스트 (시스템 설계 반영)
	•	SEC 페어액세스: 초당 10요청 이하·백오프·재시도 필수.  ￼
	•	User-Agent: 모든 요청에 개발사/이메일 포함.  ￼
	•	CORS 미지원: 프론트에서 직접 호출 금지 → 백엔드 프록시.  ￼
	•	OPRA/옵션 재배포: 상업 API 계약 및 표시 요건 준수, 실시간은 사용자 단위 과금·보고의무 가능.  ￼
	•	IEX Cloud 미사용(서비스 종료).  ￼

⸻

4) 정보 처리 파이프라인 (PoC)
	1.	수집(Ingest)
	•	EDGAR Puller: Submissions 폴링(최근 5분/파일링타입 필터) → 신규 8-K/10-Q/10-K/4/13F/13D/G 큐잉. 헤더(User-Agent)·레이트리밋 준수.  ￼
	•	XBRL 파서: companyfacts/frames로 표준 태그 us-gaap/DEI 우선.  ￼
	•	옵션/시세 Puller: 선택한 벤더 API(지연/스냅샷)로 키 메트릭 수집.  ￼
	2.	정규화(Normalize)
	•	Filing 메타, 본문 섹션(1A/7/7A) 추출 → 문단 토큰화 → 이전분기 대비 Diff(삽입/삭제/강조 점수).
	•	XBRL 계정 → 통일 단위/스케일 정규화 → QoQ/YoY 변화율.
	•	Form 4 라인아이템 → 금액 환산·클러스터 감지(기간·직책·가중).
	3.	저장(Stores)
	•	OLTP(Postgres): issuers, filings, filing_deltas, insider_tx, insti_holdings, option_metrics, prices.
	•	시계열(TimescaleDB/ClickHouse 택1): 옵션/가격·IV/OI 시계열.
	•	검색/임베딩(VectorDB: Qdrant/PGVector): 인용 가능한 문단 인덱스.
	4.	분석(Compute)
	•	시그널 엔진: “IV 급등”, “Form 4 클러스터 매수”, “MD&A 위험표현 강화” 등 룰/통계적 임계치.
	•	LLM-RAG: 증거 문단/표를 RAG로 주입하여 멀티-렌즈 요약 생성(출처 링크 강제).
	5.	딜리버리(API)
	•	Spring Boot REST: /tickers/:id/summary, /filings/delta, /insider/cluster, /insti/flow, /options/pulse, /alerts
	6.	알림
	•	크론/스트리머에서 임계치 충족 시 Slack/Email 푸시(Asia/Seoul 타임존).

⸻

5) 시스템 아키텍처 (유지보수성 지향)

구성(모듈화)
	•	api (Spring Boot 3, Java 21): 인증, REST, RAG 게이트웨이, 규정 준수(레이트·UA 프록시)
	•	ingestor-edgar (Spring Scheduler 또는 별도 워커): Submissions/XBRL 수집·정규화
	•	ingestor-market (옵션/시세 공급사 어댑터)
	•	llm-service (선택, 내부 추상화): 프롬프트 템플릿·출처 강제·함수호출
	•	db (Postgres + Timescale 확장 or ClickHouse), vector (Qdrant/PGVector), cache (Redis)
	•	메시징: 최소 Redis Streams(PoC), 이후 Kafka/Redpanda로 확장

Docker Compose (개요)
api, ingestor-edgar, ingestor-market, llm-service, postgres, redis, vector, frontend 컨테이너. 환경변수로 SEC UA, 벤더 API 키, 레이트리밋 설정. (SEC CORS 미지원이므로 api가 모든 외부 호출의 프록시).  ￼

코드 품질/유지보수
	•	멀티모듈 Gradle(공통 DTO/에러/클라이언트)
	•	클린아키텍처(도메인/애플리케이션/인프라 분리)
	•	관찰성: OpenTelemetry + Prometheus/Grafana 대시보드(레이트·에러·TTI)

⸻

6) 프론트엔드(UI/UX)
	•	스택: React 18 + TypeScript, 상태(Zustand/Redux Toolkit), 라우팅(react-router)
	•	차트 라이브러리 선택
	•	TradingView Lightweight Charts — 경량·고성능 금융 캔들/라인/볼륨 차트(MIT, 35~45KB급). 금융차트에 최적.  ￼
	•	Apache ECharts — 대시보드형(히트맵/샌키/지도/트리) 강력, 대용량 렌더링·Canvas/SVG 전환. React 래퍼 이용.  ￼
→ 원칙: 가격/IV/OI = Lightweight, 분석 히트맵/네트워크 = ECharts 조합.

핵심 화면
	•	티커 개요 보드: 상단 스냅샷 + “변화 배지(예: MD&A 3문장 변경, Form 4 2건)”
	•	DeltaMap: 좌측 문단 Diff, 우측 XBRL 변화 히트맵
	•	ExecPACE/InstiFlow/Options Pulse: 카드 3열 + 소형 타임라인
	•	LLM 멀티-렌즈: 6카드(가치/퀄/성장/모멘텀/리스크/경영·기관) – 각 카드에 인용 배지(클릭 시 원문)

⸻

7) API 설계(예시)
	•	GET /api/tickers/{symbol}/summary → { price, iv_snapshot, change_badges[], multi_lens[] }
	•	GET /api/filings/{cik}/latest?form=10-Q&limit=2 → 최근분기 파일링 메타
	•	POST /api/filings/delta body: { cik, formType } → { changed_paragraphs[], xbrl_deltas[] }
	•	GET /api/insider/{cik}/clusters?days=30 → { clusters: [{ officers:[], sum$, zscore, window }] }
	•	GET /api/insti/{cik}/qflows?quarters=4 → { net_change$, top_holders_delta[] }
	•	GET /api/options/{symbol}/pulse?window=5d → { iv_spikes[], skew_events[] }
	•	GET /api/alerts / POST /api/alerts(조건식 생성)

⸻

8) 데이터 스키마(요지)
	•	issuers(cik, ticker, name, sector, ...) – Submissions 메타 활용  ￼
	•	filings(id, cik, form, filed_at, accession, url, ...)
	•	filing_sections(filing_id, section, text, hash)
	•	filing_deltas(filing_id, section, op, text_snippet, score)
	•	xbrl_facts(filing_id, taxonomy, tag, unit, period, value) / xbrl_deltas(...)  ￼
	•	insider_tx(filing_id, insider, role, code, qty, price, ...)
	•	insti_holdings(period, manager, ticker, shares, value) (13F 파싱)  ￼
	•	ownership_filings(type, holder, pct, trigger_date) (13D/G)  ￼
	•	option_metrics(ts, symbol, expiry, strike, type, iv, oi, delta, gamma, ...)  ￼
	•	prices(ts, symbol, open, high, low, close, volume)(공급사별 소스필드)

⸻

9) LLM 설계(고신뢰)
	•	RAG 강제 인용: 카드별 근거 URL(EDGAR/IR/옵션·시세) 없으면 출력 금지.
	•	숫자 재검증: 요약 전 후크에서 XBRL·지표 재조회(함수호출) → 표/그래프 생성.
	•	톤 가이드: “가설/추정” 문구 태깅, 시나리오(상·하방)와 트리거 분리.
	•	한국어/영어 동시 출력(원문 영어, 요약 한글 기본).

⸻

10) 보안/컴플라이언스
	•	레이트·UA 준수(SEC), CORS 백엔드 프록시 강제.  ￼
	•	OPRA/상업 API 라이선스 조건에 맞춘 사용·표시(지연 vs 실시간 명확화).  ￼
	•	로그: 원문 URL/타임스탬프/해시 저장(감사 추적).

⸻

11) 비주얼라이제이션 가이드
	•	가격/캔들/볼륨: Lightweight Charts – 캔들·볼륨 오버레이·크로스헤어·모바일 제스처 최적화.  ￼
	•	XBRL 변화 히트맵/네트워크: Apache ECharts – 대용량·캔버스/진행형 렌더링.  ￼

⸻

12) 운영/성능 (초기 목표)
	•	EDGAR 폴링: 30초1분 주기(큐 적재), XBRL는 파일링 감지 후 12분 지연 예상.  ￼
	•	레이트 관리: ingestor 레벨에서 버스트 8rps, 초당 토큰버킷, 429/Access Denied 대응 백오프.  ￼
	•	옵션/시세: PoC는 일중 N회 스냅샷(비용 내), 알림은 구조적 이벤트 중심.

⸻

13) PRD 수용 기준(정량)
	1.	DeltaMap 정확도: 전분기 10-Q 대비 핵심 3개 문단 이상 변화를 95% 이상 탐지(수동 라벨과 비교).
	2.	ExecPACE: 지난 30일 Form 4 클러스터 매수(임원 ≥2명, 동일 14일 윈도우) 탐지 및 카드 생성 ≤2분. 근거 링크(각 Form 4) 포함.
	3.	InstiFlow: 최근 분기 13F 반영 후 Top 10 보유기관 순위 변화 요약.
	4.	Options Pulse: IV Z-score>2 스파이크 탐지 및 만기/행사가 요약 카드.
	5.	LLM 카드: 6렌즈 요약 모든 주장에 최소 1개 출처 링크(EDGAR/IR/옵션).
	6.	UI/성능: 티커 상세 최초 로드 ≤ 2.5s(캐시 적중 시), 차트 인터랙션 60fps 근접.

⸻

14) 위험요인 & 완화
	•	옵션 실시간 비용/규정: PoC는 지연/스냅샷, 상용화 시 OPRA 계약·벤더 다각화.  ￼
	•	SEC CORS 미지원: 프런트 직접호출 금지 → API 프록시 고정.  ￼
	•	IEX Cloud 종료로 인한 종속 리스크 제거, 대체 벤더 플러그인 구조.  ￼
	•	XBRL 프레임 정합성: SEC가 달력 프레임 보정을 명시 → 기간 매칭 로직·툴팁에 공지.  ￼

⸻

15) 로드맵(8주 PoC 제안)
	•	주1–2: 데이터 커넥터(EDGAR Submissions/XBRL, 옵션·시세 벤더), 스키마/큐 구축
	•	주3–4: DeltaMap Diff + ExecPACE/InstiFlow 요약 카드, Lightweight/ECharts 통합
	•	주5–6: LLM-RAG(근거 강제) + 알림(8-K/4/IV)
	•	주7–8: 튜닝/모니터링/보안·라이선스 점검, 사용자 테스트

⸻

16) 부록 — 참조·정책 원문
	•	SEC EDGAR API 개요/엔드포인트·리얼타임/벌크·CORS 미지원(User-Agent 별도):  ￼
	•	User-Agent 요구/예시:  ￼
	•	레이트 리밋 10rps:  ￼
	•	Form 4 2영업일 규정:  ￼
	•	13F 45일 규정:  ￼
	•	13G 마감 단축(2024-09-30 시행):  ￼
	•	OPRA 역할/라이선스:  ￼
	•	옵션 데이터 벤더(Polygon/Intrinio/Cboe DataShop):  ￼
	•	IEX Cloud 서비스 종료:  ￼
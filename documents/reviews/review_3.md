  XBRL 히트맵 데이터 메트릭 계산 문제를
  성공적으로 해결했습니다.

  발견된 문제들:

  1. XBRL Facts 수집 미구현: Filing은
  수집되었지만, XBRL facts를 파싱하고 저장하는
  로직이 실행되지 않고 있었습니다.
  2. 데이터베이스 타입 불일치: xbrl_facts
  테이블의 dimensions 컬럼이 JSONB 타입인데,
  JPA 엔티티는 String을 저장하려고 해서 타입
  에러 발생.

  해결 방법:

  1. XBRL Facts 수집 로직 통합:
    - DataIngestionService에 ingestXbrlFacts()
  메서드 추가
    - Company submissions 수집 후 자동으로 XBRL
   facts도 수집하도록 수정
    - SEC API의
  /api/xbrl/companyfacts/CIK{cik}.json
  엔드포인트 활용
  2. 데이터 검증 로직 추가:
    - validateXbrlFact() 메서드로 데이터 품질
  검증
    - NaN, Infinity, 미래 날짜, 너무 오래된
  날짜 등 필터링
  3. 데이터베이스 타입 수정:
    - dimensions 컬럼을 JSONB → TEXT로 변경하여
   호환성 확보

  테스트 결과:

  ✅ AAPL (Apple) 데이터 수집 성공:
  - 1,887개의 XBRL facts 파싱 및 저장
  - 6개의 메트릭 계산 (InventoryNet, Revenues,
  Operating Income, Equity, Revenue, Net
  Income)
  - 히트맵 API가 정상적으로 데이터 반환:
  {
    "filingId": 2245,
    "rows": [
      {"metric": "Revenue", "values": {"Abs":
  94036000000.00}, "zscore": 0.0},
      {"metric": "Net Income", "values":
  {"Abs": 23434000000.00}, "zscore": 0.0}
      ...
    ]
  }

  향후 개선 사항:

  1. 다중 Filing XBRL 매핑: 현재는 최신 filing
  하나에만 XBRL facts를 저장하므로, 각 filing의
   period에 맞는 facts를 정확히 매핑하는 로직
  필요
  2. QoQ/YoY 계산: 이전 filing과 비교하여
  변화율 계산 (현재는 Abs만 표시)
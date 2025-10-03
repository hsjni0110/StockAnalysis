/**
 * 주식 초보자를 위한 친절한 용어 설명 모음
 */

export interface HelpText {
  title: string;
  description: string;
  example?: string;
}

// Form Types (SEC 제출 서류 유형)
export const FORM_TYPES: Record<string, HelpText> = {
  '10-K': {
    title: '10-K (연간 보고서)',
    description: '기업이 1년에 한 번 제출하는 가장 상세한 재무 보고서입니다. 기업의 전반적인 사업 현황, 재무 상태, 리스크 요인 등을 포함합니다.',
    example: '애플이 2023년 회계연도 실적을 정리해서 SEC에 제출하는 문서',
  },
  '10-Q': {
    title: '10-Q (분기 보고서)',
    description: '기업이 분기마다 제출하는 재무 보고서입니다. 10-K보다는 간략하지만 최신 실적을 빠르게 확인할 수 있습니다.',
    example: '테슬라가 2024년 1분기 실적을 발표하며 제출하는 문서',
  },
  '8-K': {
    title: '8-K (수시 공시)',
    description: '중요한 사건이 발생했을 때 즉시 제출하는 보고서입니다. CEO 교체, 대규모 인수합병, 회계감사 변경 등 투자자에게 중요한 정보를 담고 있습니다.',
    example: '마이크로소프트가 새로운 CEO를 임명했을 때 제출하는 문서',
  },
  '4': {
    title: 'Form 4 (내부자 거래 보고서)',
    description: '임원, 이사 등 회사 내부자가 자사 주식을 사고팔 때 제출하는 보고서입니다. 내부자의 매수는 긍정적 신호로 해석되기도 합니다.',
    example: 'CEO가 자사 주식 1만 주를 매수했을 때 제출하는 문서',
  },
  '13F-HR': {
    title: '13F (기관투자자 보유 현황)',
    description: '1억 달러 이상을 운용하는 기관투자자가 분기마다 제출하는 보유 주식 목록입니다. 워렌 버핏 같은 유명 투자자의 포트폴리오를 확인할 수 있습니다.',
    example: '버크셔 해서웨이가 보유 중인 애플, 코카콜라 등의 주식 수량을 공개하는 문서',
  },
};

// Filing Sections (보고서 섹션)
export const FILING_SECTIONS: Record<string, HelpText> = {
  Item1A: {
    title: 'Item 1A: Risk Factors (리스크 요인)',
    description: '회사가 직면한 위험 요소들을 설명하는 섹션입니다. 경쟁 심화, 규제 변화, 소송 가능성 등 투자 전 반드시 확인해야 할 내용입니다.',
    example: '반도체 기업이 "중국과의 무역 분쟁으로 매출이 감소할 수 있다"고 경고하는 내용',
  },
  Item7: {
    title: 'Item 7: MD&A (경영진 논의 및 분석)',
    description: '경영진이 직접 회사의 재무 상태와 실적을 설명하는 섹션입니다. 매출이 늘어난 이유, 비용이 증가한 배경 등을 경영진의 시각에서 풀어냅니다.',
    example: '매출이 20% 증가한 이유를 "신제품 출시와 해외 시장 확대 덕분"이라고 설명',
  },
  Item7A: {
    title: 'Item 7A: Market Risk (시장 리스크)',
    description: '환율, 이자율, 원자재 가격 변동이 회사에 미치는 영향을 분석하는 섹션입니다. 글로벌 기업이나 원자재 의존 기업에서 특히 중요합니다.',
    example: '달러 강세로 해외 매출이 줄어들 가능성을 분석한 내용',
  },
};

// Delta Operations (변화 유형)
export const DELTA_OPERATIONS: Record<string, HelpText> = {
  INSERT: {
    title: 'INSERT (추가)',
    description: '이번 보고서에 새로 추가된 내용입니다. 새로운 리스크, 사업 계획, 또는 공시 항목이 생겼다는 의미입니다.',
    example: '"AI 기술 개발 경쟁 심화" 문단이 리스크 요인에 새로 추가됨',
  },
  DELETE: {
    title: 'DELETE (삭제)',
    description: '이전 보고서에 있었지만 이번에는 삭제된 내용입니다. 리스크가 해소되었거나 더 이상 중요하지 않다고 판단한 경우입니다.',
    example: '"팬데믹으로 인한 공급망 차질" 문단이 삭제됨 (리스크 완화)',
  },
  MODIFY: {
    title: 'MODIFY (수정)',
    description: '이전 보고서의 내용이 일부 변경된 경우입니다. 상황 변화나 추가 정보를 반영한 것으로, 변화의 정도를 점수로 표시합니다.',
    example: '"소송 진행 중"에서 "소송 1심 패소"로 내용이 업데이트됨',
  },
};

// XBRL Metrics (재무 지표)
export const XBRL_METRICS: Record<string, HelpText> = {
  Revenue: {
    title: 'Revenue (매출)',
    description: '기업이 상품이나 서비스를 판매해서 벌어들인 총 수익입니다. 매출 증가는 일반적으로 긍정적 신호입니다.',
    example: '애플이 아이폰, 맥북 등을 판매해서 번 전체 금액',
  },
  OperatingIncomeLoss: {
    title: 'Operating Income (영업이익)',
    description: '매출에서 영업 비용(인건비, 임대료 등)을 뺀 이익입니다. 기업의 본업 수익성을 보여주는 핵심 지표입니다.',
    example: '매출 100억에서 인건비, 광고비 등 80억을 뺀 영업이익 20억',
  },
  NetIncomeLoss: {
    title: 'Net Income (순이익)',
    description: '모든 비용과 세금을 제하고 최종적으로 남은 이익입니다. 주주에게 돌아갈 수 있는 실질적인 이익을 의미합니다.',
    example: '영업이익 20억에서 이자, 세금 5억을 뺀 순이익 15억',
  },
  Assets: {
    title: 'Assets (자산)',
    description: '기업이 보유한 모든 재산의 총합입니다. 현금, 부동산, 설비, 재고 등이 포함됩니다.',
    example: '애플이 보유한 현금 200억 달러 + 공장 설비 + 특허권 등',
  },
  Liabilities: {
    title: 'Liabilities (부채)',
    description: '기업이 갚아야 할 빚의 총합입니다. 은행 대출, 사채, 미지급금 등이 포함됩니다.',
    example: '회사가 은행에서 빌린 100억 + 납품업체에 아직 안 준 돈 20억',
  },
  StockholdersEquity: {
    title: 'Stockholders Equity (자본)',
    description: '자산에서 부채를 뺀 순수 자본입니다. 회사의 진짜 가치를 보여주는 지표로, 높을수록 재무 건전성이 좋습니다.',
    example: '자산 300억 - 부채 100억 = 자본 200억 (주주의 몫)',
  },
  CashAndCashEquivalents: {
    title: 'Cash (현금 및 현금성 자산)',
    description: '기업이 즉시 사용할 수 있는 현금과 단기 금융상품입니다. 현금이 많으면 위기 대응력이 높습니다.',
    example: '은행 예금 50억 + 3개월 만기 정기예금 30억',
  },
  Inventory: {
    title: 'Inventory (재고자산)',
    description: '아직 팔지 못한 제품이나 원자재의 가치입니다. 재고가 급증하면 판매 부진 신호일 수 있습니다.',
    example: '창고에 쌓여있는 완제품 10억 + 원자재 5억',
  },
  PropertyPlantAndEquipment: {
    title: 'PP&E (유형자산)',
    description: '공장, 건물, 설비 등 물리적 자산의 가치입니다. 제조업에서 특히 중요한 지표입니다.',
    example: '반도체 공장 건물 500억 + 생산 설비 300억',
  },
  ResearchAndDevelopmentExpense: {
    title: 'R&D (연구개발비)',
    description: '신제품 개발이나 기술 연구에 쓰는 비용입니다. IT, 제약업에서 높은 R&D는 미래 성장 가능성을 시사합니다.',
    example: '신약 개발에 투입한 100억 + 신기술 연구비 50억',
  },
  CapitalExpenditure: {
    title: 'CapEx (자본적 지출)',
    description: '공장, 설비, 부동산 등 장기 자산에 투자한 금액입니다. 높으면 미래 성장을 준비 중이라는 신호입니다.',
    example: '새로운 공장 건설에 200억 + 생산라인 증설에 100억 투자',
  },
  FreeCashFlow: {
    title: 'Free Cash Flow (잉여현금흐름)',
    description: '영업활동으로 벌어들인 현금에서 필수 투자를 뺀 금액입니다. 배당이나 자사주 매입에 쓸 수 있는 여유 자금입니다.',
    example: '영업현금 300억 - 필수 설비투자 100억 = 잉여현금 200억',
  },
};

// Change Metrics (변화율 지표)
export const CHANGE_METRICS: Record<string, HelpText> = {
  QoQ: {
    title: 'QoQ (Quarter-over-Quarter, 전분기 대비)',
    description: '직전 분기와 비교한 변화율입니다. 단기 트렌드를 파악하는 데 유용합니다.',
    example: '2024년 2분기 매출이 1분기보다 10% 증가 → QoQ +10%',
  },
  YoY: {
    title: 'YoY (Year-over-Year, 전년 동기 대비)',
    description: '작년 같은 분기와 비교한 변화율입니다. 계절적 요인을 제거하고 장기 성장세를 볼 수 있습니다.',
    example: '2024년 2분기 매출이 2023년 2분기보다 20% 증가 → YoY +20%',
  },
  'Z-Score': {
    title: 'Z-Score (이상치 점수)',
    description: '통계적으로 얼마나 비정상적인 변화인지를 나타내는 점수입니다. 2 이상이면 이례적인 변화로 주의가 필요합니다.',
    example: 'Z-Score 2.5 → 매출이 평소보다 매우 급격하게 증가 (또는 감소)',
  },
};

// General Terms (일반 용어)
export const GENERAL_TERMS: Record<string, HelpText> = {
  CIK: {
    title: 'CIK (Central Index Key)',
    description: 'SEC가 기업에 부여한 고유 식별번호입니다. 주식 티커와 달리 절대 변하지 않습니다.',
    example: '애플의 CIK는 0000320193 (티커 AAPL과 연결됨)',
  },
  AccessionNumber: {
    title: 'Accession Number (접수번호)',
    description: 'SEC에 제출된 각 문서의 고유 번호입니다. 특정 보고서를 정확히 찾을 때 사용합니다.',
    example: '0000320193-24-000123 형식으로 표시',
  },
  PeriodEnd: {
    title: 'Period End (기간 종료일)',
    description: '보고서가 다루는 회계 기간의 마지막 날입니다. 실적이 언제까지의 데이터인지를 보여줍니다.',
    example: '2024년 3월 31일 → 2024년 1분기 실적 보고서',
  },
  FiledAt: {
    title: 'Filed At (제출일)',
    description: '실제로 SEC에 보고서를 제출한 날짜입니다. 보통 기간 종료 후 몇 주 뒤에 제출됩니다.',
    example: '3월 31일 마감 → 4월 30일에 10-Q 제출',
  },
  Score: {
    title: 'Score (중요도 점수)',
    description: '변화의 중요도를 0~1 점수로 나타냅니다. 높을수록 투자자가 주목해야 할 중요한 변화입니다.',
    example: '점수 0.9 → 매우 중요한 변화 / 점수 0.2 → 경미한 변화',
  },
};

/**
 * 키워드로 도움말 검색하는 헬퍼 함수
 */
export function getHelpText(keyword: string): HelpText | undefined {
  return (
    FORM_TYPES[keyword] ||
    FILING_SECTIONS[keyword] ||
    DELTA_OPERATIONS[keyword] ||
    XBRL_METRICS[keyword] ||
    CHANGE_METRICS[keyword] ||
    GENERAL_TERMS[keyword]
  );
}

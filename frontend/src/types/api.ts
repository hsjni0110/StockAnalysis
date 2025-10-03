export interface IngestRequest {
  symbols?: string[];
  mode: 'today' | 'latest';
}

export interface IngestResponse {
  logId: string;
  totalProcessed: number;
  totalInserted: number;
  totalSkipped: number;
  warnings: string[] | null;
  status: 'completed' | 'failed' | 'in_progress';
}

export interface IngestStatus {
  id: string;
  requestTimestamp: string;
  mode: 'today' | 'latest';
  symbols: string[] | null;
  totalProcessed: number;
  totalInserted: number;
  totalSkipped: number;
  completedAt?: string;
  status: 'completed' | 'failed' | 'in_progress';
  warnings: string[] | null;
}

export interface TickerInfo {
  cik: string;
  ticker: string;
  name: string;
  exchange: string;
}

export interface Filing {
  id: number;
  cik: string;
  accessionNo: string;
  form: string;
  filedAt: string;
  periodEnd?: string;
  primaryDocUrl: string;
  source: 'daily-index' | 'submissions';
  ticker?: string;
  companyName?: string;
  createdAt: string;
}

export interface FilingStats {
  cik: string;
  ticker: string;
  totalFilings: number;
  latestFiling?: string;
  forms: Record<string, number>;
}

export interface ApiError {
  message: string;
  status: number;
  timestamp: string;
}

// DeltaMap types
export interface FilingDelta {
  id: number;
  filingId: number;
  section: string;
  operation: 'INSERT' | 'DELETE' | 'MODIFY';
  snippet: string;
  score: number;
  createdAt: string;
}

export interface FilingSection {
  id: number;
  filingId: number;
  section: string;
  text: string;
  textHash: string;
  charCount: number;
  createdAt: string;
}

export interface DeltaMapResponse {
  current: FilingInfo;
  previous?: FilingInfo;
  totalChanges: number;
  insertCount: number;
  deleteCount: number;
  modifyCount: number;
  deltas: FilingDelta[];
}

export interface FilingInfo {
  filingId: number;
  form: string;
  periodEnd?: string;
  filedAt: string;
  accessionNo: string;
  primaryDocUrl: string;
}

export interface XbrlHeatmapData {
  filingId: number;
  rows: HeatmapRow[];
}

export interface HeatmapRow {
  metric: string;
  values: Record<string, number>; // basis -> value (e.g., "QoQ": 5.2, "YoY": 12.1)
  zScore: number;
}

export interface ChangeBadge {
  type: 'section' | 'xbrl';
  label: string;
  severity: 'low' | 'medium' | 'high';
}

export interface TickerDeltaSummary {
  symbol: string;
  companyName: string;
  latestFiling: FilingInfo;
  totalChanges: number;
  changeBadges: ChangeBadge[];
}
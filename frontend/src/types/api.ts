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
import axios from 'axios';
import type { AxiosResponse } from 'axios';
import type {
  IngestRequest,
  IngestResponse,
  IngestStatus,
  TickerInfo,
  Filing,
  FilingStats,
  ApiError
} from '../types/api';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const apiError: ApiError = {
      message: error.response?.data?.message || error.message || 'Unknown error',
      status: error.response?.status || 500,
      timestamp: new Date().toISOString(),
    };
    return Promise.reject(apiError);
  }
);

export class ApiService {
  static async triggerRefresh(request: IngestRequest): Promise<IngestResponse> {
    const response: AxiosResponse<IngestResponse> = await apiClient.post('/ingest/refresh', request);
    return response.data;
  }

  static async getIngestStatus(requestId: string): Promise<IngestStatus> {
    const response: AxiosResponse<IngestStatus> = await apiClient.get(`/ingest/status/${requestId}`);
    return response.data;
  }

  static async getIngestStatusList(limit = 10): Promise<IngestStatus[]> {
    const response: AxiosResponse<IngestStatus[]> = await apiClient.get('/ingest/status', {
      params: { limit }
    });
    return response.data;
  }

  static async checkIngestHealth(): Promise<string> {
    const response: AxiosResponse<string> = await apiClient.get('/ingest/health');
    return response.data;
  }

  static async resolveTicker(symbol: string): Promise<TickerInfo> {
    const response: AxiosResponse<TickerInfo> = await apiClient.get('/ticker/resolve', {
      params: { symbol }
    });
    return response.data;
  }

  static async getLatestFilings(symbol: string, limit = 10): Promise<Filing[]> {
    const response: AxiosResponse<Filing[]> = await apiClient.get(`/filings/${symbol}/latest`, {
      params: { limit }
    });
    return response.data;
  }

  static async getRecentFilings(params: {
    forms?: string;
    days?: number;
    limit?: number;
  } = {}): Promise<Filing[]> {
    const response: AxiosResponse<Filing[]> = await apiClient.get('/filings/recent', {
      params
    });
    return response.data;
  }

  static async getFilingStats(symbol: string): Promise<FilingStats> {
    const response: AxiosResponse<FilingStats> = await apiClient.get(`/filings/stats/${symbol}`);
    return response.data;
  }
}

export default ApiService;
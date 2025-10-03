import { useQuery, useMutation } from '@tanstack/react-query';
import type {
  DeltaMapResponse,
  XbrlHeatmapData,
  TickerDeltaSummary,
  FilingSection
} from '../types/api';

const API_BASE_URL = 'http://localhost:8080/api/deltamap';

export function useAnalyzeFiling(filingId: number) {
  return useMutation({
    mutationFn: async () => {
      const response = await fetch(`${API_BASE_URL}/filings/${filingId}/analyze`, {
        method: 'POST',
      });

      if (!response.ok) {
        throw new Error('Failed to analyze filing');
      }

      return response.json();
    },
  });
}

export function useFilingSections(filingId: number, section?: string) {
  return useQuery<{ filingId: number; sections: FilingSection[] }>({
    queryKey: ['filing-sections', filingId, section],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (section) {
        params.append('section', section);
      }

      const response = await fetch(
        `${API_BASE_URL}/filings/${filingId}/sections?${params.toString()}`
      );

      if (!response.ok) {
        throw new Error('Failed to fetch sections');
      }

      return response.json();
    },
    enabled: !!filingId,
  });
}

export function useFilingDeltas(filingId: number, section?: string) {
  return useQuery<DeltaMapResponse>({
    queryKey: ['filing-deltas', filingId, section],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (section) {
        params.append('section', section);
      }

      const response = await fetch(
        `${API_BASE_URL}/filings/${filingId}/deltas?${params.toString()}`
      );

      if (!response.ok) {
        throw new Error('Failed to fetch deltas');
      }

      return response.json();
    },
    enabled: !!filingId,
  });
}

export function useXbrlHeatmap(filingId: number) {
  return useQuery<XbrlHeatmapData>({
    queryKey: ['xbrl-heatmap', filingId],
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/filings/${filingId}/xbrl-heatmap`);

      if (!response.ok) {
        if (response.status === 204) {
          return { filingId, rows: [] };
        }
        throw new Error('Failed to fetch XBRL heatmap');
      }

      return response.json();
    },
    enabled: !!filingId,
  });
}

export function useTickerDeltaSummary(symbol: string) {
  return useQuery<TickerDeltaSummary>({
    queryKey: ['ticker-delta-summary', symbol],
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/tickers/${symbol}/summary`);

      if (!response.ok) {
        throw new Error('Failed to fetch ticker delta summary');
      }

      return response.json();
    },
    enabled: !!symbol,
  });
}

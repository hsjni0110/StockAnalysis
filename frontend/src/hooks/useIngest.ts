import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiService } from '../services/api';
import type { IngestRequest, IngestResponse, IngestStatus } from '../types/api';

export const useIngestRefresh = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: IngestRequest) => ApiService.triggerRefresh(request),
    onSuccess: (data: IngestResponse) => {
      queryClient.invalidateQueries({ queryKey: ['ingestStatus'] });
      queryClient.setQueryData(['ingestStatus', data.logId], data);
    },
  });
};

export const useIngestStatus = (requestId: string | null, enabled = true) => {
  return useQuery({
    queryKey: ['ingestStatus', requestId],
    queryFn: () => ApiService.getIngestStatus(requestId!),
    enabled: enabled && !!requestId,
    refetchInterval: (query) => {
      return query.state.data?.status === 'in_progress' ? 2000 : false;
    },
  });
};

export const useIngestStatusList = (limit = 10) => {
  return useQuery({
    queryKey: ['ingestStatus', 'list', limit],
    queryFn: () => ApiService.getIngestStatusList(limit),
  });
};

export const useIngestHealth = () => {
  return useQuery({
    queryKey: ['ingestHealth'],
    queryFn: () => ApiService.checkIngestHealth(),
    refetchInterval: 30000, // Check every 30 seconds
  });
};

export const useTickerResolve = (symbol: string | null, enabled = true) => {
  return useQuery({
    queryKey: ['ticker', symbol],
    queryFn: () => ApiService.resolveTicker(symbol!),
    enabled: enabled && !!symbol,
  });
};

export const useLatestFilings = (symbol: string | null, limit = 10, enabled = true) => {
  return useQuery({
    queryKey: ['filings', symbol, 'latest', limit],
    queryFn: () => ApiService.getLatestFilings(symbol!, limit),
    enabled: enabled && !!symbol,
  });
};

export const useRecentFilings = (params: {
  forms?: string;
  days?: number;
  limit?: number;
} = {}, enabled = true) => {
  return useQuery({
    queryKey: ['filings', 'recent', params],
    queryFn: () => ApiService.getRecentFilings(params),
    enabled,
  });
};

export const useFilingStats = (symbol: string | null, enabled = true) => {
  return useQuery({
    queryKey: ['filings', 'stats', symbol],
    queryFn: () => ApiService.getFilingStats(symbol!),
    enabled: enabled && !!symbol,
  });
};
import React, { useState } from 'react';
import {
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  TextField,
  Chip,
  Box,
  Typography,
  Alert,
} from '@mui/material';
import { Refresh as RefreshIcon } from '@mui/icons-material';
import { useIngestRefresh } from '../hooks/useIngest';
import type { IngestRequest } from '../types/api';

interface RefreshButtonProps {
  onRefreshStart?: (logId: string) => void;
  onRefreshComplete?: () => void;
}

export const RefreshButton: React.FC<RefreshButtonProps> = ({
  onRefreshStart,
  onRefreshComplete,
}) => {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<'today' | 'latest'>('latest');
  const [symbolInput, setSymbolInput] = useState('');
  const [symbols, setSymbols] = useState<string[]>([]);

  const { mutate: triggerRefresh, isPending, error } = useIngestRefresh();

  const handleAddSymbol = () => {
    const trimmedSymbol = symbolInput.trim().toUpperCase();
    if (trimmedSymbol && !symbols.includes(trimmedSymbol)) {
      setSymbols([...symbols, trimmedSymbol]);
      setSymbolInput('');
    }
  };

  const handleRemoveSymbol = (symbolToRemove: string) => {
    setSymbols(symbols.filter(symbol => symbol !== symbolToRemove));
  };

  const handleKeyPress = (event: React.KeyboardEvent) => {
    if (event.key === 'Enter') {
      handleAddSymbol();
    }
  };

  const handleRefresh = () => {
    const request: IngestRequest = {
      mode,
      symbols: symbols.length > 0 ? symbols : undefined,
    };

    triggerRefresh(request, {
      onSuccess: (response) => {
        setOpen(false);
        onRefreshStart?.(response.logId);
        if (response.status === 'completed') {
          onRefreshComplete?.();
        }
      },
    });
  };

  const handleClose = () => {
    if (!isPending) {
      setOpen(false);
    }
  };

  return (
    <>
      <Button
        variant="contained"
        startIcon={<RefreshIcon />}
        onClick={() => setOpen(true)}
        disabled={isPending}
      >
        갱신
      </Button>

      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle>데이터 갱신</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <FormControl fullWidth>
              <InputLabel>갱신 모드</InputLabel>
              <Select
                value={mode}
                label="갱신 모드"
                onChange={(e) => setMode(e.target.value as 'today' | 'latest')}
                disabled={isPending}
              >
                <MenuItem value="latest">최신 데이터</MenuItem>
                <MenuItem value="today">오늘자 데이터</MenuItem>
              </Select>
            </FormControl>

            <Typography variant="body2" color="text.secondary">
              {mode === 'latest'
                ? '각 종목의 최신 submissions을 수집합니다.'
                : '오늘자 daily-index 기반으로 수집합니다.'
              }
            </Typography>

            <Box>
              <TextField
                fullWidth
                label="종목 심볼 (선택사항)"
                placeholder="예: AAPL, NVDA, MSFT"
                value={symbolInput}
                onChange={(e) => setSymbolInput(e.target.value)}
                onKeyPress={handleKeyPress}
                disabled={isPending}
                helperText="종목을 입력하고 Enter를 누르세요. 비워두면 전체 종목을 대상으로 합니다."
              />

              {symbols.length > 0 && (
                <Box sx={{ mt: 1, display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                  {symbols.map((symbol) => (
                    <Chip
                      key={symbol}
                      label={symbol}
                      onDelete={() => handleRemoveSymbol(symbol)}
                      disabled={isPending}
                      size="small"
                    />
                  ))}
                </Box>
              )}
            </Box>

            {error && (
              <Alert severity="error">
                {error.message}
              </Alert>
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} disabled={isPending}>
            취소
          </Button>
          <Button
            onClick={handleRefresh}
            variant="contained"
            disabled={isPending}
          >
            {isPending ? '갱신 중...' : '갱신 시작'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
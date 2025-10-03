import React from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  IconButton,
  Tooltip,
} from '@mui/material';
import {
  OpenInNew as OpenInNewIcon,
  Description as DescriptionIcon,
} from '@mui/icons-material';
import type { Filing } from '../types/api';

interface FilingCardProps {
  filing: Filing;
}

export const FilingCard: React.FC<FilingCardProps> = ({ filing }) => {
  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  const getFormColor = (form: string) => {
    if (form.startsWith('10-')) return 'primary';
    if (form.startsWith('8-')) return 'secondary';
    if (form === '4') return 'warning';
    if (form.startsWith('13')) return 'info';
    return 'default';
  };

  const handleOpenDocument = () => {
    window.open(filing.primaryDocUrl, '_blank', 'noopener,noreferrer');
  };

  return (
    <Card variant="outlined" sx={{ mb: 1 }}>
      <CardContent sx={{ py: 2 }}>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start">
          <Box flex={1}>
            <Box display="flex" alignItems="center" gap={1} mb={1}>
              <Chip
                label={filing.form}
                color={getFormColor(filing.form)}
                size="small"
              />
              {filing.ticker && (
                <Typography variant="body2" color="primary.main" fontWeight="bold">
                  {filing.ticker}
                </Typography>
              )}
              {filing.companyName && (
                <Typography variant="body2" color="text.primary">
                  {filing.companyName}
                </Typography>
              )}
            </Box>

            <Box display="flex" alignItems="center" gap={1} mb={1}>
              <Typography variant="body2" color="text.secondary">
                CIK: {filing.cik}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                접수번호: {filing.accessionNo}
              </Typography>
            </Box>

            <Box display="flex" gap={2} mb={1}>
              <Typography variant="body2" color="text.secondary">
                <strong>제출일:</strong> {formatDate(filing.filedAt)}
              </Typography>
              {filing.periodEnd && (
                <Typography variant="body2" color="text.secondary">
                  <strong>기간 종료일:</strong> {formatDate(filing.periodEnd)}
                </Typography>
              )}
            </Box>

            <Box display="flex" justify="space-between" alignItems="center">
              <Typography variant="caption" color="text.secondary">
                수집 시간: {formatDate(filing.createdAt)}
              </Typography>
              <Chip
                label={filing.source === 'daily-index' ? '일일 인덱스' : '제출 API'}
                variant="outlined"
                size="small"
              />
            </Box>
          </Box>

          <Box display="flex" flexDirection="column" alignItems="center">
            <Tooltip title="원문 보기">
              <IconButton
                onClick={handleOpenDocument}
                color="primary"
                size="small"
              >
                <OpenInNewIcon />
              </IconButton>
            </Tooltip>
            <Typography variant="caption" color="text.secondary">
              원문
            </Typography>
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
};
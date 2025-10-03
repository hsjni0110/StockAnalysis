import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
  IconButton,
  Tooltip,
  Button,
} from '@mui/material';
import {
  OpenInNew as OpenInNewIcon,
  CompareArrows as CompareArrowsIcon,
} from '@mui/icons-material';
import type { Filing } from '../types/api';
import { HelpTooltip } from './HelpTooltip';
import { FORM_TYPES, GENERAL_TERMS } from '../constants/helpTexts';

interface FilingCardProps {
  filing: Filing;
}

export const FilingCard: React.FC<FilingCardProps> = ({ filing }) => {
  const navigate = useNavigate();

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

  const handleViewDelta = () => {
    navigate(`/deltamap/${filing.id}`);
  };

  const canShowDelta = filing.form.match(/10-[KQ]/); // Only show delta for 10-K and 10-Q
  const formHelp = FORM_TYPES[filing.form];

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
              {formHelp && (
                <HelpTooltip
                  title={formHelp.title}
                  description={formHelp.description}
                  example={formHelp.example}
                  size="small"
                  inline
                />
              )}
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
              <Box display="flex" alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  CIK: {filing.cik}
                </Typography>
                <HelpTooltip
                  title={GENERAL_TERMS.CIK.title}
                  description={GENERAL_TERMS.CIK.description}
                  example={GENERAL_TERMS.CIK.example}
                  size="small"
                  inline
                />
              </Box>
              <Box display="flex" alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  접수번호: {filing.accessionNo}
                </Typography>
                <HelpTooltip
                  title={GENERAL_TERMS.AccessionNumber.title}
                  description={GENERAL_TERMS.AccessionNumber.description}
                  example={GENERAL_TERMS.AccessionNumber.example}
                  size="small"
                  inline
                />
              </Box>
            </Box>

            <Box display="flex" gap={2} mb={1}>
              <Box display="flex" alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  <strong>제출일:</strong> {formatDate(filing.filedAt)}
                </Typography>
                <HelpTooltip
                  title={GENERAL_TERMS.FiledAt.title}
                  description={GENERAL_TERMS.FiledAt.description}
                  example={GENERAL_TERMS.FiledAt.example}
                  size="small"
                  inline
                />
              </Box>
              {filing.periodEnd && (
                <Box display="flex" alignItems="center">
                  <Typography variant="body2" color="text.secondary">
                    <strong>기간 종료일:</strong> {formatDate(filing.periodEnd)}
                  </Typography>
                  <HelpTooltip
                    title={GENERAL_TERMS.PeriodEnd.title}
                    description={GENERAL_TERMS.PeriodEnd.description}
                    example={GENERAL_TERMS.PeriodEnd.example}
                    size="small"
                    inline
                  />
                </Box>
              )}
            </Box>

            <Box display="flex" justifyContent="space-between" alignItems="center">
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

          <Box display="flex" flexDirection="column" gap={1}>
            {canShowDelta && (
              <Button
                variant="outlined"
                size="small"
                startIcon={<CompareArrowsIcon />}
                onClick={handleViewDelta}
              >
                변화보기
              </Button>
            )}
            <Tooltip title="원문 보기">
              <IconButton
                onClick={handleOpenDocument}
                color="primary"
                size="small"
              >
                <OpenInNewIcon />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
};
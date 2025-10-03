import React from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  LinearProgress,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  CircularProgress,
  Chip,
  Alert,
} from '@mui/material';
import {
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
} from '@mui/icons-material';
import { useIngestStatus } from '../hooks/useIngest';

interface ProgressIndicatorProps {
  requestId: string | null;
  onComplete?: () => void;
}

const steps = [
  { key: 'mapping', label: '티커 매핑', description: '심볼을 CIK로 변환' },
  { key: 'indexing', label: '인덱스 조회', description: '최신 제출 인덱스 확인' },
  { key: 'collecting', label: '데이터 수집', description: 'SEC 원문 및 메타데이터 수집' },
  { key: 'normalizing', label: '정규화', description: '데이터 파싱 및 정규화' },
  { key: 'saving', label: '저장', description: '데이터베이스 저장' },
];

export const ProgressIndicator: React.FC<ProgressIndicatorProps> = ({
  requestId,
  onComplete,
}) => {
  const { data: status, isLoading, error } = useIngestStatus(requestId);

  React.useEffect(() => {
    if (status?.status === 'completed' || status?.status === 'failed') {
      onComplete?.();
    }
  }, [status?.status, onComplete]);

  if (!requestId) {
    return null;
  }

  if (isLoading) {
    return (
      <Card>
        <CardContent>
          <Box display="flex" alignItems="center" gap={2}>
            <CircularProgress size={24} />
            <Typography>상태 확인 중...</Typography>
          </Box>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <CardContent>
          <Alert severity="error" icon={<ErrorIcon />}>
            상태 조회 실패: {error.message}
          </Alert>
        </CardContent>
      </Card>
    );
  }

  if (!status) {
    return null;
  }

  const getActiveStep = () => {
    if (status.status === 'completed') return steps.length;
    if (status.status === 'failed') return -1;
    return 2; // 실제로는 백엔드에서 더 상세한 단계 정보를 받아야 함
  };

  const activeStep = getActiveStep();
  const isCompleted = status.status === 'completed';
  const isFailed = status.status === 'failed';

  return (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">
            데이터 수집 진행 상황
          </Typography>
          <Chip
            label={
              isCompleted
                ? `완료 - ${status.totalInserted || 0}건 갱신`
                : isFailed
                ? '실패'
                : '진행 중'
            }
            color={isCompleted ? 'success' : isFailed ? 'error' : 'primary'}
            icon={
              isCompleted ? (
                <CheckCircleIcon />
              ) : isFailed ? (
                <ErrorIcon />
              ) : (
                <CircularProgress size={16} color="inherit" />
              )
            }
          />
        </Box>

        <Box mb={2}>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            요청 ID: {status.id}
          </Typography>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            모드: {status.mode === 'today' ? '오늘자 데이터' : '최신 데이터'}
          </Typography>
          {status.symbols && status.symbols.length > 0 && (
            <Typography variant="body2" color="text.secondary">
              대상 종목: {status.symbols.join(', ')}
            </Typography>
          )}
        </Box>

        {!isCompleted && !isFailed && (
          <LinearProgress sx={{ mb: 2 }} />
        )}

        <Stepper activeStep={activeStep} orientation="vertical">
          {steps.map((step, index) => (
            <Step key={step.key} completed={index < activeStep}>
              <StepLabel>
                <Typography variant="subtitle2">{step.label}</Typography>
              </StepLabel>
              <StepContent>
                <Typography variant="body2" color="text.secondary">
                  {step.description}
                </Typography>
              </StepContent>
            </Step>
          ))}
        </Stepper>

        {status.warnings && status.warnings.length > 0 && (
          <Box mt={2}>
            <Alert severity="warning">
              <Typography variant="subtitle2" gutterBottom>
                경고 사항:
              </Typography>
              {status.warnings.map((warning, index) => (
                <Typography key={index} variant="body2">
                  • {warning}
                </Typography>
              ))}
            </Alert>
          </Box>
        )}

        {isCompleted && (
          <Box mt={2}>
            <Alert severity="success">
              <Typography variant="subtitle2" gutterBottom>
                갱신 완료
              </Typography>
              <Typography variant="body2">
                • 처리된 종목: {status.totalProcessed || 0}개
              </Typography>
              <Typography variant="body2">
                • 신규 추가: {status.totalInserted || 0}건
              </Typography>
              <Typography variant="body2">
                • 중복 제외: {status.totalSkipped || 0}건
              </Typography>
              {status.completedAt && (
                <Typography variant="body2">
                  • 완료 시간: {new Date(status.completedAt).toLocaleString()}
                </Typography>
              )}
            </Alert>
          </Box>
        )}

        {isFailed && (
          <Box mt={2}>
            <Alert severity="error">
              데이터 수집이 실패했습니다. 잠시 후 다시 시도해주세요.
            </Alert>
          </Box>
        )}
      </CardContent>
    </Card>
  );
};
import React, { useState } from 'react';
import {
  Container,
  Typography,
  Box,
  Card,
  CardContent,
  Tabs,
  Tab,
  Alert,
  Divider,
  ToggleButtonGroup,
  ToggleButton,
  Pagination,
} from '@mui/material';
import { RefreshButton } from './RefreshButton';
import { ProgressIndicator } from './ProgressIndicator';
import { FilingCard } from './FilingCard';
import { HelpTooltip } from './HelpTooltip';
import {
  useIngestStatusList,
  useIngestHealth,
  useRecentFilings
} from '../hooks/useIngest';
import { FORM_TYPES } from '../constants/helpTexts';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => {
  return (
    <div hidden={value !== index}>
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
};

export const Dashboard: React.FC = () => {
  const [activeTab, setActiveTab] = useState(0);
  const [activeRequestId, setActiveRequestId] = useState<string | null>(null);
  const [formFilter, setFormFilter] = useState<string[]>([]);
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 10;

  const { data: ingestHistory, refetch: refetchHistory } = useIngestStatusList(10);
  const { data: healthStatus, isError: healthError } = useIngestHealth();
  const { data: recentFilings } = useRecentFilings({ limit: 100, days: 365 });

  const handleRefreshStart = (logId: string) => {
    setActiveRequestId(logId);
    setActiveTab(1); // Switch to progress tab
  };

  const handleRefreshComplete = () => {
    refetchHistory();
    setTimeout(() => {
      setActiveRequestId(null);
      setActiveTab(2); // Switch to recent filings tab
    }, 2000);
  };

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleFormFilterChange = (_event: React.MouseEvent<HTMLElement>, newFormats: string[]) => {
    setFormFilter(newFormats);
    setCurrentPage(1); // Reset to first page when filter changes
  };

  const handlePageChange = (_event: React.ChangeEvent<unknown>, page: number) => {
    setCurrentPage(page);
  };

  // Filter filings based on selected form types
  const filteredFilings = recentFilings?.filter(filing => {
    if (formFilter.length === 0) return true;
    return formFilter.includes(filing.form);
  }) || [];

  // Calculate pagination
  const totalPages = Math.ceil(filteredFilings.length / itemsPerPage);
  const startIndex = (currentPage - 1) * itemsPerPage;
  const paginatedFilings = filteredFilings.slice(startIndex, startIndex + itemsPerPage);

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={4}>
        <Typography variant="h4" component="h1">
          Stock Delta System
        </Typography>
        <RefreshButton
          onRefreshStart={handleRefreshStart}
          onRefreshComplete={handleRefreshComplete}
        />
      </Box>

      {healthError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          수집 서비스 상태를 확인할 수 없습니다. 백엔드 서버가 실행 중인지 확인해주세요.
        </Alert>
      )}

      {healthStatus && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {healthStatus}
        </Alert>
      )}

      <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab label="개요" />
          <Tab label="진행 상황" />
          <Tab label="최근 파일링" />
          <Tab label="수집 이력" />
        </Tabs>
      </Box>

      <TabPanel value={activeTab} index={0}>
        <Box display="flex" gap={3} flexWrap="wrap">
          <Box flex={1} minWidth="300px">
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  시스템 개요
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  SEC EDGAR 데이터 수집 및 분석 시스템입니다.
                </Typography>
                <Divider sx={{ my: 2 }} />
                <Typography variant="subtitle2" gutterBottom>
                  주요 기능:
                </Typography>
                <Typography variant="body2" component="div">
                  • 실시간 SEC 파일링 수집
                  <br />
                  • 티커-CIK 매핑 관리
                  <br />
                  • XBRL 데이터 정규화
                  <br />
                  • Form 4/13F 인사이더 거래 추적
                </Typography>
              </CardContent>
            </Card>
          </Box>

          <Box flex={1} minWidth="300px">
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  사용 방법
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  [갱신] 버튼을 클릭하여 최신 데이터를 수집하세요.
                </Typography>
                <Divider sx={{ my: 2 }} />
                <Typography variant="subtitle2" gutterBottom>
                  수집 모드:
                </Typography>
                <Typography variant="body2" component="div">
                  • <strong>최신 데이터:</strong> 각 종목의 최신 submissions 수집
                  <br />
                  • <strong>오늘자 데이터:</strong> 당일 제출된 파일링만 수집
                </Typography>
              </CardContent>
            </Card>
          </Box>
        </Box>
      </TabPanel>

      <TabPanel value={activeTab} index={1}>
        {activeRequestId ? (
          <ProgressIndicator
            requestId={activeRequestId}
            onComplete={handleRefreshComplete}
          />
        ) : (
          <Alert severity="info">
            현재 진행 중인 수집 작업이 없습니다. [갱신] 버튼을 클릭하여 데이터 수집을 시작하세요.
          </Alert>
        )}
      </TabPanel>

      <TabPanel value={activeTab} index={2}>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={3} flexWrap="wrap" gap={2}>
          <Box display="flex" alignItems="center">
            <Typography variant="h6">
              최근 파일링
            </Typography>
            <HelpTooltip
              title="SEC 제출 서류란?"
              description="미국 증권거래위원회(SEC)에 상장 기업이 의무적으로 제출하는 공식 문서입니다. 기업의 재무 상태와 중요한 사건을 투명하게 공개합니다."
              example="애플, 테슬라 등 모든 미국 상장 기업이 분기마다 실적을 SEC에 보고해야 합니다."
              inline
            />
          </Box>
          <ToggleButtonGroup
            value={formFilter}
            onChange={handleFormFilterChange}
            aria-label="form type filter"
            size="small"
          >
            <ToggleButton value="10-K" aria-label="10-K">
              <Box display="flex" alignItems="center">
                10-K
                <HelpTooltip
                  title={FORM_TYPES['10-K'].title}
                  description={FORM_TYPES['10-K'].description}
                  example={FORM_TYPES['10-K'].example}
                  size="small"
                  inline
                />
              </Box>
            </ToggleButton>
            <ToggleButton value="10-Q" aria-label="10-Q">
              <Box display="flex" alignItems="center">
                10-Q
                <HelpTooltip
                  title={FORM_TYPES['10-Q'].title}
                  description={FORM_TYPES['10-Q'].description}
                  example={FORM_TYPES['10-Q'].example}
                  size="small"
                  inline
                />
              </Box>
            </ToggleButton>
            <ToggleButton value="8-K" aria-label="8-K">
              <Box display="flex" alignItems="center">
                8-K
                <HelpTooltip
                  title={FORM_TYPES['8-K'].title}
                  description={FORM_TYPES['8-K'].description}
                  example={FORM_TYPES['8-K'].example}
                  size="small"
                  inline
                />
              </Box>
            </ToggleButton>
            <ToggleButton value="4" aria-label="Form 4">
              <Box display="flex" alignItems="center">
                Form 4
                <HelpTooltip
                  title={FORM_TYPES['4'].title}
                  description={FORM_TYPES['4'].description}
                  example={FORM_TYPES['4'].example}
                  size="small"
                  inline
                />
              </Box>
            </ToggleButton>
            <ToggleButton value="13F-HR" aria-label="13F">
              <Box display="flex" alignItems="center">
                13F
                <HelpTooltip
                  title={FORM_TYPES['13F-HR'].title}
                  description={FORM_TYPES['13F-HR'].description}
                  example={FORM_TYPES['13F-HR'].example}
                  size="small"
                  inline
                />
              </Box>
            </ToggleButton>
          </ToggleButtonGroup>
        </Box>

        {recentFilings && recentFilings.length > 0 ? (
          <Box>
            {filteredFilings.length > 0 ? (
              <>
                <Typography variant="body2" color="text.secondary" mb={2}>
                  총 {filteredFilings.length}건의 파일링
                  {formFilter.length > 0 && ` (필터: ${formFilter.join(', ')})`}
                </Typography>
                {paginatedFilings.map((filing) => (
                  <FilingCard key={filing.id} filing={filing} />
                ))}
                {totalPages > 1 && (
                  <Box display="flex" justifyContent="center" mt={3}>
                    <Pagination
                      count={totalPages}
                      page={currentPage}
                      onChange={handlePageChange}
                      color="primary"
                      showFirstButton
                      showLastButton
                    />
                  </Box>
                )}
              </>
            ) : (
              <Alert severity="info">
                선택한 Form Type의 파일링이 없습니다. 다른 필터를 선택해주세요.
              </Alert>
            )}
          </Box>
        ) : (
          <Alert severity="info">
            최근 파일링 데이터가 없습니다. 데이터 수집을 먼저 실행해주세요.
          </Alert>
        )}
      </TabPanel>

      <TabPanel value={activeTab} index={3}>
        <Typography variant="h6" gutterBottom>
          수집 이력
        </Typography>
        {ingestHistory && ingestHistory.length > 0 ? (
          <Box display="flex" flexDirection="column" gap={2}>
            {ingestHistory.map((status) => (
              <Card variant="outlined" key={status.id}>
                <CardContent>
                  <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                    <Box>
                      <Typography variant="subtitle1" gutterBottom>
                        {status.mode === 'today' ? '오늘자 데이터' : '최신 데이터'} 수집
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        요청 시간: {new Date(status.requestTimestamp).toLocaleString()}
                      </Typography>
                      {status.completedAt && (
                        <Typography variant="body2" color="text.secondary">
                          완료 시간: {new Date(status.completedAt).toLocaleString()}
                        </Typography>
                      )}
                      {status.symbols && status.symbols.length > 0 && (
                        <Typography variant="body2" color="text.secondary">
                          대상 종목: {status.symbols.join(', ')}
                        </Typography>
                      )}
                    </Box>
                    <Box textAlign="right">
                      <Typography
                        variant="body2"
                        color={
                          status.status === 'completed' ? 'success.main' :
                          status.status === 'failed' ? 'error.main' :
                          'primary.main'
                        }
                        fontWeight="bold"
                      >
                        {status.status === 'completed' ? '완료' :
                         status.status === 'failed' ? '실패' : '진행 중'}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {status.totalInserted || 0}건 추가
                      </Typography>
                    </Box>
                  </Box>
                </CardContent>
              </Card>
            ))}
          </Box>
        ) : (
          <Alert severity="info">
            수집 이력이 없습니다.
          </Alert>
        )}
      </TabPanel>
    </Container>
  );
};
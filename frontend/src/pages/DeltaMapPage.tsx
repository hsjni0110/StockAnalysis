import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Container,
  Box,
  Typography,
  Tabs,
  Tab,
  Paper,
  Alert,
  CircularProgress,
  Button,
  Chip,
} from '@mui/material';
import { OpenInNew as OpenInNewIcon } from '@mui/icons-material';
import {
  useFilingDeltas,
  useNormalizedHeatmap,
  useNormalizeFiling,
  useNormalizationStats,
  useDataQuality,
  useAnalyzeFiling
} from '../hooks/useDeltaMap';
import { HelpTooltip } from '../components/HelpTooltip';
import { FILING_SECTIONS, DELTA_OPERATIONS, CHANGE_METRICS, XBRL_METRICS } from '../constants/helpTexts';

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

export const DeltaMapPage: React.FC = () => {
  const { filingId } = useParams<{ filingId: string }>();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedSection, setSelectedSection] = useState<string>('Item7');

  const filingIdNum = filingId ? parseInt(filingId, 10) : 0;

  const { data: deltaData, isLoading: deltasLoading, error: deltasError } = useFilingDeltas(filingIdNum, selectedSection);

  // Use normalized heatmap instead of legacy xbrl-heatmap
  const { data: heatmapData, isLoading: heatmapLoading } = useNormalizedHeatmap(filingIdNum);
  const { data: normStats } = useNormalizationStats(filingIdNum);
  const { data: dataQuality } = useDataQuality(filingIdNum);

  const analyzeMutation = useAnalyzeFiling(filingIdNum);
  const normalizeMutation = useNormalizeFiling(filingIdNum);

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleSectionChange = (section: string) => {
    setSelectedSection(section);
  };

  const handleAnalyze = () => {
    analyzeMutation.mutate();
  };

  if (!filingId) {
    return (
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Alert severity="error">Filing ID is required</Alert>
      </Container>
    );
  }

  if (deltasLoading) {
    return (
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
          <CircularProgress />
        </Box>
      </Container>
    );
  }

  if (deltasError) {
    return (
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Alert severity="error">
          Failed to load delta data. Please try analyzing the filing first.
        </Alert>
        <Box mt={2}>
          <Button
            variant="contained"
            onClick={handleAnalyze}
            disabled={analyzeMutation.isPending}
          >
            {analyzeMutation.isPending ? 'Analyzing...' : 'Analyze Filing'}
          </Button>
        </Box>
      </Container>
    );
  }

  const current = deltaData?.current;
  const previous = deltaData?.previous;

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      {/* Header */}
      <Box mb={4}>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Box display="flex" alignItems="center">
            <Typography variant="h4" component="h1">
              DeltaMap Analysis
            </Typography>
            <HelpTooltip
              title="DeltaMap이란?"
              description="이전 보고서와 비교하여 어떤 내용이 새로 추가되었고, 삭제되었고, 수정되었는지를 한눈에 보여주는 분석 도구입니다."
              example="2024년 2분기 보고서와 1분기 보고서를 비교하여 리스크 요인이 어떻게 변했는지 확인할 수 있습니다."
              inline
            />
          </Box>
          {current && (
            <Button
              variant="outlined"
              startIcon={<OpenInNewIcon />}
              href={current.primaryDocUrl}
              target="_blank"
              rel="noopener noreferrer"
            >
              View Original
            </Button>
          )}
        </Box>

        {/* Filing Info */}
        {current && (
          <Paper sx={{ p: 2, mb: 2 }}>
            <Box display="flex" gap={2} alignItems="center" flexWrap="wrap">
              <Chip label={current.form} color="primary" />
              <Typography variant="body2">
                <strong>Period:</strong>{' '}
                {current.periodEnd
                  ? new Date(current.periodEnd).toLocaleDateString()
                  : 'N/A'}
              </Typography>
              <Typography variant="body2">
                <strong>Filed:</strong>{' '}
                {new Date(current.filedAt).toLocaleDateString()}
              </Typography>
              <Typography variant="body2">
                <strong>Accession:</strong> {current.accessionNo}
              </Typography>
            </Box>

            {previous && (
              <Box mt={1} display="flex" gap={2} alignItems="center">
                <Typography variant="body2" color="text.secondary">
                  <strong>Comparing with:</strong> {previous.form} (
                  {previous.periodEnd
                    ? new Date(previous.periodEnd).toLocaleDateString()
                    : 'N/A'}
                  )
                </Typography>
              </Box>
            )}
          </Paper>
        )}

        {/* Change Summary */}
        {deltaData && (
          <Paper sx={{ p: 2, mb: 2 }}>
            <Box display="flex" gap={3} flexWrap="wrap">
              <Box>
                <Box display="flex" alignItems="center">
                  <Typography variant="caption" color="text.secondary">
                    Total Changes
                  </Typography>
                  <HelpTooltip
                    title="전체 변화 수"
                    description="이번 보고서에서 발견된 추가, 삭제, 수정의 총 개수입니다."
                    size="small"
                    inline
                  />
                </Box>
                <Typography variant="h6">{deltaData.totalChanges}</Typography>
              </Box>
              <Box>
                <Box display="flex" alignItems="center">
                  <Typography variant="caption" color="success.main">
                    Additions
                  </Typography>
                  <HelpTooltip
                    title={DELTA_OPERATIONS.INSERT.title}
                    description={DELTA_OPERATIONS.INSERT.description}
                    example={DELTA_OPERATIONS.INSERT.example}
                    size="small"
                    inline
                  />
                </Box>
                <Typography variant="h6" color="success.main">
                  {deltaData.insertCount}
                </Typography>
              </Box>
              <Box>
                <Box display="flex" alignItems="center">
                  <Typography variant="caption" color="error.main">
                    Deletions
                  </Typography>
                  <HelpTooltip
                    title={DELTA_OPERATIONS.DELETE.title}
                    description={DELTA_OPERATIONS.DELETE.description}
                    example={DELTA_OPERATIONS.DELETE.example}
                    size="small"
                    inline
                  />
                </Box>
                <Typography variant="h6" color="error.main">
                  {deltaData.deleteCount}
                </Typography>
              </Box>
              <Box>
                <Box display="flex" alignItems="center">
                  <Typography variant="caption" color="warning.main">
                    Modifications
                  </Typography>
                  <HelpTooltip
                    title={DELTA_OPERATIONS.MODIFY.title}
                    description={DELTA_OPERATIONS.MODIFY.description}
                    example={DELTA_OPERATIONS.MODIFY.example}
                    size="small"
                    inline
                  />
                </Box>
                <Typography variant="h6" color="warning.main">
                  {deltaData.modifyCount}
                </Typography>
              </Box>
            </Box>
          </Paper>
        )}
      </Box>

      {/* Section Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab
            label={
              <Box display="flex" alignItems="center" gap={0.5}>
                Item 1A (Risk Factors)
                <HelpTooltip
                  title={FILING_SECTIONS.Item1A.title}
                  description={FILING_SECTIONS.Item1A.description}
                  example={FILING_SECTIONS.Item1A.example}
                  size="small"
                  inline
                />
              </Box>
            }
            onClick={() => handleSectionChange('Item1A')}
          />
          <Tab
            label={
              <Box display="flex" alignItems="center" gap={0.5}>
                Item 7 (MD&A)
                <HelpTooltip
                  title={FILING_SECTIONS.Item7.title}
                  description={FILING_SECTIONS.Item7.description}
                  example={FILING_SECTIONS.Item7.example}
                  size="small"
                  inline
                />
              </Box>
            }
            onClick={() => handleSectionChange('Item7')}
          />
          <Tab
            label={
              <Box display="flex" alignItems="center" gap={0.5}>
                Item 7A (Market Risk)
                <HelpTooltip
                  title={FILING_SECTIONS.Item7A.title}
                  description={FILING_SECTIONS.Item7A.description}
                  example={FILING_SECTIONS.Item7A.example}
                  size="small"
                  inline
                />
              </Box>
            }
            onClick={() => handleSectionChange('Item7A')}
          />
          <Tab
            label={
              <Box display="flex" alignItems="center" gap={0.5}>
                XBRL Heatmap
                <HelpTooltip
                  title="XBRL Heatmap이란?"
                  description="재무 지표(매출, 이익 등)가 이전 분기/연도 대비 얼마나 변했는지를 색상으로 표현한 차트입니다. 빨강은 감소, 초록은 증가를 의미합니다."
                  example="매출이 전분기 대비 +15% 증가했다면 초록색으로 표시됩니다."
                  size="small"
                  inline
                />
              </Box>
            }
          />
        </Tabs>
      </Box>

      {/* Content Panels */}
      <TabPanel value={activeTab} index={0}>
        <Typography variant="h6" gutterBottom>
          Risk Factors Changes
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          Diff view coming soon. This will show side-by-side comparison of risk factors.
        </Alert>
        {deltaData?.deltas && deltaData.deltas.length > 0 ? (
          <Box>
            {deltaData.deltas
              .filter((d) => d.section === 'Item1A')
              .map((delta) => (
                <Paper key={delta.id} sx={{ p: 2, mb: 2 }}>
                  <Box display="flex" alignItems="center" gap={1} mb={1}>
                    <Chip
                      label={delta.operation}
                      size="small"
                      color={
                        delta.operation === 'INSERT'
                          ? 'success'
                          : delta.operation === 'DELETE'
                          ? 'error'
                          : 'warning'
                      }
                    />
                    <Chip label={`Score: ${delta.score.toFixed(2)}`} size="small" variant="outlined" />
                  </Box>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                    {delta.snippet}
                  </Typography>
                </Paper>
              ))}
          </Box>
        ) : (
          <Alert severity="info">No changes detected in this section.</Alert>
        )}
      </TabPanel>

      <TabPanel value={activeTab} index={1}>
        <Typography variant="h6" gutterBottom>
          MD&A Changes
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          Diff view coming soon. This will show side-by-side comparison of Management Discussion & Analysis.
        </Alert>
        {deltaData?.deltas && deltaData.deltas.length > 0 ? (
          <Box>
            {deltaData.deltas
              .filter((d) => d.section === 'Item7')
              .map((delta) => (
                <Paper key={delta.id} sx={{ p: 2, mb: 2 }}>
                  <Box display="flex" alignItems="center" gap={1} mb={1}>
                    <Chip
                      label={delta.operation}
                      size="small"
                      color={
                        delta.operation === 'INSERT'
                          ? 'success'
                          : delta.operation === 'DELETE'
                          ? 'error'
                          : 'warning'
                      }
                    />
                    <Chip label={`Score: ${delta.score.toFixed(2)}`} size="small" variant="outlined" />
                  </Box>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                    {delta.snippet}
                  </Typography>
                </Paper>
              ))}
          </Box>
        ) : (
          <Alert severity="info">No changes detected in this section.</Alert>
        )}
      </TabPanel>

      <TabPanel value={activeTab} index={2}>
        <Typography variant="h6" gutterBottom>
          Market Risk Changes
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          Diff view coming soon. This will show side-by-side comparison of quantitative and qualitative market risk disclosures.
        </Alert>
        {deltaData?.deltas && deltaData.deltas.length > 0 ? (
          <Box>
            {deltaData.deltas
              .filter((d) => d.section === 'Item7A')
              .map((delta) => (
                <Paper key={delta.id} sx={{ p: 2, mb: 2 }}>
                  <Box display="flex" alignItems="center" gap={1} mb={1}>
                    <Chip
                      label={delta.operation}
                      size="small"
                      color={
                        delta.operation === 'INSERT'
                          ? 'success'
                          : delta.operation === 'DELETE'
                          ? 'error'
                          : 'warning'
                      }
                    />
                    <Chip label={`Score: ${delta.score.toFixed(2)}`} size="small" variant="outlined" />
                  </Box>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                    {delta.snippet}
                  </Typography>
                </Paper>
              ))}
          </Box>
        ) : (
          <Alert severity="info">No changes detected in this section.</Alert>
        )}
      </TabPanel>

      <TabPanel value={activeTab} index={3}>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">
            XBRL Financial Metrics Heatmap
          </Typography>
          <Button
            variant="contained"
            onClick={() => normalizeMutation.mutate(true)}
            disabled={normalizeMutation.isPending}
          >
            {normalizeMutation.isPending ? 'Normalizing...' : 'Normalize Filing'}
          </Button>
        </Box>

        {/* Normalization Stats */}
        {normStats && (
          <Paper sx={{ p: 2, mb: 2 }}>
            <Box display="flex" gap={3} flexWrap="wrap" alignItems="center">
              <Box>
                <Typography variant="caption" color="text.secondary">
                  Normalized Concepts
                </Typography>
                <Typography variant="h6">{normStats.normalizedConceptCount}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">
                  Unique FAC Concepts
                </Typography>
                <Typography variant="h6">{normStats.distinctConcepts.length}</Typography>
              </Box>
              {dataQuality && (
                <>
                  <Box>
                    <Typography variant="caption" color="error.main">
                      Errors
                    </Typography>
                    <Typography variant="h6" color="error.main">
                      {dataQuality.errorCount}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="warning.main">
                      Warnings
                    </Typography>
                    <Typography variant="h6" color="warning.main">
                      {dataQuality.warningCount}
                    </Typography>
                  </Box>
                </>
              )}
            </Box>
            {normStats.distinctConcepts.length > 0 && (
              <Box mt={2}>
                <Typography variant="caption" color="text.secondary" display="block" mb={1}>
                  Available Concepts:
                </Typography>
                <Box display="flex" gap={1} flexWrap="wrap">
                  {normStats.distinctConcepts.map((concept) => (
                    <Chip key={concept} label={concept} size="small" variant="outlined" />
                  ))}
                </Box>
              </Box>
            )}
          </Paper>
        )}

        {normalizeMutation.isSuccess && (
          <Alert severity="success" sx={{ mb: 2 }}>
            Filing normalized successfully! {normalizeMutation.data.normalizedConceptCount} concepts processed.
          </Alert>
        )}

        {normalizeMutation.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            Normalization failed. Please try again.
          </Alert>
        )}

        {heatmapLoading ? (
          <Box display="flex" justifyContent="center" py={4}>
            <CircularProgress />
          </Box>
        ) : heatmapData && heatmapData.rows && heatmapData.rows.length > 0 ? (
          <Box>
            <Alert severity="info" sx={{ mb: 2 }}>
              Interactive heatmap visualization coming soon. Below is the raw data.
            </Alert>
            {heatmapData.rows.map((row, idx) => {
              const helpText = XBRL_METRICS[row.metric];

              return (
                <Paper key={idx} sx={{ p: 2, mb: 2 }}>
                  <Box display="flex" justifyContent="space-between" alignItems="center">
                    <Box display="flex" alignItems="center">
                      <Typography variant="subtitle1" fontWeight="bold">
                        {row.metric}
                      </Typography>
                      {helpText && (
                        <HelpTooltip
                          title={helpText.title}
                          description={helpText.description}
                          example={helpText.example}
                          size="small"
                          inline
                        />
                      )}
                    </Box>
                    {row.zScore > 2 && (
                      <Box display="flex" alignItems="center">
                        <Chip label="⚠️ Anomaly" color="warning" size="small" />
                        <HelpTooltip
                          title={CHANGE_METRICS['Z-Score'].title}
                          description={CHANGE_METRICS['Z-Score'].description}
                          example={CHANGE_METRICS['Z-Score'].example}
                          size="small"
                          inline
                        />
                      </Box>
                    )}
                  </Box>
                  <Box display="flex" gap={2} mt={1} flexWrap="wrap">
                    {Object.entries(row.values).map(([basis, value]) => (
                      <Box key={basis}>
                        <Box display="flex" alignItems="center">
                          <Typography variant="caption" color="text.secondary">
                            {basis}
                          </Typography>
                          {(basis === 'QoQ' || basis === 'YoY') && CHANGE_METRICS[basis] && (
                            <HelpTooltip
                              title={CHANGE_METRICS[basis].title}
                              description={CHANGE_METRICS[basis].description}
                              example={CHANGE_METRICS[basis].example}
                              size="small"
                              inline
                            />
                          )}
                        </Box>
                        <Typography
                          variant="body1"
                          color={value > 0 ? 'success.main' : value < 0 ? 'error.main' : 'text.primary'}
                          fontWeight="bold"
                        >
                          {basis === 'Abs' ? (
                            // Absolute values: format as currency/number
                            typeof value === 'number'
                              ? value >= 1e9
                                ? `$${(value / 1e9).toFixed(2)}B`
                                : value >= 1e6
                                ? `$${(value / 1e6).toFixed(2)}M`
                                : `$${value.toLocaleString()}`
                              : value
                          ) : (
                            // Percentage values (QoQ, YoY, etc.)
                            <>
                              {value > 0 ? '+' : ''}
                              {typeof value === 'number' ? value.toFixed(2) : value}%
                            </>
                          )}
                        </Typography>
                      </Box>
                    ))}
                    <Box>
                      <Box display="flex" alignItems="center">
                        <Typography variant="caption" color="text.secondary">
                          Z-Score
                        </Typography>
                        <HelpTooltip
                          title={CHANGE_METRICS['Z-Score'].title}
                          description={CHANGE_METRICS['Z-Score'].description}
                          example={CHANGE_METRICS['Z-Score'].example}
                          size="small"
                          inline
                        />
                      </Box>
                      <Typography variant="body1">{row.zScore.toFixed(2)}</Typography>
                    </Box>
                  </Box>
                </Paper>
              );
            })}
          </Box>
        ) : (
          <Alert severity="info">No XBRL metrics available for this filing.</Alert>
        )}
      </TabPanel>
    </Container>
  );
};

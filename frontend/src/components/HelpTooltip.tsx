import React from 'react';
import { Tooltip, IconButton, Box, Typography } from '@mui/material';
import { HelpOutline as HelpIcon } from '@mui/icons-material';

interface HelpTooltipProps {
  title: string;
  description: string;
  example?: string;
  size?: 'small' | 'medium';
  inline?: boolean;
}

/**
 * 재사용 가능한 도움말 툴팁 컴포넌트
 * 사용자가 용어나 지표를 이해할 수 있도록 친절한 설명을 제공합니다.
 */
export const HelpTooltip: React.FC<HelpTooltipProps> = ({
  title,
  description,
  example,
  size = 'small',
  inline = false,
}) => {
  const tooltipContent = (
    <Box sx={{ maxWidth: 400, p: 1 }}>
      <Typography variant="subtitle2" fontWeight="bold" gutterBottom>
        {title}
      </Typography>
      <Typography variant="body2" sx={{ mb: example ? 1 : 0 }}>
        {description}
      </Typography>
      {example && (
        <Box sx={{ mt: 1, p: 1, bgcolor: 'rgba(255, 255, 255, 0.1)', borderRadius: 1 }}>
          <Typography variant="caption" fontWeight="bold">
            예시:
          </Typography>
          <Typography variant="caption" display="block">
            {example}
          </Typography>
        </Box>
      )}
    </Box>
  );

  return (
    <Tooltip
      title={tooltipContent}
      arrow
      enterDelay={200}
      leaveDelay={200}
      placement="top"
    >
      <IconButton
        size={size}
        sx={{
          ml: inline ? 0.5 : 0,
          p: inline ? 0.25 : 0.5,
          verticalAlign: inline ? 'middle' : 'inherit',
        }}
        aria-label={`${title}에 대한 도움말`}
      >
        <HelpIcon fontSize={size} />
      </IconButton>
    </Tooltip>
  );
};

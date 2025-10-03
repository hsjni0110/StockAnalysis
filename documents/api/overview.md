# API 개요

Stock Delta System의 REST API는 SEC 데이터 수집, 티커 관리, 파일링 조회를 위한 RESTful 엔드포인트를 제공합니다.

## 기본 정보

- **Base URL**: `http://localhost:8080/api`
- **Content-Type**: `application/json`
- **인증**: 현재 미적용 (향후 확장 가능)
- **Rate Limiting**: SEC API 제한에 따른 자동 조절

## API 구성

### 1. 데이터 수집 (Ingestion)
SEC 데이터 수집을 트리거하고 모니터링하는 엔드포인트

### 2. 티커 관리 (Ticker)
티커-CIK 매핑 및 발행자 정보 관리

### 3. 파일링 조회 (Filings)
수집된 SEC 파일링 데이터 조회 및 검색

## 주요 특징

- **비동기 처리**: 대용량 데이터 수집을 위한 백그라운드 처리
- **캐싱**: Redis를 활용한 효율적인 응답
- **모니터링**: 상세한 로깅 및 상태 추적
- **확장성**: 마이크로서비스 아키텍처 대응

## 다음 단계

각 API 그룹별 상세 문서:
- [데이터 수집 API](./ingestion.md)
- [티커 관리 API](./ticker.md)
- [파일링 조회 API](./filings.md)
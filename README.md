# Stock Delta System

A comprehensive SEC filing data collection and analysis system that tracks changes in corporate filings and provides real-time insights for stock analysis.

## Features

- **SEC Data Collection**: Automated collection from EDGAR database
- **Real-time Processing**: Daily index monitoring and submissions tracking
- **Rate-Limited API**: Compliant with SEC fair access rules (10 rps)
- **Ticker Resolution**: Automatic symbol-to-CIK mapping with caching
- **Data Normalization**: XBRL facts parsing and standardization
- **Change Detection**: Delta analysis between filing periods

## Architecture

- **Backend**: Spring Boot 3 with Java 21
- **Database**: PostgreSQL with optimized indexing
- **Cache**: Redis for API response caching
- **API**: RESTful endpoints for data ingestion and retrieval
- **Containerization**: Docker Compose for easy deployment

## License

Private project - All rights reserved.
# API Management

This directory contains all SAP API Management artifacts and configurations.

## Contents

- **API Subscriptions** - Consumer API product definitions
- **Policies** - Authentication, rate limiting, and quota policies
- **Developer Portal** - Documentation and consumer onboarding
- **Analytics** - API consumption tracking and monitoring

## Consumer API Products

### REP_PORTAL_API
- Sales Rep Portal synchronous integration
- OAuth 2.0 authentication
- Rate limit: [To be defined]

### EDL_SALES_ORDER_INBOUND_API
- Epicor HQ / EDI Translator asynchronous integration
- OAuth / API Key authentication
- Rate limit: [To be defined]

### CONSUMER_A_API
- Template for future partner integrations
- OAuth authentication
- Rate limit: [To be defined]

## Policies

- `AuthenticationPolicy` - OAuth 2.0 validation
- `RateLimitPolicy` - Consumer-specific rate limiting
- `QuotaPolicy` - Daily/monthly quotas per consumer
- `AnalyticsPolicy` - Request/response tracking

## Documentation

Consumer API documentation and interactive testing available via API Management Developer Portal.

## Configuration

See implementation guides in `/implementation` for API setup and policy configuration.

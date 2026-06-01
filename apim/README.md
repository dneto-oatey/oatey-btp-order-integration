# API Management

Week 1 APIM foundation for the approved Oatey SAP BTP inbound sales order architecture.

## API Products

| Product | Consumer | Pattern | Base Path | Target |
| --- | --- | --- | --- | --- |
| REP_PORTAL_API | Sales Rep Portal | Sync | /api/v1/portal/sales-orders | Standard SAP Sales Order API |
| EDL_SALES_ORDER_INBOUND_API | EDI Translator | Async | /api/v1/sales-orders | IFL_SO_INBOUND |
| CONSUMER_A_API | Future partners | Async | /api/v1/sales-orders | IFL_SO_INBOUND |

## Proxies

- sales-rep-portal-v1 -> openapi/sales-rep-portal-api.yaml
- - sales-order-inbound-v1 -> openapi/sales-order-inbound-api.yaml
  - - callback-notification-v1 -> openapi/callback-notification-api.yaml
   
    - ## Policies
   
    - - OAuth 2.0 or approved API key.
      - - Consumer authorization by API product.
        - - Quota, rate limit, and spike arrest per consumer.
          - - Required Idempotency-Key and X-Consumer-ID headers.
            - - X-Correlation-ID pass-through to downstream runtime.
              - - Analytics by consumer, status code, latency, and API product.
               
                - ## Environment Strategy
               
                - DEV is for contract build, QA is for integrated validation, and PROD is for controlled production traffic. Hostnames, credentials, destinations, quotas, and rate limits are environment configuration, not source code.
               
                - ## Out of Scope
               
                - APIM does not perform mapping, orchestration, retry, DLQ handling, persistence, CAP, PostgreSQL, Event Mesh, or UI.
                - 

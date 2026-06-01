# API Management

Week 1 APIM foundation for the approved Oatey SAP BTP inbound sales order architecture.

API products are REP_PORTAL_API for Sales Rep Portal synchronous traffic at /api/v1/portal/sales-orders, EDL_SALES_ORDER_INBOUND_API for EDI Translator asynchronous traffic at /api/v1/sales-orders, and CONSUMER_A_API as the future partner template at /api/v1/sales-orders.

API proxies are sales-rep-portal-v1 using openapi/sales-rep-portal-api.yaml, sales-order-inbound-v1 using openapi/sales-order-inbound-api.yaml, and callback-notification-v1 using openapi/callback-notification-api.yaml.

Policies include OAuth 2.0 or approved API key, consumer authorization by API product, quota, rate limit, spike arrest, Idempotency-Key enforcement, X-Consumer-ID enforcement, X-Correlation-ID pass-through, and analytics by consumer, status code, latency, and API product.

Environment strategy uses DEV for contract build, QA for integrated validation, and PROD for controlled production traffic. Hostnames, credentials, destinations, quotas, and rate limits are environment configuration and must not be stored as secrets in this repository.

Out of scope for APIM: mapping, orchestration, retry, DLQ handling, persistence, CAP, PostgreSQL, Event Mesh, and UI.

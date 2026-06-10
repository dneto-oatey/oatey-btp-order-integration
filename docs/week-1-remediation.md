# Week 1 Remediation

## Summary

Applied the Week 1 review recommendations without changing the approved architecture. The runtime remains APIM plus SAP Integration Suite plus JMS plus the standard SAP Sales Order API.

## Changes Applied

`sales-order-inbound-api.yaml` now explicitly includes `X-Correlation-ID`, `Idempotency-Key`, and `X-Consumer-ID` headers. It also includes 401 Unauthorized, 403 Forbidden, and 429 Too Many Requests responses for APIM policy outcomes.

`sales-rep-portal-api.yaml` was reformatted from single-line JSON flow into multiline YAML flow style. It now includes `X-Correlation-ID` and `Idempotency-Key` headers and APIM policy responses.

`callback-notification-api.yaml` defines OAuth2 Client Credentials authentication, a security section, and an example callback payload.

`duplicate-idempotency-key.json` no longer treats `idempotencyKey` as a body field. `test-payloads/README.md` documents that `Idempotency-Key` is the authoritative HTTP header.

## Architecture Guardrails

No PostgreSQL, Event Mesh, UI, or additional services were introduced.

APIM remains responsible only for authentication, authorization, rate limiting, quotas, analytics, consumer separation, and policy enforcement.

Integration Suite remains responsible for validation, enrichment, JMS publication, orchestration, SAP API invocation, response handling, retry/DLQ, and callback processing.

## Week 2 Readiness

Status: ready for Week 2 build planning.

The remaining Week 2 work should focus on implementable APIM proxy configuration, CPI iFlow construction, mapping rules, JMS setup, and environment-specific security values.

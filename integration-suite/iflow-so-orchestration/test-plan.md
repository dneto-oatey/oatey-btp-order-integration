# IFL_SO_ORCHESTRATION Test Plan

## Purpose

Executable test plan for Week 6 orchestration build. Fill `Actual Result` and `Pass/Fail` during CPI runtime execution.

## Test Matrix

| ID | Scenario | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- | --- |
| ORCH-001 | Valid K-Cimarron payload | Message consumed from JMS_SO_INBOUND; API_SALES_ORDER_SRV called; SAP order number captured; SUCCESS callback prepared; no payload logged on success | TBD | TBD |
| ORCH-002 | Valid Affiliated Dist payload | Message consumed; optional fields such as IncotermsClassification preserved; API_SALES_ORDER_SRV called; SUCCESS callback prepared | TBD | TBD |
| ORCH-003 | SAP business validation error | SAP_BUSINESS_ERROR; no blind retry; FAILED callback prepared with SAP error context; DLQ only if operations policy requires manual analysis | TBD | TBD |
| ORCH-004 | SAP transient 503 | SAP_TRANSIENT_ERROR; retry attempted until success or maxRetryCount | TBD | TBD |
| ORCH-005 | SAP timeout | SAP_TRANSIENT_ERROR; retry attempted; DLQ_SO_INBOUND after retry exhaustion | TBD | TBD |
| ORCH-006 | Missing correlationId | VALIDATION_ERROR; no SAP call; DLQ_SO_INBOUND because correlationId must exist by orchestration stage | TBD | TBD |
| ORCH-007 | Missing consumerId | VALIDATION_ERROR unless inbound fallback UNKNOWN_CONSUMER is present; no SAP call when missing entirely | TBD | TBD |
| ORCH-008 | Missing idempotencyKey | Allowed in POC; warning logged; no DLQ routing for this condition alone; SAP call can continue | TBD | TBD |
| ORCH-009 | Malformed JMS payload | VALIDATION_ERROR; no SAP call; DLQ_SO_INBOUND | TBD | TBD |
| ORCH-010 | Callback failure | CALLBACK_ERROR; callback retry policy applied; DLQ_SO_INBOUND after callback retry exhaustion | TBD | TBD |
| ORCH-011 | DLQ replay validation | DLQ payload contains original SAP JSON, correlationId, available idempotencyKey, consumerId, error context, and replay guidance requiring idempotency review | TBD | TBD |

## Response Classification Tests

| SAP/API condition | Expected classification | Expected behavior | Actual Result | Pass/Fail |
| --- | --- | --- | --- | --- |
| HTTP 200/201 with sales order number | SUCCESS | Complete message and prepare SUCCESS callback | TBD | TBD |
| HTTP 200/201 without sales order number | TECHNICAL_ERROR | Retry, then DLQ after exhaustion | TBD | TBD |
| HTTP 400 | SAP_BUSINESS_ERROR | No blind retry | TBD | TBD |
| HTTP 409 | SAP_BUSINESS_ERROR or duplicate | No blind retry | TBD | TBD |
| HTTP 422 | SAP_BUSINESS_ERROR | No blind retry | TBD | TBD |
| HTTP 401/403 | SAP_AUTH_CONFIG_ERROR | Limited retry or DLQ by policy | TBD | TBD |
| HTTP 408/429/5xx | SAP_TRANSIENT_ERROR | Retryable | TBD | TBD |

## Monitoring Assertions

| Assertion | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- |
| correlationId preserved across SAP call, callback, and DLQ | Present | TBD | TBD |
| consumerId preserved or UNKNOWN_CONSUMER fallback used | Present | TBD | TBD |
| Missing idempotencyKey warning only | Warning, no DLQ | TBD | TBD |
| Success path has no payload logging | Metadata only | TBD | TBD |
| DLQ contains replay context | Present | TBD | TBD |

## Security Assertions

| Assertion | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- |
| SAP credentials use security material or destination | No hard-coded credentials | TBD | TBD |
| Callback credentials externalized | No hard-coded credentials | TBD | TBD |
| Tokens/passwords absent from logs | No secrets logged | TBD | TBD |

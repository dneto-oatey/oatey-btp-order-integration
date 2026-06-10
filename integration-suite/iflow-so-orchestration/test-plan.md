# IFL_SO_ORCHESTRATION Test Plan

## Purpose

Executable test plan for the runtime-validated orchestration build. Fill `Actual Result` and `Pass/Fail` during CPI runtime execution.

## Runtime Validated Results

| Scenario | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- |
| JMS consumption | Message consumed from `JMS_SO_INBOUND` | Working | PASS |
| CSRF token fetch | HTTP GET returns x-csrf-token | Working | PASS |
| SAP session cookie handling | set-cookie captured and Cookie header prepared | Working | PASS |
| HTTP POST to API_SALES_ORDER_SRV | POST reaches SAP API | Working | PASS |
| SAP functional validation | SAP returns business validation response | `Sales area 1000 10 00 does not exist` | PASS |
| Failure routing | Failed message routes to `DLQ_SO_INBOUND` | Working | PASS |

## Test Matrix

| ID | Scenario | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- | --- |
| ORCH-001 | Valid K-Cimarron payload | Message consumed; CSRF fetched; HTTP POST reaches API_SALES_ORDER_SRV; SAP response classified; no payload logged on success | TBD | TBD |
| ORCH-002 | Valid Affiliated Dist payload | Optional fields such as IncotermsClassification preserved; HTTP POST reaches SAP API | TBD | TBD |
| ORCH-003 | SAP business validation error | SAP_BUSINESS_ERROR; no blind retry; DLQ envelope routed to `DLQ_SO_INBOUND` | `Sales area 1000 10 00 does not exist` | PASS |
| ORCH-004 | SAP transient 503 | SAP_TRANSIENT_ERROR; retry attempted until success or maxRetryCount | TBD | TBD |
| ORCH-005 | SAP timeout | SAP_TRANSIENT_ERROR; retry attempted; `DLQ_SO_INBOUND` after retry exhaustion | TBD | TBD |
| ORCH-006 | Missing correlationId | VALIDATION_ERROR; no SAP call; `DLQ_SO_INBOUND` because correlationId must exist by orchestration stage | TBD | TBD |
| ORCH-007 | Missing consumerId | VALIDATION_ERROR unless inbound fallback UNKNOWN_CONSUMER is present; no SAP call when missing entirely | TBD | TBD |
| ORCH-008 | Missing idempotencyKey | Allowed; warning logged; no DLQ routing for this condition alone; SAP call can continue | TBD | TBD |
| ORCH-009 | Malformed JMS payload | VALIDATION_ERROR; no SAP call; `DLQ_SO_INBOUND` | TBD | TBD |
| ORCH-010 | Missing CSRF token | TECHNICAL_ERROR; DLQ envelope after exception handling | TBD | TBD |
| ORCH-011 | Missing SAP cookie | TECHNICAL_ERROR; DLQ envelope after exception handling | TBD | TBD |
| ORCH-012 | DLQ payload source priority | `originalPayload` comes from `sapRequestPayload`, then `originalPayload`, then current body | TBD | TBD |
| ORCH-013 | Replayed message fails again | New DLQ envelope preserves incoming `replayCount`; does not reset to 0 | TBD | TBD |

## DLQ Envelope Assertions

| Field | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- |
| sourceIFlow/sourceQueue/targetQueue | Present | TBD | TBD |
| correlationId/consumerId/idempotencyKey | Present when provided or fallback is available | TBD | TBD |
| errorCategory/errorCode/errorMessage | Present and sanitized | TBD | TBD |
| sapResponseStatusCode/sapErrorCode/sapErrorMessage | Present when SAP provides them | TBD | TBD |
| retryAttempt/maxRetryCount | Present when available | TBD | TBD |
| replayRequired/replayInstruction | Present | TBD | TBD |
| replayCount/maxReplayCount | Present with defaults 0/1 when missing | TBD | TBD |
| originalPayload | Present from `sapRequestPayload`, `originalPayload`, or body fallback | TBD | TBD |

## Response Classification Tests

| SAP/API condition | Expected classification | Expected behavior | Actual Result | Pass/Fail |
| --- | --- | --- | --- | --- |
| HTTP 200/201 with sales order number | SUCCESS | Complete message | TBD | TBD |
| HTTP 200/201 without sales order number | TECHNICAL_ERROR | Route through exception flow | TBD | TBD |
| HTTP 400 | SAP_BUSINESS_ERROR | No blind retry | Sales area validation error | PASS |
| HTTP 409 | SAP_BUSINESS_ERROR or duplicate | No blind retry | TBD | TBD |
| HTTP 422 | SAP_BUSINESS_ERROR | No blind retry | TBD | TBD |
| HTTP 401/403 | SAP_AUTH_CONFIG_ERROR | DLQ by policy | TBD | TBD |
| HTTP 408/429/5xx | SAP_TRANSIENT_ERROR | Retryable | TBD | TBD |

## Monitoring Assertions

| Assertion | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- |
| correlationId preserved across SAP call and DLQ | Present | TBD | TBD |
| consumerId preserved or UNKNOWN_CONSUMER fallback used | Present | TBD | TBD |
| Missing idempotencyKey warning only | Warning, no DLQ by itself | TBD | TBD |
| replayCount/maxReplayCount visible in MPL custom headers when populated | Present | TBD | TBD |
| CSRF token and SAP cookie absent from MPL logs | No token/cookie logging | TBD | TBD |
| Success path has no payload logging | Metadata only | TBD | TBD |
| DLQ contains replay context | Present | TBD | TBD |

## Security Assertions

| Assertion | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- |
| SAP credentials use security material | No hard-coded credentials | TBD | TBD |
| CSRF token and SAP cookie are runtime values only | Not externalized, not logged | TBD | TBD |
| Tokens/passwords absent from logs | No secrets logged | TBD | TBD |

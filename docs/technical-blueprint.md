# Technical Blueprint

## 1. Objective

Document the final validated state for the Oatey SAP BTP inbound Sales Order integration. The approved architecture remains APIM + SAP Integration Suite + JMS + SAP standard Sales Order APIs.

## 2. Runtime Topology

```text
APIM
-> IFL_SO_INBOUND
-> JMS_SO_INBOUND
-> IFL_SO_ORCHESTRATION
-> SAP Standard API API_SALES_ORDER_SRV via HTTP Receiver
-> DLQ_SO_INBOUND on failure
-> Optional manual replay using IFL_SO_REPROCESS_DLQ
   -> JMS_SO_INBOUND if replay eligible
   -> REJECTED_REPLAY_SO_INBOUND if replay is not eligible
```

## 3. IFL_SO_INBOUND Status

Status: COMPLETED.

Validated in SAP Integration Suite runtime:

| Capability | Result |
| --- | --- |
| Deployment | Successful |
| OAuth authentication | Successful |
| HTTPS endpoint | Operational |
| Header validation | Operational |
| Correlation ID preservation | Operational |
| Correlation ID auto-generation | Operational |
| Consumer fallback | Operational |
| Idempotency fallback | Operational |
| JMS publication | Operational |
| ACK 202 response | Operational |
| Exception subprocess | Operational |

## 4. Validated Test Results

| Scenario | Result | Status |
| --- | --- | --- |
| Inbound accepts message and returns async accepted response | HTTP 202 | PASS |
| Orchestration consumes `JMS_SO_INBOUND` | Working | PASS |
| CSRF token fetch | Working | PASS |
| SAP POST reaches `API_SALES_ORDER_SRV` | Working | PASS |
| SAP business validation error for DEV payload | `Sales area 1000 10 00 does not exist` | PASS |
| Failed orchestration message | Routed to `DLQ_SO_INBOUND` | PASS |
| Reprocess without replay approval | Routed to `REJECTED_REPLAY_SO_INBOUND` | PASS |
| Reprocess missing `originalPayload` | Routed to `REJECTED_REPLAY_SO_INBOUND` | PASS |
| Payload logging | No payload logged to MPL | PASS |

## 5. IFL_SO_INBOUND Responsibility

Responsible for OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

Not responsible for SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## 6. IFL_SO_ORCHESTRATION Responsibility

Responsible for JMS consumption, CSRF token fetch, SAP cookie/session handling, HTTP POST to `API_SALES_ORDER_SRV/A_SalesOrder`, SAP response classification, retry handling, DLQ envelope creation, and failure routing to `DLQ_SO_INBOUND`.

HTTP Receiver is used instead of OData Receiver because the OData adapter attempted to parse JSON deep insert payloads as XML/Atom.

## 7. IFL_SO_REPROCESS_DLQ Responsibility

Responsible for manual/ad hoc replay of reviewed DLQ messages. The iFlow remains Not Deployed by default, is deployed only during an approved replay window, and is undeployed immediately after replay completion.

Eligible replay messages are sent to `JMS_SO_INBOUND`. Ineligible messages are sent to `REJECTED_REPLAY_SO_INBOUND`.

## 8. Correlation Strategy

`X-Correlation-ID` is optional. `IFL_SO_INBOUND` preserves an incoming value when provided, generates a UUID when missing, returns `correlationId` in the ACK response, and uses correlationId for operational traceability.

## 9. Idempotency Strategy

`Idempotency-Key` is optional in the current implementation. Current inbound flow preserves idempotency key when provided but does not reject requests when missing. Missing `Idempotency-Key` is accepted with an empty idempotency key. Idempotency validation is deferred to `IFL_SO_ORCHESTRATION` or future production policy.

## 10. Header Handling Lessons Learned

Inbound HTTP headers were not consistently accessible throughout runtime processing. The final pattern is to capture `Content-Type`, `X-Correlation-ID`, `X-Consumer-ID`, and `Idempotency-Key` into Exchange Properties immediately after HTTPS Sender and use Exchange Properties during subsequent processing.

## 11. CSRF And SAP HTTP Handling

`IFL_SO_ORCHESTRATION` uses HTTP Receiver for SAP Sales Order creation.

CSRF flow:

1. Preserve `sapRequestPayload` before CSRF fetch.
2. GET `/sap/opu/odata/sap/API_SALES_ORDER_SRV/` with `x-csrf-token = Fetch`.
3. Extract `x-csrf-token` and `set-cookie`.
4. Build Cookie header.
5. Restore `sapRequestPayload` before POST.
6. POST `/sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder`.

Never log CSRF token or SAP cookie to MPL.

## 12. DLQ Envelope And Replay Governance

`GS_PrepareDlqPayload` resolves `originalPayload` using this priority:

1. Message property `sapRequestPayload`
2. Message property `originalPayload`
3. Current message body as fallback

DLQ envelope includes source, queue, correlation, consumer, idempotency, processing, error, SAP response, retry, replay, and `originalPayload` fields.

Replay governance:

| Field | Rule |
| --- | --- |
| `replayCount` | Default `0`; preserved through DLQ and replay |
| `maxReplayCount` | Default `1`; preserved through DLQ and replay |
| Replayed failure | New DLQ envelope preserves incoming `replayCount` |

Do not rely on JMS technical redelivery count for business replay governance.

## 13. Monitoring And Logging Strategy

Success path: no payload logging, only operational metadata. Error path: payload may exist inside the DLQ envelope body for replay support, but it must not be logged to MPL.

Do not log payload, CSRF token, SAP cookie, Authorization header, bearer token, password, or secrets to MPL.

## 14. Decisions

| Decision | Title | Reason |
| --- | --- | --- |
| D-001 | No payload logging on successful processing | Security and operational best practices |
| D-002 | Header normalization immediately after HTTPS Sender | Runtime header propagation inconsistencies |
| D-003 | JMS decoupling between inbound and orchestration layers | Resilience, replay capability, retry support, and consumer decoupling |
| D-004 | Business Validation Deferred to SAP Standard API | Avoid duplication of SAP business logic inside Integration Suite and maintain Clean Core principles |
| D-005 | Replay Governance Through DLQ Envelope Metadata | Avoid reliance on JMS technical redelivery count for business replay decisions |

## 15. Guardrails

Do not introduce PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z APIs, custom persistence, S/4HANA core modification, custom canonical model, or payload logging to MPL.

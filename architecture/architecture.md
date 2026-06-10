# SAP BTP Inbound Sales Order Integration Architecture (Oatey)

## Executive Summary

This document defines the approved architecture and final implementation decisions for the Oatey SAP BTP inbound Sales Order integration. The approved runtime remains APIM + SAP Integration Suite + JMS + SAP standard Sales Order APIs.

## Approved Runtime Architecture

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

## Scope Guardrails

Do not introduce PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA calls inside `IFL_SO_INBOUND`, canonical models, custom persistence, or payload persistence outside JMS.

## IFL_SO_INBOUND Status

Status: COMPLETED.

| Runtime capability | Result |
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

## Validated Test Results

| Scenario | Result | Status |
| --- | --- | --- |
| Happy Path | 202 ACCEPTED | PASS |
| Missing X-Correlation-ID | UUID generated automatically | PASS |
| Missing X-Consumer-ID | UNKNOWN_CONSUMER fallback | PASS |
| Missing Idempotency-Key | Accepted with empty idempotency key | PASS |
| Invalid Content-Type | Rejected | PASS |
| Orchestration consumes `JMS_SO_INBOUND` | Working | PASS |
| CSRF token fetch | Working | PASS |
| SAP POST reaches `API_SALES_ORDER_SRV` | SAP returned business validation response | PASS |
| DEV payload SAP response | `Sales area 1000 10 00 does not exist` | PASS |
| Failure routing | Message routed to `DLQ_SO_INBOUND` | PASS |
| Reprocess without replay approval | Routed to `REJECTED_REPLAY_SO_INBOUND` | PASS |
| Reprocess missing `originalPayload` | Routed to `REJECTED_REPLAY_SO_INBOUND` | PASS |

## IFL_SO_INBOUND Responsibility

`IFL_SO_INBOUND` is responsible for OAuth authentication, HTTPS endpoint exposure, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

`IFL_SO_INBOUND` is not responsible for SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## IFL_SO_ORCHESTRATION Responsibility

`IFL_SO_ORCHESTRATION` consumes `JMS_SO_INBOUND`, preserves correlation and replay metadata, performs CSRF token handling, calls SAP standard API `API_SALES_ORDER_SRV` through HTTP Receiver, classifies SAP responses, and routes terminal failures to `DLQ_SO_INBOUND`.

HTTP Receiver is used instead of OData Receiver because the OData adapter attempted to parse JSON deep insert payloads as XML/Atom.

## IFL_SO_REPROCESS_DLQ Responsibility

`IFL_SO_REPROCESS_DLQ` is a manual/ad hoc replay utility. It remains Not Deployed by default, is deployed only during an approved replay window, and is undeployed immediately after replay completion.

Messages eligible for replay are sent back to `JMS_SO_INBOUND`. Messages not eligible for replay are routed to `REJECTED_REPLAY_SO_INBOUND`.

## Correlation Strategy

`X-Correlation-ID` is optional. `IFL_SO_INBOUND` preserves an incoming value when provided and generates a UUID when the header is missing. The ACK response returns `correlationId`, and correlationId is used for operational traceability across runtime monitoring, error responses, JMS processing, DLQ, and replay.

## Idempotency Strategy

`Idempotency-Key` is optional in the current implementation. Current inbound flow preserves idempotency key when provided but does not reject requests when missing. Missing `Idempotency-Key` is accepted with an empty idempotency key. Validation and duplicate handling are deferred to the orchestration layer or future production policy.

## SAP Integration Suite Header Handling Lessons Learned

Inbound HTTP headers were not consistently accessible throughout runtime processing. The final implementation pattern captures required inbound headers into Exchange Properties immediately after the HTTPS Sender and uses Exchange Properties during subsequent processing.

| Header | Exchange Property usage |
| --- | --- |
| Content-Type | Header validation and JSON transport check |
| X-Correlation-ID | Preserve incoming correlation ID when provided |
| X-Consumer-ID | Consumer identification and UNKNOWN_CONSUMER fallback |
| Idempotency-Key | Idempotency preservation and empty fallback |

## Validation Approach

The executable inbound flow uses Groovy-based validation. JSON Schema Validation is not used because the target SAP Integration Suite tenant did not provide a native JSON Schema Validator component. EDI Validator and XML Validator are not valid substitutes for JSON payload validation.

Business validation is performed by SAP S/4HANA through `API_SALES_ORDER_SRV`.

## DLQ And Replay Governance

`GS_PrepareDlqPayload` resolves `originalPayload` using this priority:

1. Message property `sapRequestPayload`
2. Message property `originalPayload`
3. Current message body as fallback

DLQ replay governance is controlled by DLQ envelope metadata, not JMS technical redelivery count.

| Field | Rule |
| --- | --- |
| `replayCount` | Default `0`; preserved through DLQ and replay |
| `maxReplayCount` | Default `1`; preserved through DLQ and replay |
| Replayed failure | New DLQ envelope keeps incoming `replayCount`; it must not reset to `0` |

## Monitoring And Logging Strategy

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Normal operation does not depend on MPL custom properties.

Success path logging does not include payload logging and records only operational metadata. Error path logging does not persist payloads by default and uses Trace mode only for deep troubleshooting.

Do not log payload, CSRF token, SAP cookie, Authorization header, bearer token, password, or secrets to MPL.

## POC Decisions

| Decision | Title | Description | Reason |
| --- | --- | --- | --- |
| D-001 | No payload logging on successful processing | Successful transactions do not log payload bodies. | Security and operational best practices. |
| D-002 | Header normalization immediately after HTTPS Sender | Inbound headers are captured into Exchange Properties at the start of the iFlow. | Runtime header propagation inconsistencies. |
| D-003 | JMS decoupling between inbound and orchestration layers | JMS separates inbound acceptance from SAP order processing. | Resilience, replay capability, retry support, and consumer decoupling. |
| D-004 | Business Validation Deferred to SAP Standard API | Business validation is intentionally deferred to `IFL_SO_ORCHESTRATION` and SAP S/4HANA. The inbound flow validates only transport and integration concerns. | Avoid duplication of SAP business logic inside Integration Suite and maintain Clean Core principles. |
| D-005 | Replay Governance Through DLQ Envelope Metadata | Replay count and replay limits are controlled through DLQ envelope fields. | Avoid reliance on JMS technical redelivery count for business replay decisions. |

## Clean Core Alignment

The solution uses SAP standard Sales Order APIs only. No BAPI, RFC, custom Z services, S/4HANA core modification, custom persistence, or custom canonical model is introduced.

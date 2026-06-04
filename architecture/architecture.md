# SAP BTP Inbound Sales Order Integration Architecture (Oatey)

## Executive Summary

This document defines the approved architecture and final POC implementation decisions for the Oatey SAP BTP inbound Sales Order integration. The approved runtime remains APIM + SAP Integration Suite + JMS + SAP standard Sales Order APIs.

IFL_SO_INBOUND implementation and runtime testing in SAP Integration Suite are complete. The inbound integration flow is operational and ready for IFL_SO_ORCHESTRATION implementation.

## Approved Runtime Architecture

Consumer / Partner / EDI Source -> SAP API Management -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> SAP Standard Sales Order API -> Callback Notification.

## Scope Guardrails

Do not introduce CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA calls inside IFL_SO_INBOUND, canonical models, or payload persistence outside JMS.

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

All scenarios below were successfully tested in the SAP Integration Suite runtime.

| Scenario | Result | Status |
| --- | --- | --- |
| Happy Path | 202 ACCEPTED | PASS |
| Missing X-Correlation-ID | UUID generated automatically | PASS |
| Missing X-Consumer-ID | UNKNOWN_CONSUMER fallback | PASS |
| Missing Idempotency-Key | Accepted with empty idempotency key | PASS |
| Invalid Content-Type | Rejected | PASS |

## IFL_SO_INBOUND Responsibility

IFL_SO_INBOUND is responsible for OAuth authentication, HTTPS endpoint exposure, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

IFL_SO_INBOUND is not responsible for SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## Correlation Strategy

X-Correlation-ID is optional. IFL_SO_INBOUND preserves an incoming value when provided and generates a UUID when the header is missing. The ACK response returns correlationId, and correlationId is used for operational traceability across runtime monitoring, error responses, and JMS processing.

## Idempotency Strategy

Idempotency-Key is optional in the current POC implementation. Current inbound flow preserves idempotency key when provided but does not reject requests when missing. Missing Idempotency-Key is accepted with an empty idempotency key. Validation and duplicate handling are deferred to the orchestration layer. A future production implementation may enforce mandatory idempotency.

## SAP Integration Suite Header Handling Lessons Learned

Inbound HTTP headers were not consistently accessible throughout runtime processing. The final implementation pattern captures required inbound headers into Exchange Properties immediately after the HTTPS Sender and uses Exchange Properties during subsequent processing.

| Header | Exchange Property usage |
| --- | --- |
| Content-Type | Header validation and JSON transport check |
| X-Correlation-ID | Preserve incoming correlation ID when provided |
| X-Consumer-ID | Consumer identification and UNKNOWN_CONSUMER fallback |
| Idempotency-Key | Idempotency preservation and empty fallback |

## Validation Approach

The executable POC flow uses Groovy-based validation. JSON Schema Validation is not used because the target SAP Integration Suite tenant did not provide a native JSON Schema Validator component. EDI Validator and XML Validator are not valid substitutes for JSON payload validation. JSON schema validation remains a future option depending on tenant capabilities and runtime availability.

## JMS Configuration

JMS publication from IFL_SO_INBOUND to JMS_SO_INBOUND was successfully validated through runtime testing. Messages were successfully published and visible in JMS monitoring.

| Setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |

## Monitoring And Logging Strategy

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Normal operation does not depend on MPL custom properties.

| Mode | Usage |
| --- | --- |
| Production | INFO log level |
| Troubleshooting | Temporary Debug or Trace |

Success path logging does not include payload logging and records only operational metadata. Error path logging does not persist payloads by default and uses Trace mode only for deep troubleshooting. This approach supports security, storage optimization, and operational best practices.

## Security Strategy

Current POC authentication uses OAuth2 Client Credentials against the SAP Integration Suite Runtime Endpoint. Future production runtime keeps Integration Suite behind SAP API Management. API Management will provide OAuth enforcement, consumer separation, rate limiting, spike arrest, and analytics.

## POC Decisions

| Decision | Title | Description | Reason |
| --- | --- | --- | --- |
| D-001 | No CAP Layer | No CAP layer is included in the approved runtime. | Approved architecture is APIM + Integration Suite + JMS + SAP Standard APIs. |
| D-002 | No payload logging on successful processing | Successful transactions do not log payload bodies. | Security and operational best practices. |
| D-003 | Header normalization immediately after HTTPS Sender | Inbound headers are captured into Exchange Properties at the start of the iFlow. | Runtime header propagation inconsistencies. |
| D-004 | JMS decoupling between inbound and orchestration layers | JMS separates inbound acceptance from SAP order processing. | Resilience, replay capability, retry support, and consumer decoupling. |
| D-005 | Business Validation Deferred to Orchestration Layer | Business validation is intentionally deferred to IFL_SO_ORCHESTRATION and SAP S/4HANA. The inbound flow validates only transport and integration concerns. | Avoid duplication of SAP business logic inside Integration Suite and maintain Clean Core principles. |

## Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| Current | IFL_SO_INBOUND | COMPLETED |
| Next | IFL_SO_ORCHESTRATION | JMS consumption, idempotency enforcement, SAP Sales Order API invocation, retry strategy, DLQ strategy, callback notifications, and SAP business validation |

## Clean Core Alignment

The solution uses SAP standard Sales Order APIs only. No BAPI, RFC, custom Z services, S/4HANA core modification, custom persistence, or custom canonical model is introduced.

## Document History

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-06-01 | Initial architecture definition |
| 1.1 | 2026-06-04 | Updated with IFL_SO_INBOUND POC implementation decisions and lessons learned |
| 1.2 | 2026-06-04 | Updated with final validated IFL_SO_INBOUND runtime status and D-005 |

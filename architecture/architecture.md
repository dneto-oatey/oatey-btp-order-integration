# SAP BTP Inbound Sales Order Integration Architecture (Oatey)

## Executive Summary

This document defines the approved architecture and POC implementation decisions for the Oatey SAP BTP inbound Sales Order integration. The approved runtime remains APIM + SAP Integration Suite + JMS + SAP standard Sales Order APIs.

The IFL_SO_INBOUND POC implementation and runtime testing are complete. The inbound integration flow is operational and ready for the orchestration phase implementation.

## Approved Runtime Architecture

Consumer / Partner / EDI Source -> SAP API Management -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> SAP Standard Sales Order API -> Callback Notification.

## Scope Guardrails

Do not introduce CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA calls inside IFL_SO_INBOUND, canonical models, or payload persistence outside JMS.

## IFL_SO_INBOUND Implementation Status

| Area | Status |
| --- | --- |
| HTTPS Endpoint | Validated |
| OAuth Authentication | Validated against Integration Suite runtime endpoint |
| Header Validation | Validated |
| Correlation Handling | Validated |
| JMS Publication | Validated |
| HTTP 202 ACK Response | Validated |
| Exception Subprocess | Validated |
| Runtime Deployment | Validated |

Status: Completed. The inbound integration flow is operational and ready for IFL_SO_ORCHESTRATION build.

## Correlation Strategy

X-Correlation-ID is optional in the current implementation. IFL_SO_INBOUND preserves an incoming value when provided and generates a UUID when the header is missing. The ACK response returns correlationId, and correlationId is used for operational traceability across MPL, error responses, and JMS processing.

## Idempotency Strategy

Idempotency-Key is optional in the current POC implementation. Current inbound flow preserves idempotency key when provided but does not reject requests when missing. Validation and duplicate handling are deferred to the orchestration layer. A future production implementation may enforce mandatory idempotency.

## SAP Integration Suite Header Handling Lessons Learned

During implementation it was observed that inbound HTTP headers are not always consistently available throughout the CPI runtime. The best practice adopted is to capture inbound headers immediately after the HTTPS Sender, store values as Exchange Properties, and use Exchange Properties throughout processing.

| Header | Exchange Property usage |
| --- | --- |
| Content-Type | Content validation and parser selection |
| X-Correlation-ID | Preserve or generate correlationId |
| X-Consumer-ID | Consumer traceability; UNKNOWN_CONSUMER fallback |
| Idempotency-Key | Preserve when supplied; empty when missing |

Processing should use Exchange Properties instead of relying on direct HTTP header access after the initial capture step.

## Validation Approach

The executable POC flow uses Groovy-based validation. JSON Schema Validation is not used because the target SAP Integration Suite tenant did not provide a native JSON Schema Validator component. EDI Validator and XML Validator are not valid substitutes for JSON payload validation. JSON schema validation remains a future option depending on tenant capabilities and runtime availability.

## JMS Configuration

JMS publication from IFL_SO_INBOUND to JMS_SO_INBOUND was successfully validated through runtime testing.

| Setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |

JMS decouples inbound acceptance from orchestration and provides resilience, replay capability, retry support, and consumer decoupling.

## Monitoring Strategy

Payload logging is not performed for successful transactions. This follows operational best practices and payload minimization.

| Path | Monitoring approach |
| --- | --- |
| Success path | Standard MPL, correlationId, and queue monitoring |
| Error path | Exception subprocess, controlled error responses, and error context logging |

## Security Strategy

Current POC authentication uses OAuth2 Client Credentials against the SAP Integration Suite Runtime Endpoint. Future production runtime will place Integration Suite behind SAP API Management. API Management will provide OAuth enforcement, consumer separation, rate limiting, spike arrest, and analytics.

## POC Decisions

| Decision | Description | Reason |
| --- | --- | --- |
| D-001 | No CAP Layer | Approved architecture is APIM + Integration Suite + JMS + SAP Standard APIs |
| D-002 | No payload logging on successful processing | Security and operational best practices |
| D-003 | Header normalization immediately after HTTPS Sender | Runtime header propagation inconsistencies |
| D-004 | JMS decoupling between inbound and orchestration layers | Resilience, replay capability, retry support, and consumer decoupling |

## Clean Core Alignment

The solution uses SAP standard Sales Order APIs only. No BAPI, RFC, custom Z services, S/4HANA core modification, custom persistence, or custom canonical model is introduced.

## Document History

| Version | Date | Changes |
| --- | --- | --- |
| 1.0 | 2026-06-01 | Initial architecture definition |
| 1.1 | 2026-06-04 | Updated with IFL_SO_INBOUND POC implementation decisions and lessons learned |

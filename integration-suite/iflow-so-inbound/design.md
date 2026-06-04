# IFL_SO_INBOUND Build Specification

## Purpose

IFL_SO_INBOUND is the completed inbound POC iFlow for SAP Integration Suite. It receives SAP Sales Order API style JSON, normalizes headers into Exchange Properties, validates with Groovy, sends the original payload to JMS_SO_INBOUND, and returns HTTP 202 only after successful JMS publication.

This iFlow does not call S/4HANA. IFL_SO_ORCHESTRATION consumes JMS_SO_INBOUND and will invoke the standard SAP Sales Order API in the next phase.

## Implementation Status

Status: Completed and runtime validated.

| Capability | Status |
| --- | --- |
| HTTPS Endpoint | Validated |
| OAuth Authentication | Validated |
| Header Validation | Validated |
| Correlation Handling | Validated |
| JMS Publication | Validated |
| HTTP 202 ACK Response | Validated |
| Exception Subprocess | Validated |
| Runtime Deployment | Validated |

## Current Executable Flow

Start / HTTPS Sender -> CM_SetInitialProperties -> CM_SetHeaderValidationContext -> GS_ValidateHeaders -> GS_EnsureCorrelationId -> GS_ExtractMonitoringFields -> CM_SetPayloadValidationStatus -> GS_PrepareJmsMessage -> CM_SetJmsHeaders -> Send to JMS Receiver -> CM_SetAckResponse -> End

No JSON Schema Validation step is present in this executable flow.

## Correlation Strategy

X-Correlation-ID is optional. The iFlow preserves an incoming value when provided and generates a UUID when missing. The ACK response returns correlationId and operations use correlationId for traceability.

## Idempotency Strategy

Idempotency-Key is optional for the current POC. Current inbound flow preserves idempotency key when provided but does not reject requests when missing. Idempotency validation is deferred to IFL_SO_ORCHESTRATION. Future production may enforce mandatory idempotency.

## Header Handling Lessons Learned

Inbound HTTP headers are not always consistently available throughout the CPI runtime. The implemented best practice is to capture Content-Type, X-Correlation-ID, X-Consumer-ID, and Idempotency-Key immediately after HTTPS Sender and store them as Exchange Properties. Processing uses Exchange Properties instead of relying on direct HTTP header access.

## Validation Approach

Payload validation is Groovy-based. JSON Schema Validation is not used because the target tenant did not provide a native JSON Schema Validator component. EDI Validator and XML Validator are not valid substitutes for JSON payload validation. JSON schema validation remains a future option depending on tenant capabilities and runtime availability.

## JMS Receiver Configuration

| JMS setting | Value |
| --- | --- |
| Pattern | One-way Send to JMS Receiver |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |

JMS publication was successfully validated through runtime testing. JMS send failure returns HTTP 500 through the exception subprocess.

## ACK Response

ACK is returned only after successful Groovy validation and successful JMS publication.

| Field | Value |
| --- | --- |
| HTTP status | 202 Accepted |
| Content-Type | application/json |
| correlationId | Preserved or generated correlation ID |
| status | ACCEPTED |

## Monitoring Strategy

Payload logging is not performed for successful transactions. Success path monitoring uses standard MPL, correlationId, and queue monitoring. Error path monitoring uses exception subprocess, controlled error responses, and error context logging.

## Architecture Guardrails

No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA calls inside IFL_SO_INBOUND, canonical model, or payload persistence outside JMS.

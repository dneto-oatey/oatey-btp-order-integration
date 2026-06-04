# IFL_SO_INBOUND Build Specification

## Purpose

IFL_SO_INBOUND is the completed inbound POC iFlow for SAP Integration Suite. It receives SAP Sales Order API style JSON, normalizes headers into Exchange Properties, performs transport/integration validation, sends the original payload to JMS_SO_INBOUND, and returns HTTP 202 only after successful JMS publication.

This iFlow does not call S/4HANA. IFL_SO_ORCHESTRATION is responsible for consuming JMS_SO_INBOUND and invoking the standard SAP Sales Order API in the next phase.

## Status

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

## Current Executable Flow

Start / HTTPS Sender -> CM_SetInitialProperties -> CM_SetHeaderValidationContext -> GS_ValidateHeaders -> GS_EnsureCorrelationId -> GS_ExtractMonitoringFields -> CM_SetPayloadValidationStatus -> GS_PrepareJmsMessage -> CM_SetJmsHeaders -> Send to JMS Receiver -> CM_SetAckResponse -> End

No JSON Schema Validation step is present in this executable flow.

## Responsibilities

IFL_SO_INBOUND is responsible for OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

IFL_SO_INBOUND is not responsible for SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## Header Handling Lesson Learned

Inbound HTTP headers were not consistently accessible throughout runtime processing. The final pattern captures Content-Type, X-Correlation-ID, X-Consumer-ID, and Idempotency-Key into Exchange Properties immediately after HTTPS Sender and uses Exchange Properties during subsequent processing.

## Correlation And Idempotency

X-Correlation-ID is optional. Incoming values are preserved, missing values generate UUIDs, and ACK responses return correlationId.

Idempotency-Key is optional for the POC. Current inbound flow preserves idempotency key when provided but does not reject requests when missing. Missing Idempotency-Key is accepted with an empty idempotency key. Idempotency enforcement is deferred to orchestration.

## Validation Boundary

The inbound flow validates only transport and integration concerns. Business validation is intentionally deferred to IFL_SO_ORCHESTRATION and SAP S/4HANA through the SAP Sales Order API.

## JMS Validation

JMS publication to JMS_SO_INBOUND was successfully validated through runtime testing. Messages were successfully published and visible in JMS monitoring.

| JMS setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |

## Monitoring And Logging

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Production uses INFO log level. Troubleshooting may temporarily use Debug or Trace. Normal operation does not depend on MPL custom properties.

Success path uses no payload logging and records only operational metadata. Error path uses controlled error responses and error context logging, with Trace mode reserved for deep troubleshooting.

## Architecture Guardrails

No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA calls inside IFL_SO_INBOUND, canonical model, or payload persistence outside JMS.

# IFL_SO_INBOUND Manual Build Guide

## Purpose

Manual SAP Integration Suite build guide for the completed IFL_SO_INBOUND POC implementation. Runtime remains APIM -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> standard SAP Sales Order API.

## Status

Status: COMPLETED. Deployment, OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer fallback, idempotency fallback, JMS publication, ACK 202 response, and exception subprocess were successfully validated in runtime.

## Current Executable Main Flow

Start / HTTPS Sender -> CM_SetInitialProperties -> CM_SetHeaderValidationContext -> GS_ValidateHeaders -> GS_EnsureCorrelationId -> GS_ExtractMonitoringFields -> CM_SetPayloadValidationStatus -> GS_PrepareJmsMessage -> CM_SetJmsHeaders -> Send to JMS Receiver -> CM_SetAckResponse -> End

No JSON Schema Validation step is present.

## Final Runtime Behavior

| Scenario | Runtime result | Status |
| --- | --- | --- |
| Happy Path | 202 ACCEPTED | PASS |
| Missing X-Correlation-ID | UUID generated automatically | PASS |
| Missing X-Consumer-ID | UNKNOWN_CONSUMER fallback | PASS |
| Missing Idempotency-Key | Accepted with empty idempotency key | PASS |
| Invalid Content-Type | Rejected | PASS |

## Header Handling Lesson Learned

Inbound HTTP headers were not consistently accessible throughout runtime processing. Capture Content-Type, X-Correlation-ID, X-Consumer-ID, and Idempotency-Key into Exchange Properties immediately after HTTPS Sender and use Exchange Properties for subsequent processing.

## Responsibility Boundary

IFL_SO_INBOUND handles OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

IFL_SO_INBOUND does not handle SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## Monitoring Lesson Learned

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Production uses INFO log level. Troubleshooting may temporarily use Debug or Trace. Normal operation does not depend on MPL custom properties.

## Logging Strategy

Success path: no payload logging, only operational metadata. Error path: no payload persistence by default, with Trace mode used for deep troubleshooting. Reasons: security, storage optimization, and operational best practices.

## Next Phase

IFL_SO_ORCHESTRATION will implement JMS consumption, idempotency enforcement, SAP Sales Order API invocation, retry strategy, DLQ strategy, callback notifications, and SAP business validation.

# IFL_SO_INBOUND Implementation Checklist

## Source Of Truth

Checklist for the current SAP Integration Suite implementation findings for IFL_SO_INBOUND.

## Architecture Guardrails

| Rule | Required behavior |
| --- | --- |
| Runtime path | APIM -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION |
| S/4HANA call | Not allowed inside IFL_SO_INBOUND |
| Payload model | Preserve original SAP Sales Order JSON |
| Canonical model | Not allowed |
| Payload persistence | JMS only |
| Excluded services | No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, or custom Z service |

## Current Executable Main Flow

Start / HTTPS Sender -> CM_SetInitialProperties -> CM_SetHeaderValidationContext -> GS_ValidateHeaders -> GS_EnsureCorrelationId -> GS_ExtractMonitoringFields -> CM_SetPayloadValidationStatus -> GS_PrepareJmsMessage -> CM_SetJmsHeaders -> Send to JMS Receiver -> CM_SetAckResponse -> End

No JSON Schema Validation step is present.

## Required Checks

| Area | Done when |
| --- | --- |
| JSON validation | Groovy validation is used because native JSON Schema Validator is unavailable |
| Validator substitutes | EDI Validator and XML Validator are not used for JSON payload validation |
| GS_ExtractMonitoringFields | Uses message.getBody(java.io.Reader) and new JsonSlurper().parse(reader) |
| Body preservation | No script or Content Modifier changes body before JMS Send |
| JMS pattern | One-way Send to JMS Receiver; no Request Reply |
| JMS queue | JMS_SO_INBOUND |
| JMS Access Type | Non-Exclusive |
| JMS Retention | 7 days |
| Transfer Exchange Properties | Enabled |
| Compression | Enabled if configured in tenant |
| Encryption | Enabled if configured in tenant |
| ACK rule | HTTP 202 only after successful JMS Send |
| JMS failure | HTTP 500 through local exception subprocess |
| Exception subprocess | GS_BuildErrorContext -> CM_SetErrorResponse -> End |

## Header Behavior

| Header | POC behavior | Future APIM behavior |
| --- | --- | --- |
| Content-Type | Mandatory application/json | Same |
| X-Correlation-ID | Optional; CPI generates UUID | Same or APIM may pass through |
| Idempotency-Key | Optional; empty when missing | Consumer supplied or APIM fallback generation |
| X-Consumer-ID | Optional; UNKNOWN_CONSUMER when missing | APIM injects from authenticated app/API Product |

## Definition Of Done

| Requirement | Done when |
| --- | --- |
| Main flow | Matches the exact executable sequence above |
| Validation path | Invalid JSON/payload returns 400 or 422 and does not send to JMS |
| Success path | Valid payload creates JMS_SO_INBOUND message and returns 202 |
| Error response | JSON response with Content-Type application/json and CamelHttpResponseCode from httpStatus |
| Scope | No excluded service or architecture redesign appears in the iFlow |

# IFL_SO_INBOUND Build Specification

## Purpose

`IFL_SO_INBOUND` is the completed inbound SAP Integration Suite iFlow. It receives SAP Sales Order API style JSON, normalizes inbound headers into Exchange Properties, performs transport/integration validation, sends the original payload to `JMS_SO_INBOUND`, and returns HTTP 202 only after successful JMS publication.

This iFlow does not call S/4HANA and does not perform SAP business validation.

## Status

Status: COMPLETED and runtime validated.

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

## Validated Architecture Context

```text
APIM
-> IFL_SO_INBOUND
-> JMS_SO_INBOUND
-> IFL_SO_ORCHESTRATION
-> SAP Standard API API_SALES_ORDER_SRV via HTTP Receiver
-> DLQ_SO_INBOUND on failure
-> Optional manual replay using IFL_SO_REPROCESS_DLQ
```

## Current Executable Flow

```text
Start / HTTPS Sender
-> CM_SetInitialProperties
-> GS_ValidateHeaders
-> GS_EnsureCorrelationId
-> GS_ExtractMonitoringFields
-> CM_SetPayloadValidationStatus
-> GS_PrepareJmsMessage
-> CM_SetJmsHeaders
-> Send to JMS Receiver
-> CM_SetAckResponse
-> End
```

`CM_SetHeaderValidationContext` is not part of the executable flow. No JSON Schema Validation step is present in this executable flow.

## CM_SetInitialProperties Header Capture

`CM_SetInitialProperties` captures the following immediately after HTTPS Sender:

- `Content-Type`
- `X-Correlation-ID`
- `X-Consumer-ID`
- `Idempotency-Key`
- `expectedContentType`
- `jmsQueueName`
- `validationStatus`
- `payloadPreservationMode`
- `inboundReceivedAt`

Processing uses Exchange Properties instead of relying on direct HTTP header access later in the CPI runtime.

## Responsibilities

`IFL_SO_INBOUND` is responsible for OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

`IFL_SO_INBOUND` is not responsible for SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## Correlation And Idempotency

`X-Correlation-ID` is optional. Incoming values are preserved, missing values generate UUIDs, and ACK responses return `correlationId`.

`Idempotency-Key` is optional for the current implementation. The inbound flow preserves it when provided but does not reject requests when missing. Missing `Idempotency-Key` is accepted with an empty idempotency key. Idempotency enforcement is deferred to orchestration or future production policy.

## JMS Validation

JMS publication to `JMS_SO_INBOUND` was successfully validated through runtime testing. Messages were successfully published and visible in JMS monitoring.

| JMS setting | Value |
| --- | --- |
| Queue | `JMS_SO_INBOUND` |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |

## MPL Custom Headers

Inbound custom headers:

- `ConsumerID`
- `correlationId`
- `IdempotencyKey`
- `validationStatus`

Scripts that add MPL custom headers must only add a custom header property when the value exists. Empty values must not be written because MPL appends values and does not overwrite previous custom header values.

## Monitoring And Logging

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Production uses INFO log level. Troubleshooting may temporarily use Debug or Trace.

Success path uses no payload logging and records only operational metadata. Error path uses controlled error responses and error context logging, with Trace mode reserved for deep troubleshooting.

## Architecture Guardrails

No PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA calls inside `IFL_SO_INBOUND`, canonical model, or payload persistence outside JMS.

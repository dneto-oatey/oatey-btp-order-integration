# IFL_SO_INBOUND Manual Build Guide

## Purpose

Manual SAP Integration Suite build guide for the completed `IFL_SO_INBOUND` implementation.

Runtime remains:

```text
APIM -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> SAP Standard API API_SALES_ORDER_SRV
```

## Status

Status: COMPLETED. Deployment, OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer fallback, idempotency fallback, JMS publication, ACK 202 response, and exception subprocess were successfully validated in runtime.

## Current Executable Main Flow

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

No JSON Schema Validation step is present. `CM_SetHeaderValidationContext` is not part of the executable sequence.

## Final Runtime Behavior

| Scenario | Runtime result | Status |
| --- | --- | --- |
| Happy Path | 202 ACCEPTED | PASS |
| Missing X-Correlation-ID | UUID generated automatically | PASS |
| Missing X-Consumer-ID | UNKNOWN_CONSUMER fallback | PASS |
| Missing Idempotency-Key | Accepted with empty idempotency key | PASS |
| Invalid Content-Type | Rejected | PASS |

## Header Handling Lesson Learned

Inbound HTTP headers were not consistently accessible throughout runtime processing. Capture `Content-Type`, `X-Correlation-ID`, `X-Consumer-ID`, and `Idempotency-Key` into Exchange Properties immediately after HTTPS Sender and use Exchange Properties for subsequent processing.

## Responsibility Boundary

`IFL_SO_INBOUND` handles OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

`IFL_SO_INBOUND` does not handle SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## MPL Custom Headers

Inbound custom headers:

- `ConsumerID`
- `correlationId`
- `IdempotencyKey`
- `validationStatus`

`GS_LogBeforeJms` or `GS_SetMplCustomHeaders`, when present, must only add custom header properties when values exist. Do not write empty custom header values.

## Monitoring Lesson Learned

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Production uses INFO log level. Troubleshooting may temporarily use Debug or Trace. Normal operation does not depend on MPL custom properties.

## Logging Strategy

Success path: no payload logging, only operational metadata. Error path: no payload persistence by default, with Trace mode used for deep troubleshooting. Reasons: security, storage optimization, and operational best practices.

## Downstream Runtime

`IFL_SO_ORCHESTRATION` consumes `JMS_SO_INBOUND`, performs CSRF handling, calls SAP standard API `API_SALES_ORDER_SRV` through HTTP Receiver, and routes failures to `DLQ_SO_INBOUND`.

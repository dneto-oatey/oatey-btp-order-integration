# IFL_SO_ORCHESTRATION Manual Build Guide

## Purpose

Manual SAP Integration Suite build guide for the runtime-validated `IFL_SO_ORCHESTRATION` implementation. This document is implementation-ready and keeps the approved architecture unchanged.

## Runtime Validation Summary

The iFlow uses HTTP Receiver instead of OData Receiver for SAP Sales Order creation. The OData adapter attempted to parse the JSON deep insert payload as XML/Atom. HTTP Receiver successfully reached SAP `API_SALES_ORDER_SRV` and SAP returned the functional business validation error `Sales area 1000 10 00 does not exist`, confirming connectivity and SAP API invocation.

Validated:

| Capability | Result |
| --- | --- |
| JMS consumption | Working |
| CSRF token fetch | Working |
| SAP session cookie handling | Working |
| HTTP POST to API_SALES_ORDER_SRV | Working; reaches SAP API |
| SAP business validation | Functional SAP error returned |
| DLQ routing | Failed message routed to `DLQ_SO_INBOUND` |

## Runtime Flow

```text
JMS_SO_INBOUND
-> CM_ReadJmsMetadata
-> GS_ValidateConsumedMessage
-> GS_PrepareSapSalesOrderRequest
-> CM_PrepareCsrfFetch
-> HTTP GET /sap/opu/odata/sap/API_SALES_ORDER_SRV/ with x-csrf-token Fetch
-> GS_ExtractCsrfToken
-> CM_PrepareSapPostRequest
-> HTTP POST /sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder
-> GS_HandleSapResponse
-> CM_SetSuccessContext
-> End
```

## Exception Flow

```text
Exception Start
-> CM_SetFailedContext
-> GS_PrepareDlqPayload
-> CM_SetDlqContext
-> JMS Receiver DLQ_SO_INBOUND
-> End
```

No `GS_BuildOrchestrationErrorContext` script exists. `GS_PrepareDlqPayload.groovy` captures current CPI exception context, classifies errors, sanitizes sensitive values, preserves replay governance metadata, and builds the complete DLQ envelope.

## CSRF Payload Preservation

`CM_PrepareCsrfFetch` must save `sapRequestPayload = ${body}` before the HTTP GET token fetch because the GET response replaces the message body. `CM_PrepareSapPostRequest` restores the body from `sapRequestPayload` before POST.

Never log CSRF token or SAP cookie to MPL.

## DLQ Payload Resolution

`GS_PrepareDlqPayload` resolves `originalPayload` using this priority:

1. Message property `sapRequestPayload`
2. Message property `originalPayload`
3. Current message body as fallback

The DLQ envelope must include `replayCount` and `maxReplayCount`. Defaults are `0` and `1` when missing.

## Replay Governance

`replayCount` and `maxReplayCount` are business replay governance metadata. Do not rely on JMS technical redelivery count for replay governance.

If a replayed message fails again, keep the incoming `replayCount` in the new DLQ envelope. Do not reset it to `0`.

## Externalized Parameters

| Parameter | Purpose |
| --- | --- |
| SAP_BASE_PATH | `/sap/opu/odata/sap/API_SALES_ORDER_SRV/` |
| SAP_CREATE_PATH | `/sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder` |
| SAP_PROXY_TYPE | HTTP receiver proxy type |
| SAP_LOCATION_ID | Cloud Connector location ID when required |
| SAP_CREDENTIAL_NAME | SAP credential/security material name |
| SAP_TIMEOUT_MINUTES | HTTP receiver timeout |
| JMS_SOURCE_QUEUE | `JMS_SO_INBOUND` |
| JMS_DLQ_QUEUE | `DLQ_SO_INBOUND` |
| IDEMPOTENCY_POLICY | Idempotency behavior marker |

Do not externalize runtime values: correlationId, consumerId, idempotencyKey, csrfToken, sapCookie, sapRequestPayload, sapSalesOrderNumber, or errorMessage.

## Build Steps

1. Configure JMS Sender on `JMS_SO_INBOUND`.
2. Add `CM_ReadJmsMetadata`.
3. Add `GS_ValidateConsumedMessage`.
4. Add `GS_PrepareSapSalesOrderRequest`.
5. Add `CM_PrepareCsrfFetch` and preserve `sapRequestPayload`.
6. Configure HTTP GET CSRF fetch.
7. Add `GS_ExtractCsrfToken`.
8. Add `CM_PrepareSapPostRequest` and restore body from `sapRequestPayload`.
9. Configure HTTP POST to `API_SALES_ORDER_SRV/A_SalesOrder`.
10. Add `GS_HandleSapResponse`.
11. Add `CM_SetSuccessContext`.
12. Configure Exception Subprocess to build DLQ envelope and send to `DLQ_SO_INBOUND`.

## MPL Custom Headers

Orchestration custom headers:

- `ConsumerID`
- `correlationId`
- `IdempotencyKey`
- `processingStatus`
- `errorCategory`
- `sapResponseStatusCode`
- `replayCount`
- `maxReplayCount`

`GS_LogBeforeJms` or `GS_SetMplCustomHeaders`, when present, must only add custom header properties when values exist. Do not write empty custom header values.

## Logging

Success path logs metadata only. Payload is allowed inside the DLQ envelope but must not be logged to MPL. Never log credentials, Authorization headers, bearer tokens, CSRF token, SAP session cookie, passwords, or secrets.

## Clean Core Alignment

Do not add CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, direct custom S/4 APIs, UI, or custom persistence. SAP business validation remains in SAP S/4HANA through `API_SALES_ORDER_SRV`.

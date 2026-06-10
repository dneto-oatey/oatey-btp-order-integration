# IFL_SO_ORCHESTRATION Implementation Checklist

## Purpose

Implementation-ready CPI checklist for the runtime-validated `IFL_SO_ORCHESTRATION` build.

## Approved Runtime

```text
APIM
-> IFL_SO_INBOUND
-> JMS_SO_INBOUND
-> IFL_SO_ORCHESTRATION
-> SAP Standard API API_SALES_ORDER_SRV via HTTP Receiver
-> DLQ_SO_INBOUND on failure
-> Optional manual replay using IFL_SO_REPROCESS_DLQ
```

## Runtime Validated Decisions

- [ ] Use HTTP Receiver, not OData Receiver, for SAP Sales Order JSON deep insert.
- [ ] Fetch CSRF token with HTTP GET before POST.
- [ ] Preserve SAP session cookie from GET and send it on POST.
- [ ] Preserve `sapRequestPayload` before CSRF GET because GET response replaces the body.
- [ ] Restore `sapRequestPayload` before HTTP POST.
- [ ] POST original SAP Sales Order JSON to `API_SALES_ORDER_SRV/A_SalesOrder`.
- [ ] Treat SAP business validation response as proof SAP API invocation reached SAP.
- [ ] Route failed messages to `DLQ_SO_INBOUND`.

## Architecture Guardrails

- [ ] Use `JMS_SO_INBOUND` as the only orchestration source queue.
- [ ] Use SAP standard API `API_SALES_ORDER_SRV`.
- [ ] Use secure credential handling for SAP API authentication.
- [ ] Preserve correlationId, idempotencyKey, and consumerId from JMS metadata.
- [ ] Preserve replayCount and maxReplayCount from message metadata.
- [ ] Do not duplicate SAP business validation in Integration Suite.
- [ ] Do not introduce PostgreSQL.
- [ ] Do not introduce Event Mesh.
- [ ] Do not introduce RFC.
- [ ] Do not introduce BAPI.
- [ ] Do not introduce custom Z APIs.
- [ ] Do not introduce custom persistence.
- [ ] Do not log payloads, CSRF tokens, cookies, credentials, or Authorization headers to MPL.

## Externalized Parameters

- [ ] SAP_BASE_PATH
- [ ] SAP_CREATE_PATH
- [ ] SAP_PROXY_TYPE
- [ ] SAP_LOCATION_ID
- [ ] SAP_CREDENTIAL_NAME
- [ ] SAP_TIMEOUT_MINUTES
- [ ] JMS_SOURCE_QUEUE
- [ ] JMS_DLQ_QUEUE
- [ ] IDEMPOTENCY_POLICY

Do not externalize runtime values: correlationId, consumerId, idempotencyKey, csrfToken, sapCookie, sapRequestPayload, sapSalesOrderNumber, errorMessage, replayCount, or maxReplayCount.

## Executable Flow Checklist

- [ ] Create iFlow `IFL_SO_ORCHESTRATION`.
- [ ] Add JMS Sender adapter for `JMS_SO_INBOUND` through `JMS_SOURCE_QUEUE`.
- [ ] Add `CM_ReadJmsMetadata`.
- [ ] Add `GS_ValidateConsumedMessage`.
- [ ] Add `GS_PrepareSapSalesOrderRequest`.
- [ ] Add `CM_PrepareCsrfFetch`.
- [ ] Configure HTTP GET `/sap/opu/odata/sap/API_SALES_ORDER_SRV/` with `x-csrf-token = Fetch`.
- [ ] Add `GS_ExtractCsrfToken`.
- [ ] Add `CM_PrepareSapPostRequest`.
- [ ] Configure HTTP POST `/sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder`.
- [ ] Add `GS_HandleSapResponse`.
- [ ] Add `CM_SetSuccessContext`.
- [ ] Add Exception Subprocess.
- [ ] Add `CM_SetFailedContext`.
- [ ] Add `GS_PrepareDlqPayload`.
- [ ] Add `CM_SetDlqContext`.
- [ ] Add JMS Receiver to send terminal messages to `DLQ_SO_INBOUND` through `JMS_DLQ_QUEUE`.

## DLQ Envelope Checklist

- [ ] `GS_PrepareDlqPayload` resolves `originalPayload` from `sapRequestPayload`, then `originalPayload`, then current body.
- [ ] DLQ envelope includes source/target, correlation, consumer, idempotency, processing, error, SAP response, retry, replay, and originalPayload fields.
- [ ] `replayCount` defaults to `0` when missing.
- [ ] `maxReplayCount` defaults to `1` when missing.
- [ ] Replayed message failure preserves incoming `replayCount`; it does not reset to `0`.
- [ ] Replay governance does not rely on JMS technical redelivery count.

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

Only add custom header properties when values exist. Do not write empty values.

## Completion Criteria

- [ ] JMS consumption works.
- [ ] CSRF token fetch works.
- [ ] SAP session cookie handling works.
- [ ] HTTP POST reaches `API_SALES_ORDER_SRV`.
- [ ] SAP business validation errors are classified as SAP_BUSINESS_ERROR.
- [ ] Missing idempotencyKey remains warning only and is not a DLQ reason by itself.
- [ ] Exception flow sends complete DLQ envelope to `DLQ_SO_INBOUND`.
- [ ] DLQ envelope supports `IFL_SO_REPROCESS_DLQ` replay governance.
- [ ] Success path logs metadata only.

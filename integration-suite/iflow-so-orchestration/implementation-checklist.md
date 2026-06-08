# IFL_SO_ORCHESTRATION Implementation Checklist

## Purpose

Implementation-ready CPI checklist for the runtime-validated IFL_SO_ORCHESTRATION build.

## Approved Runtime

APIM -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> SAP Standard Sales Order API -> DLQ

## Runtime Validated Decisions

- [ ] Use HTTP Receiver, not OData Receiver, for SAP Sales Order JSON deep insert.
- [ ] Fetch CSRF token with HTTP GET before POST.
- [ ] Preserve SAP session cookie from GET and send it on POST.
- [ ] POST original SAP Sales Order JSON to API_SALES_ORDER_SRV/A_SalesOrder.
- [ ] Treat SAP business validation response as proof SAP API invocation reached SAP.

## Architecture Guardrails

- [ ] Use JMS_SO_INBOUND as the only orchestration source queue.
- [ ] Use SAP standard API API_SALES_ORDER_SRV.
- [ ] Use secure credential handling for SAP API authentication.
- [ ] Preserve correlationId, idempotencyKey, and consumerId from JMS metadata.
- [ ] Do not duplicate SAP business validation in Integration Suite.
- [ ] Do not introduce CAP.
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

Do not externalize runtime values: correlationId, consumerId, idempotencyKey, csrfToken, sapCookie, sapRequestPayload, sapSalesOrderNumber, or errorMessage.

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

## CPI Component Build Checklist

| Step | Component | Checklist |
| --- | --- | --- |
| 1 | JMS Sender | Consume original SAP Sales Order JSON from `JMS_SOURCE_QUEUE` |
| 2 | CM_ReadJmsMetadata | Capture correlationId, idempotencyKey, consumerId, queue names, and runtime status |
| 3 | GS_ValidateConsumedMessage | Validate technical readiness only |
| 4 | GS_PrepareSapSalesOrderRequest | Preserve original SAP JSON and prepare request context |
| 5 | CM_PrepareCsrfFetch | Set x-csrf-token Fetch, Accept application/json, and store sapRequestPayload |
| 6 | HTTP GET Receiver | Fetch CSRF token and SAP session cookie from `SAP_BASE_PATH` |
| 7 | GS_ExtractCsrfToken | Extract token/cookie, set csrfToken and sapCookie, prepare POST headers |
| 8 | CM_PrepareSapPostRequest | Restore sapRequestPayload and set token, cookie, JSON, correlation, and idempotency headers |
| 9 | HTTP POST Receiver | POST to `SAP_CREATE_PATH` using SAP standard API |
| 10 | GS_HandleSapResponse | Classify SAP success, SAP business error, transient error, auth/config error, or technical error |
| 11 | CM_SetSuccessContext | Set success metadata with Create actions only |
| 12 | Exception Subprocess | Route failures through GS_PrepareDlqPayload and DLQ receiver |

## Completion Criteria

- [ ] JMS consumption works.
- [ ] CSRF token fetch works.
- [ ] SAP session cookie handling works.
- [ ] HTTP POST reaches API_SALES_ORDER_SRV.
- [ ] SAP business validation errors are classified as SAP_BUSINESS_ERROR.
- [ ] Missing idempotencyKey remains warning only and is not a DLQ reason by itself.
- [ ] Exception flow sends complete DLQ envelope to DLQ_SO_INBOUND.
- [ ] Success path logs metadata only.

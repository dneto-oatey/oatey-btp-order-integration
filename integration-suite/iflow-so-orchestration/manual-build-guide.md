# IFL_SO_ORCHESTRATION Manual Build Guide

## Purpose

Manual SAP Integration Suite build guide for the runtime-validated IFL_SO_ORCHESTRATION implementation. This document is implementation-ready and keeps the approved architecture unchanged.

## Runtime Validation Summary

The iFlow uses HTTP Receiver instead of OData Receiver for SAP Sales Order creation. The OData adapter attempted to parse the JSON deep insert payload as XML/Atom. HTTP Receiver successfully reached SAP API_SALES_ORDER_SRV and SAP returned the functional business validation error `Sales area 1000 10 00 does not exist`, confirming connectivity and SAP API invocation.

Validated:

| Capability | Result |
| --- | --- |
| JMS consumption | Working |
| CSRF token fetch | Working |
| SAP session cookie handling | Working |
| HTTP POST to API_SALES_ORDER_SRV | Working; reaches SAP API |
| SAP business validation | Functional SAP error returned |

## Runtime Flow

JMS_SO_INBOUND -> CM_ReadJmsMetadata -> GS_ValidateConsumedMessage -> GS_PrepareSapSalesOrderRequest -> CM_PrepareCsrfFetch -> HTTP GET /sap/opu/odata/sap/API_SALES_ORDER_SRV/ with x-csrf-token Fetch -> GS_ExtractCsrfToken -> CM_PrepareSapPostRequest -> HTTP POST /sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder -> GS_HandleSapResponse -> CM_SetSuccessContext -> End

## Exception Flow

Exception Start -> CM_SetFailedContext -> GS_PrepareDlqPayload -> CM_SetDlqContext -> JMS Receiver DLQ_SO_INBOUND -> End

No `GS_BuildOrchestrationErrorContext` script exists. `GS_PrepareDlqPayload.groovy` captures current CPI exception context, classifies errors, sanitizes sensitive values, and builds the complete DLQ envelope.

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

## 1. Configure JMS Sender

| Setting | Value |
| --- | --- |
| Component | JMS Sender |
| Adapter type | JMS |
| Queue | Externalized `JMS_SOURCE_QUEUE` |
| Message body | Original SAP Sales Order JSON |
| Expected metadata | correlationId, idempotencyKey, consumerId |

## 2. Add CM_ReadJmsMetadata

Capture correlationId, idempotencyKey, consumerId, sourceQueueName, dlqQueueName, orchestrationReceivedAt, processingStatus, idempotency policy, and idempotency warning. Use Create action only.

## 3. Add GS_ValidateConsumedMessage

Validate JSON and integration metadata only. Do not validate customer, material, pricing, partner determination, sales area, or SAP business rules.

## 4. Add GS_PrepareSapSalesOrderRequest

Preserve the original SAP Sales Order JSON body and set request context fields such as sapApiOperation, sapRequestPrepared, payloadPreservationMode, purchaseOrderByCustomer, and soldToParty.

## 5. Add CM_PrepareCsrfFetch

| Field | Value |
| --- | --- |
| Header x-csrf-token | Fetch |
| Header Accept | application/json |
| Exchange Property sapRequestPayload | `${body}` |

The following receiver settings are externalized: SAP_BASE_PATH, SAP_PROXY_TYPE, SAP_LOCATION_ID, SAP_CREDENTIAL_NAME, SAP_TIMEOUT_MINUTES.

## 6. Configure HTTP GET CSRF Fetch

| Setting | Value |
| --- | --- |
| Receiver adapter type | HTTP |
| Method | GET |
| Path | Externalized `SAP_BASE_PATH` |
| Header | x-csrf-token = Fetch |
| Purpose | Retrieve CSRF token and SAP session cookie |

## 7. Add GS_ExtractCsrfToken

`GS_ExtractCsrfToken.groovy` extracts `x-csrf-token` and `set-cookie` from the SAP GET response, builds the Cookie header, sets properties `csrfToken` and `sapCookie`, and explicitly sets message headers `x-csrf-token`, `X-CSRF-Token`, and `Cookie` for POST compatibility.

Never log token or cookie to MPL.

## 8. Add CM_PrepareSapPostRequest

| Field | Value |
| --- | --- |
| Body | `${property.sapRequestPayload}` |
| Header x-csrf-token | Property csrfToken |
| Header X-CSRF-Token | Property csrfToken when required by runtime |
| Header Cookie | Property sapCookie |
| Header Content-Type | application/json |
| Header Accept | application/json |
| Header X-Correlation-ID | Property correlationId |
| Header Idempotency-Key | Property idempotencyKey |

## 9. Configure HTTP POST Sales Order Create

| Setting | Value |
| --- | --- |
| Receiver adapter type | HTTP |
| Method | POST |
| Path | Externalized `SAP_CREATE_PATH` |
| Target API | API_SALES_ORDER_SRV / A_SalesOrder |
| Authentication | Externalized `SAP_CREDENTIAL_NAME` |
| Payload | Original SAP Sales Order JSON restored from sapRequestPayload |

## 10. Add GS_HandleSapResponse

Classify SAP response. SAP business validation errors such as `Sales area 1000 10 00 does not exist` prove that connectivity worked and SAP S/4HANA performed business validation.

## 11. Add CM_SetSuccessContext

Set processingStatus, sapSalesOrderNumber, callbackStatus, completedAt, and MPL custom status using Create action only.

## 12. Configure Exception Subprocess

| Component | Configuration |
| --- | --- |
| Exception Start | Catch validation, SAP, callback, and technical errors |
| CM_SetFailedContext | Set initial error fields when available; GS_PrepareDlqPayload finalizes classification |
| GS_PrepareDlqPayload | Build complete DLQ envelope, capture exception context, sanitize sensitive fields, preserve original payload in DLQ body |
| CM_SetDlqContext | Set DLQ routing headers/properties only |
| JMS Receiver | Send DLQ envelope to externalized `JMS_DLQ_QUEUE` |

## Logging

Success path logs metadata only. Payload is allowed inside the DLQ envelope but must not be logged to MPL. Never log credentials, Authorization headers, bearer tokens, CSRF token, SAP session cookie, passwords, or secrets.

## Clean Core Alignment

Do not add CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, direct custom S/4 APIs, UI, or custom persistence. SAP business validation remains in SAP S/4HANA through API_SALES_ORDER_SRV.

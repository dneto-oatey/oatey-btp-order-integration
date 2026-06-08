# IFL_SO_ORCHESTRATION Build Specification

## Purpose

IFL_SO_ORCHESTRATION consumes accepted SAP Sales Order JSON messages from JMS_SO_INBOUND, invokes SAP standard API_SALES_ORDER_SRV through HTTP Receiver, classifies the SAP response, and routes failures to DLQ_SO_INBOUND.

## Runtime Validation

The runtime was validated with HTTP Receiver instead of OData Receiver. The OData adapter attempted to parse the JSON deep insert payload as XML/Atom, so the implementation uses HTTP Receiver for both CSRF token fetch and Sales Order POST.

Validated results:

| Capability | Result |
| --- | --- |
| JMS consumption | Working |
| CSRF token fetch | Working |
| SAP session cookie handling | Working |
| HTTP POST to API_SALES_ORDER_SRV | Reaches SAP |
| SAP business validation response | SAP returned functional error: Sales area 1000 10 00 does not exist |
| Connectivity conclusion | Integration connectivity and SAP API invocation are confirmed |

## Approved Runtime

APIM -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> SAP Standard Sales Order API -> DLQ

## Executable Flow

JMS_SO_INBOUND -> CM_ReadJmsMetadata -> GS_ValidateConsumedMessage -> GS_PrepareSapSalesOrderRequest -> CM_PrepareCsrfFetch -> HTTP GET /sap/opu/odata/sap/API_SALES_ORDER_SRV/ with x-csrf-token Fetch -> GS_ExtractCsrfToken -> CM_PrepareSapPostRequest -> HTTP POST /sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder -> GS_HandleSapResponse -> CM_SetSuccessContext -> End

## Exception Flow

Exception Start -> CM_SetFailedContext -> GS_PrepareDlqPayload -> CM_SetDlqContext -> JMS Receiver DLQ_SO_INBOUND -> End

## HTTP Receiver Decision

Use HTTP Receiver for SAP Sales Order creation. Do not use OData Receiver for this JSON deep insert flow because the OData adapter can attempt XML/Atom parsing and reject the JSON payload before SAP business validation.

## CSRF And Cookie Handling

`GS_ExtractCsrfToken.groovy` extracts `x-csrf-token` and `set-cookie` from the SAP GET response. It sets runtime properties `csrfToken` and `sapCookie`, and explicitly sets both `x-csrf-token` and `X-CSRF-Token` headers for POST compatibility.

Tokens and cookies are runtime values. Never log them to MPL.

## Externalized Parameters

| Parameter | Purpose |
| --- | --- |
| SAP_BASE_PATH | Base SAP OData service path, `/sap/opu/odata/sap/API_SALES_ORDER_SRV/` |
| SAP_CREATE_PATH | Sales Order create entity path, `/sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder` |
| SAP_PROXY_TYPE | Receiver proxy type |
| SAP_LOCATION_ID | Cloud Connector location ID when required |
| SAP_CREDENTIAL_NAME | Security material or credential alias for SAP API authentication |
| SAP_TIMEOUT_MINUTES | HTTP receiver timeout |
| JMS_SOURCE_QUEUE | Source queue, `JMS_SO_INBOUND` |
| JMS_DLQ_QUEUE | DLQ queue, `DLQ_SO_INBOUND` |
| IDEMPOTENCY_POLICY | Runtime policy marker for idempotency handling |

## Runtime Values Not Externalized

Do not externalize correlationId, consumerId, idempotencyKey, csrfToken, sapCookie, sapRequestPayload, sapSalesOrderNumber, or errorMessage. These are message/runtime values.

## SAP Business Validation

Business validation is performed by SAP S/4HANA through API_SALES_ORDER_SRV. IFL_SO_ORCHESTRATION must not duplicate customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## Architecture Guardrails

Keep the flow aligned with Clean Core and SAP standard API usage. Do not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, direct custom S/4 APIs, UI, or custom persistence. Success path logging is metadata-only and must not log the full payload to MPL.

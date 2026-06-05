# IFL_SO_ORCHESTRATION Groovy Script Specifications

## Purpose

Groovy script behavior specification for IFL_SO_ORCHESTRATION. Actual SAP CPI compatible Groovy source files are available under `integration-suite/iflow-so-orchestration/scripts/`.

## Generated Script Files

| Script | Source file |
| --- | --- |
| GS_ValidateConsumedMessage | `scripts/GS_ValidateConsumedMessage.groovy` |
| GS_PrepareSapSalesOrderRequest | `scripts/GS_PrepareSapSalesOrderRequest.groovy` |
| GS_HandleSapResponse | `scripts/GS_HandleSapResponse.groovy` |
| GS_PrepareCallbackPayload | `scripts/GS_PrepareCallbackPayload.groovy` |
| GS_PrepareDlqPayload | `scripts/GS_PrepareDlqPayload.groovy` |

## SAP Upgrade Readiness

Scripts that parse JSON use the SAP Integration Suite streaming-compatible pattern: `message.getBody(java.io.Reader)` with `new JsonSlurper().parse(reader)`. Do not use `JsonSlurper.parseText(message.getBody(String))`.

## GS_ValidateConsumedMessage

| Area | Specification |
| --- | --- |
| Purpose | Validate technical readiness of the JMS message before SAP API call |
| Input | Original SAP Sales Order JSON body; correlationId, idempotencyKey, consumerId properties |
| Output | validationStatus, processingStatus, itemCount, idempotencyWarning |
| Properties used | correlationId, idempotencyKey, consumerId, idempotencyPocPolicy |
| Headers used | correlationId, idempotencyKey, consumerId when available from JMS |
| Failure behavior | Throw controlled VALIDATION_ERROR for missing body, invalid JSON, missing correlationId, missing consumerId, or unusable payload structure |

Validation rules:

| Validation | POC behavior |
| --- | --- |
| Body missing | Fail validation |
| Invalid JSON | Fail validation |
| correlationId missing | Fail validation; it must exist by orchestration stage |
| consumerId missing | Fail validation unless inbound supplied UNKNOWN_CONSUMER fallback |
| idempotencyKey missing | Allow; set idempotencyWarning; do not DLQ |
| SAP business data invalid | Do not validate here; SAP S/4HANA validates through API_SALES_ORDER_SRV |

## GS_PrepareSapSalesOrderRequest

| Area | Specification |
| --- | --- |
| Purpose | Prepare request context for API_SALES_ORDER_SRV without changing the SAP payload |
| Input | Original SAP Sales Order JSON body and orchestration properties |
| Output | sapApiOperation, sapRequestPrepared, payloadPreservationMode, purchaseOrderByCustomer, soldToParty |
| Properties used | correlationId, idempotencyKey, consumerId, sapEndpoint |
| Headers used | None required; headers are set by CM_PrepareSapRequest |
| Failure behavior | Throw TECHNICAL_ERROR only when the message cannot be prepared for the receiver adapter |

Rules:

| Rule | Requirement |
| --- | --- |
| Preserve body | Keep original SAP JSON body |
| No canonical model | Do not transform to custom object |
| Optional SAP fields | Preserve IncotermsClassification, pricing, text, item, and extension fields |
| Idempotency | Keep as metadata; do not inject into SAP body |

## GS_HandleSapResponse

| Area | Specification |
| --- | --- |
| Purpose | Classify SAP API response and extract SAP Sales Order number or SAP error context |
| Input | SAP HTTP status, SAP response body, correlationId |
| Output | processingStatus, sapSalesOrderNumber, sapResponseStatusCode, sapErrorCode, sapErrorMessage, errorCategory, retryable |
| Properties used | correlationId, retryAttempt, maxRetryCount |
| Headers used | SAP HTTP status/header values from receiver adapter |
| Failure behavior | Throw controlled exception for retryable or terminal error categories |

Classification:

| Condition | Category | Retry? |
| --- | --- | --- |
| 200/201 with order number | SUCCESS | No |
| 200/201 without order number | TECHNICAL_ERROR | Yes |
| 400/409/422 SAP business error | SAP_BUSINESS_ERROR | No blind retry |
| 401/403 | SAP_AUTH_CONFIG_ERROR | Limited |
| 408/429/500/502/503/504 | SAP_TRANSIENT_ERROR | Yes |
| Timeout/network error | SAP_TRANSIENT_ERROR | Yes |

## GS_PrepareCallbackPayload

| Area | Specification |
| --- | --- |
| Purpose | Build SUCCESS or FAILED callback payload |
| Input | correlationId, consumerId, processingStatus, sapSalesOrderNumber, SAP error context |
| Output | Callback JSON body |
| Properties used | correlationId, consumerId, sapSalesOrderNumber, errorCategory, errorCode, errorMessage, sapErrorCode, sapErrorMessage |
| Headers used | X-Correlation-ID and X-Consumer-ID are set by callback header Content Modifier or receiver configuration |
| Failure behavior | Throw CALLBACK_ERROR when callback payload cannot be built |

Required callback fields:

| Field | Source |
| --- | --- |
| correlationId | Exchange Property |
| consumerId | Exchange Property |
| status | processingStatus |
| salesOrderNumber | sapSalesOrderNumber when successful |
| processingTimestamp | current UTC timestamp |
| errors | SAP or technical error context when failed |

## GS_PrepareDlqPayload

| Area | Specification |
| --- | --- |
| Purpose | Build DLQ envelope for terminal or retry-exhausted failures |
| Input | Original SAP Sales Order JSON body and error context |
| Output | DLQ JSON body |
| Properties used | correlationId, idempotencyKey, consumerId, errorCategory, errorCode, errorMessage, sapErrorCode, sapErrorMessage, retryAttempt |
| Headers used | DLQ headers set by CM_SetDlqContext |
| Failure behavior | If DLQ payload cannot be built, throw TECHNICAL_ERROR and raise operations alert |

DLQ payload must include original payload and replay guidance. Missing idempotencyKey alone is not a DLQ reason in the POC.

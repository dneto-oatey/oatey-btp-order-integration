# IFL_SO_ORCHESTRATION Groovy Script Specifications

## Purpose

Groovy script behavior specification for IFL_SO_ORCHESTRATION. Actual SAP CPI compatible Groovy source files are available under `integration-suite/iflow-so-orchestration/scripts/`.

No `GS_BuildOrchestrationErrorContext` script exists. Enterprise error context capture and final DLQ envelope completeness are handled by `GS_PrepareDlqPayload.groovy`.

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
| Failure behavior | Throw controlled VALIDATION_ERROR for invalid JSON, missing correlationId, missing consumerId, or unusable payload structure |

## GS_PrepareSapSalesOrderRequest

| Area | Specification |
| --- | --- |
| Purpose | Prepare request context for API_SALES_ORDER_SRV without changing the SAP payload |
| Output | sapApiOperation, sapRequestPrepared, payloadPreservationMode, purchaseOrderByCustomer, soldToParty |
| Rule | Preserve original SAP JSON and do not create a canonical model |

## GS_HandleSapResponse

| Area | Specification |
| --- | --- |
| Purpose | Classify SAP API response and extract SAP Sales Order number or SAP error context |
| SAP 400, 409, 422 | SAP_BUSINESS_ERROR, non-retryable |
| SAP 401, 403 | SAP_AUTH_CONFIG_ERROR |
| SAP 408, 429, 500, 502, 503, 504 | SAP_TRANSIENT_ERROR, retryable |

## GS_PrepareCallbackPayload

| Area | Specification |
| --- | --- |
| Purpose | Build SUCCESS or FAILED callback payload |
| Required fields | correlationId, consumerId, status, processingTimestamp, salesOrderNumber on success, errors on failure |

## GS_PrepareDlqPayload

| Area | Specification |
| --- | --- |
| Purpose | Build complete enterprise-grade DLQ envelope and capture/classify current exception context |
| Input | Original failed payload, CPI properties, CamelExceptionCaught when available |
| Output | JSON DLQ envelope as message body |
| Required envelope fields | sourceIFlow, sourceQueue, targetQueue, correlationId, consumerId, idempotencyKey, processingStatus, failureTimestamp, errorCategory, errorCode, errorMessage, sapResponseStatusCode, sapErrorCode, sapErrorMessage, retryAttempt, maxRetryCount, replayRequired, replayInstruction, originalPayload |
| Security | Sanitize error fields; never expose credentials, Authorization headers, bearer tokens, passwords, or secrets |
| Payload handling | Preserve originalPayload inside DLQ envelope only; do not log payload to MPL |
| Idempotency | Missing idempotencyKey alone is not a DLQ reason; when routed for another failure, envelope includes empty idempotencyKey and replayInstruction requires idempotency review |

## DLQ Classification Rules

| Rule | Classification |
| --- | --- |
| Existing errorCategory exists | Preserve it |
| SAP response status 400, 409, 422 | SAP_BUSINESS_ERROR |
| SAP response status 401, 403 | SAP_AUTH_CONFIG_ERROR |
| SAP response status 408, 429, 500, 502, 503, 504 | SAP_TRANSIENT_ERROR |
| Exception message indicates timeout or connection failure | SAP_TRANSIENT_ERROR |
| validationStatus FAILED or errorCode invalid JSON / missing metadata | VALIDATION_ERROR |
| Otherwise | TECHNICAL_ERROR |

## Clean Core Alignment

Scripts do not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, direct custom S/4 APIs, UI, or custom persistence. SAP business validation remains in SAP S/4HANA through API_SALES_ORDER_SRV.

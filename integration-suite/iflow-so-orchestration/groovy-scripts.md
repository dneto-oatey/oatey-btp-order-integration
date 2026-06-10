# IFL_SO_ORCHESTRATION Groovy Script Specifications

## Purpose

Groovy script behavior specification for `IFL_SO_ORCHESTRATION`. Actual SAP CPI compatible Groovy source files are available under `integration-suite/iflow-so-orchestration/scripts/`.

No `GS_BuildOrchestrationErrorContext` script exists. Enterprise error context capture and final DLQ envelope completeness are handled by `GS_PrepareDlqPayload.groovy`.

## Generated Script Files

| Script | Source file |
| --- | --- |
| GS_ValidateConsumedMessage | `scripts/GS_ValidateConsumedMessage.groovy` |
| GS_PrepareSapSalesOrderRequest | `scripts/GS_PrepareSapSalesOrderRequest.groovy` |
| GS_ExtractCsrfToken | `scripts/GS_ExtractCsrfToken.groovy` |
| GS_HandleSapResponse | `scripts/GS_HandleSapResponse.groovy` |
| GS_PrepareCallbackPayload | `scripts/GS_PrepareCallbackPayload.groovy` |
| GS_PrepareDlqPayload | `scripts/GS_PrepareDlqPayload.groovy` |

## SAP Upgrade Readiness

Scripts that parse JSON use the SAP Integration Suite streaming-compatible pattern: `message.getBody(java.io.Reader)` with `new JsonSlurper().parse(reader)`. Do not use `JsonSlurper.parseText(message.getBody(String))`.

## GS_ValidateConsumedMessage

Validates technical readiness of the JMS message before SAP API call. It does not duplicate SAP business validation.

## GS_PrepareSapSalesOrderRequest

Prepares request context for `API_SALES_ORDER_SRV` without changing the SAP payload. The original SAP JSON is preserved.

## GS_ExtractCsrfToken

| Area | Specification |
| --- | --- |
| Purpose | Extract CSRF token and SAP session cookie from HTTP GET response |
| Input | SAP GET response headers `x-csrf-token` and `set-cookie` |
| Output properties | csrfToken, sapCookie |
| Output headers | x-csrf-token, X-CSRF-Token, Cookie |
| Failure behavior | Throw TECHNICAL_ERROR when token or cookie is missing |
| Security | Never log CSRF token or SAP cookie to MPL |

## GS_HandleSapResponse

Classifies SAP response. SAP 400, 409, and 422 are SAP_BUSINESS_ERROR. SAP 401 and 403 are SAP_AUTH_CONFIG_ERROR. SAP 408, 429, 500, 502, 503, and 504 are SAP_TRANSIENT_ERROR.

## GS_PrepareCallbackPayload

Builds SUCCESS or FAILED callback payload with correlationId, consumerId, status, processingTimestamp, salesOrderNumber on success, and errors on failure. Callback remains optional and is not part of the validated core path.

## GS_PrepareDlqPayload

Builds the complete enterprise-grade DLQ envelope and captures/classifies current exception context. Payload is preserved inside `originalPayload` in the DLQ envelope only and must not be logged to MPL.

`originalPayload` resolution priority:

1. Message property `sapRequestPayload`
2. Message property `originalPayload`
3. Current message body as fallback

The DLQ envelope must include:

- `sourceIFlow`
- `sourceQueue`
- `targetQueue`
- `correlationId`
- `consumerId`
- `idempotencyKey`
- `processingStatus`
- `failureTimestamp`
- `errorCategory`
- `errorCode`
- `errorMessage`
- `sapResponseStatusCode`
- `sapErrorCode`
- `sapErrorMessage`
- `retryAttempt`
- `maxRetryCount`
- `replayRequired`
- `replayInstruction`
- `replayCount`
- `maxReplayCount`
- `originalPayload`

Replay governance metadata is preserved in the DLQ envelope:

| Field | Source | Default |
| --- | --- | --- |
| `replayCount` | Message property or header | `0` |
| `maxReplayCount` | Message property or header | `1` |
| `replayed` | Message property or header | Empty |
| `replayedAt` | Message property or header | Empty |
| `replaySource` | Message property or header | Empty |
| `replayTarget` | Message property or header | Empty |
| `replayFlow` | Message property or header | Empty |

If a replayed message fails again, `GS_PrepareDlqPayload` must keep the incoming `replayCount` from the replayed message and must not reset it to `0`. This allows `IFL_SO_REPROCESS_DLQ` to enforce replay governance through `maxReplayCount`.

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

`GS_LogBeforeJms` or `GS_SetMplCustomHeaders`, when present, must only add custom header properties when values exist. Empty values must not be written because MPL appends values and does not overwrite prior custom header values.

## Runtime Validated HTTP Flow

HTTP Receiver is used instead of OData Receiver because the OData adapter attempted to parse the JSON deep insert payload as XML/Atom. CSRF token fetch, SAP session cookie handling, and HTTP POST to `/sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder` were runtime validated.

## Clean Core Alignment

Scripts do not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, direct custom S/4 APIs, UI, or custom persistence. SAP business validation remains in SAP S/4HANA through `API_SALES_ORDER_SRV`.

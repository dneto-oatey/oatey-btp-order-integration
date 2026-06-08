# IFL_SO_ORCHESTRATION Exception Subprocess

## Purpose

Enterprise-grade exception subprocess specification for IFL_SO_ORCHESTRATION. The subprocess routes terminal failures to DLQ_SO_INBOUND using `GS_PrepareDlqPayload.groovy` as the single script responsible for final error classification and complete DLQ envelope construction.

No `GS_BuildOrchestrationErrorContext` script exists or should be created.

## Required Exception Flow

Exception Start -> CM_SetFailedContext -> GS_PrepareDlqPayload -> CM_SetDlqContext -> JMS Receiver DLQ_SO_INBOUND -> End

## Responsibilities

| Component | Responsibility |
| --- | --- |
| Exception Start | Catch validation, SAP, callback, and technical runtime failures |
| CM_SetFailedContext | Set initial error fields when available, such as processingStatus, failureTimestamp, errorCategory, errorCode, errorMessage, SAP error fields |
| GS_PrepareDlqPayload | Capture current CPI exception context, classify missing error fields, sanitize sensitive values, build complete DLQ envelope, preserve original payload inside DLQ body |
| CM_SetDlqContext | Set DLQ routing headers/properties only; do not rebuild DLQ body |
| JMS Receiver DLQ_SO_INBOUND | Publish final DLQ envelope to DLQ_SO_INBOUND |

## Enterprise Error Classification

`GS_PrepareDlqPayload` applies final classification using this order:

| Rule | Classification |
| --- | --- |
| Existing errorCategory is present | Preserve existing value |
| SAP response status is 400, 409, or 422 | SAP_BUSINESS_ERROR |
| SAP response status is 401 or 403 | SAP_AUTH_CONFIG_ERROR |
| SAP response status is 408, 429, 500, 502, 503, or 504 | SAP_TRANSIENT_ERROR |
| Current exception indicates timeout or connection failure | SAP_TRANSIENT_ERROR |
| validationStatus is FAILED or errorCode indicates invalid JSON / missing metadata | VALIDATION_ERROR |
| No rule matches | TECHNICAL_ERROR |

## DLQ Envelope

The DLQ message body is a JSON envelope built by `GS_PrepareDlqPayload` and must include sourceIFlow, sourceQueue, targetQueue, correlationId, consumerId, idempotencyKey, processingStatus, failureTimestamp, errorCategory, errorCode, errorMessage, sapResponseStatusCode, sapErrorCode, sapErrorMessage, retryAttempt, maxRetryCount, replayRequired, replayInstruction, and originalPayload.

`replayInstruction` must be: Reprocess only through IFL_SO_ORCHESTRATION after root cause validation and idempotency review.

## Idempotency Rule

Missing idempotencyKey alone is not a DLQ reason. If another failure routes the message to DLQ and idempotencyKey is empty, the DLQ envelope must include `idempotencyKey` as an empty string and the replayInstruction must require idempotency review.

## Security And Logging

Payload is allowed inside the DLQ envelope for replay support, but payload must not be logged to MPL. Credentials, Authorization headers, bearer tokens, passwords, and secrets must never be included in error fields or monitoring logs.

## Clean Core Alignment

The subprocess does not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, direct custom S/4 APIs, or custom persistence. SAP business validation remains in SAP S/4HANA through API_SALES_ORDER_SRV.

# IFL_SO_ORCHESTRATION Exception Subprocess

## Purpose

Complete exception subprocess specification for IFL_SO_ORCHESTRATION. The subprocess classifies errors, determines retry behavior, prepares FAILED callbacks when appropriate, and routes terminal messages to DLQ_SO_INBOUND.

## Exception Flow

Exception Start -> CM_SetFailedContext -> Router by errorCategory -> Retry / Callback / DLQ -> GS_PrepareDlqPayload -> CM_SetDlqContext -> Send to DLQ_SO_INBOUND -> End

## Category Matrix

| Category | Retry? | Callback? | DLQ? | Monitoring fields |
| --- | --- | --- | --- | --- |
| VALIDATION_ERROR | No | Only when consumer context exists | Yes | correlationId, consumerId, errorCode, errorMessage |
| SAP_BUSINESS_ERROR | No blind retry | Yes, FAILED | Optional by operations policy | correlationId, consumerId, sapErrorCode, sapErrorMessage |
| SAP_TRANSIENT_ERROR | Yes, until maxRetryCount | FAILED after exhaustion | Yes after exhaustion | correlationId, retryAttempt, errorCode, sapResponseStatusCode |
| SAP_AUTH_CONFIG_ERROR | Limited | FAILED after terminal decision | Yes after exhaustion/terminal | correlationId, errorCode, sapResponseStatusCode |
| CALLBACK_ERROR | Yes, callback retry policy | No recursive callback | Yes after exhaustion | correlationId, callbackStatus, errorCode |
| TECHNICAL_ERROR | Conditional | FAILED if terminal and context exists | Yes if terminal | correlationId, errorCategory, errorCode, retryAttempt |

## VALIDATION_ERROR

| Rule | Behavior |
| --- | --- |
| Retry? | No |
| Callback? | Only if correlationId and consumerId are available |
| DLQ? | Yes |
| Examples | Missing body, invalid JSON, missing correlationId, missing consumerId, unusable structure |
| Monitoring fields | correlationId, consumerId, errorCategory, errorCode, errorMessage, failureTimestamp |

Missing idempotencyKey is not a VALIDATION_ERROR in the POC. It is allowed, monitored as warning, and not routed to DLQ.

## SAP_BUSINESS_ERROR

| Rule | Behavior |
| --- | --- |
| Retry? | No blind retry |
| Callback? | Yes, FAILED |
| DLQ? | Optional by operations policy |
| Examples | SAP 400, 409, 422 with business error payload |
| Monitoring fields | correlationId, consumerId, sapErrorCode, sapErrorMessage, processingStatus |

SAP business validation belongs to SAP S/4HANA through API_SALES_ORDER_SRV.

## SAP_TRANSIENT_ERROR

| Rule | Behavior |
| --- | --- |
| Retry? | Yes |
| Callback? | FAILED after retry exhaustion |
| DLQ? | Yes after retry exhaustion |
| Examples | 408, 429, 500, 502, 503, 504, timeout, network failure |
| Monitoring fields | correlationId, retryAttempt, maxRetryCount, errorCode, sapResponseStatusCode |

## SAP_AUTH_CONFIG_ERROR

| Rule | Behavior |
| --- | --- |
| Retry? | Limited; only when policy treats it as transient |
| Callback? | FAILED after terminal decision |
| DLQ? | Yes after exhaustion or terminal configuration failure |
| Examples | 401, 403, credential alias missing, destination issue |
| Monitoring fields | correlationId, sapResponseStatusCode, errorCode, credential alias reference |

Do not log credentials, tokens, passwords, or authorization headers.

## CALLBACK_ERROR

| Rule | Behavior |
| --- | --- |
| Retry? | Yes, according to callback retry policy |
| Callback? | No recursive callback |
| DLQ? | Yes after callback retry exhaustion |
| Examples | Callback endpoint 500, timeout, authentication failure |
| Monitoring fields | correlationId, callbackStatus, callbackEndpointAlias, errorCode |

## TECHNICAL_ERROR

| Rule | Behavior |
| --- | --- |
| Retry? | Conditional based on safe retry classification |
| Callback? | FAILED if terminal and context exists |
| DLQ? | Yes when terminal |
| Examples | Script failure, malformed SAP success response, adapter runtime error |
| Monitoring fields | correlationId, errorCategory, errorCode, retryAttempt, failureTimestamp |

## Idempotency POC Rule

| Condition | Behavior |
| --- | --- |
| idempotencyKey present | Preserve and propagate as metadata |
| idempotencyKey missing | Allowed in POC |
| Monitoring | Log warning only |
| DLQ | No DLQ routing for missing idempotencyKey alone |
| Future production | May become mandatory after approved idempotency persistence is introduced |

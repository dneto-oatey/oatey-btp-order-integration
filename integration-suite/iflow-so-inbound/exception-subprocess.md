# IFL_SO_INBOUND Exception Subprocess Behavior

## Scope

Build-ready exception subprocess specification for IFL_SO_INBOUND. Validation failures must not publish to JMS. JMS publish failure must return HTTP 500. The exception subprocess must not call S/4HANA and must not persist payloads outside JMS.

## Exception Entry Steps

| Step | Build action |
| --- | --- |
| 1 | Capture correlationId when available |
| 2 | Generate correlationId for error trace if missing |
| 3 | Classify error as VALIDATION or TECHNICAL |
| 4 | Set errorCode, errorMessage, validationStatus, and SAP_MessageProcessingLogCustomStatus |
| 5 | Build JSON error body |
| 6 | Set HTTP status and Content-Type application/json |
| 7 | Stop route without JMS publish for pre-JMS failures |

## Validation Error Matrix

| Failure | HTTP status | errorCode | JMS publish |
| --- | --- | --- | --- |
| Missing Idempotency-Key | 400 | MISSING_IDEMPOTENCY_KEY | No |
| Missing X-Consumer-ID | 400 | MISSING_CONSUMER_ID | No |
| Blank supplied X-Correlation-ID | 400 | INVALID_CORRELATION_ID | No |
| Missing or invalid Content-Type | 400 | INVALID_CONTENT_TYPE | No |
| Malformed JSON | 400 | INVALID_JSON | No |
| Missing SoldToParty | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing Material | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing RequestedQuantity | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing to_PricingElement.results | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Any sales-order-request.json schema failure | 422 | PAYLOAD_VALIDATION_FAILED | No |

Validation response fields:

| Field | Value |
| --- | --- |
| status | REJECTED |
| correlationId | correlationId |
| errorCode | errorCode |
| message | errorMessage |

## Technical Error Matrix

| Failure | HTTP status | errorCode | JMS publish state |
| --- | --- | --- | --- |
| JMS Receiver publish failure | 500 | JMS_PUBLISH_FAILED | Failed |
| Groovy runtime failure before JMS | 500 | SCRIPT_RUNTIME_ERROR | No publish |
| Unexpected iFlow runtime error | 500 | TECHNICAL_ERROR | Unknown unless adapter confirms |

Technical response fields:

| Field | Value |
| --- | --- |
| status | FAILED |
| correlationId | correlationId |
| errorCode | errorCode |
| message | errorMessage |

## JMS Publish Failure Rule

HTTP 202 is allowed only after JMS Receiver confirms successful publish to JMS_SO_INBOUND. If JMS publish fails, the iFlow returns HTTP 500 with errorCode JMS_PUBLISH_FAILED and status FAILED.

## Monitoring Fields On Error

| MPL field | Value |
| --- | --- |
| correlationId | correlationId |
| idempotencyKey | idempotencyKey when available |
| consumerId | consumerId when available |
| validationStatus | REJECTED or FAILED |
| errorCategory | VALIDATION or TECHNICAL |
| errorCode | Specific error code |
| errorMessage | Support-friendly message |
| jmsQueueName | JMS_SO_INBOUND |

## Payload Handling On Error

| Case | Payload behavior |
| --- | --- |
| Header validation failure | Do not publish to JMS |
| Malformed JSON | Do not publish to JMS |
| Schema validation failure | Do not publish to JMS |
| Technical failure before JMS | Do not persist payload outside JMS |
| JMS publish failure | Do not return ACK; return HTTP 500 |

## Guardrails

| Rule | Required behavior |
| --- | --- |
| No S/4HANA call | Exception subprocess never invokes SAP Sales Order API |
| No persistence | No database, CAP, file, Event Mesh, or custom store writes |
| No canonical model | Do not transform failed body to custom format except response body |
| No false ACK | Never return 202 from exception subprocess |

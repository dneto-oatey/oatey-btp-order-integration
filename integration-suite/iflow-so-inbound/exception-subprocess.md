# IFL_SO_INBOUND Exception Subprocess Behavior

## Scope

Build-ready exception subprocess specification for IFL_SO_INBOUND after the Groovy implementation findings. Validation failures must not send to JMS. JMS send failure must return HTTP 500. The exception subprocess must not call S/4HANA and must not persist payloads outside JMS.

## Exception Entry Steps

| Step | Build action |
| --- | --- |
| 1 | Run GS_BuildErrorContext.groovy |
| 2 | Generate correlationId if it is still missing |
| 3 | Classify error as VALIDATION or TECHNICAL |
| 4 | Set errorCode, errorCategory, errorMessage, httpStatus, and validationStatus |
| 5 | Set Content-Type application/json |
| 6 | Set CamelHttpResponseCode from httpStatus |
| 7 | Build JSON response body |
| 8 | End route without HTTP 202 |

## Validation Error Matrix

| Failure | HTTP status | errorCode | JMS send |
| --- | --- | --- | --- |
| Missing or invalid Content-Type | 400 | INVALID_CONTENT_TYPE | No |
| Malformed JSON | 400 | INVALID_JSON | No |
| Missing SoldToParty | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing Material | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing RequestedQuantity | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing to_PricingElement.results | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing required SAP Sales Order field detected by Groovy | 422 | PAYLOAD_VALIDATION_FAILED | No |

Idempotency-Key is optional for the POC and does not cause a validation error when missing. X-Consumer-ID is optional for the POC and defaults to UNKNOWN_CONSUMER when missing. X-Correlation-ID is optional and defaults to a generated UUID when missing.

## Technical Error Matrix

| Failure | HTTP status | errorCode | JMS send state |
| --- | --- | --- | --- |
| JMS Receiver send failure | 500 | JMS_PUBLISH_FAILED | Failed |
| Groovy runtime failure before JMS | 500 | TECHNICAL_ERROR | No send |
| Unexpected iFlow runtime error | 500 | TECHNICAL_ERROR | Unknown unless adapter confirms |

## Response Bodies

Validation response: status REJECTED, correlationId, errorCode, and message.

Technical response: status FAILED, correlationId, errorCode, and message.

## JMS Send Failure Rule

HTTP 202 is allowed only after the JMS Receiver confirms successful send to JMS_SO_INBOUND. If the JMS send fails, the exception subprocess returns HTTP 500 with errorCode JMS_PUBLISH_FAILED.

## Monitoring Fields On Error

Track correlationId, idempotencyKey when available, consumerId or UNKNOWN_CONSUMER, validationStatus, errorCategory, errorCode, errorMessage, and jmsQueueName JMS_SO_INBOUND.

## Guardrails

No S/4HANA call, no payload persistence, no Event Mesh, no CAP, no PostgreSQL, no UI, no RFC, no BAPI, no custom Z service, no canonical model, and no false ACK.

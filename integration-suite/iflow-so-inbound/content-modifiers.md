# IFL_SO_INBOUND Content Modifier Definitions

## Scope

Build-only SAP Integration Suite specification. Source of truth is integration-suite/iflow-so-inbound/design.md. The iFlow preserves the original SAP Sales Order JSON and publishes it to JMS_SO_INBOUND only after header and schema validation pass.

## Modifier Sequence

| Order | Name | Runs when | Body behavior |
| --- | --- | --- | --- |
| 1 | CM_SetInitialProperties | Immediately after HTTPS Sender | Preserve original body |
| 2 | CM_SetHeaderValidationContext | Before header Groovy validation | Preserve original body |
| 3 | CM_SetGeneratedCorrelationId | After valid headers | Preserve original body |
| 4 | CM_SetPayloadValidationStatus | After JSON schema validation | Preserve original body |
| 5 | CM_SetJmsHeaders | Before JMS Receiver | Preserve original body |
| 6 | CM_SetAckResponse | Only after JMS publish success | Replace body with ACK JSON |
| 7 | CM_SetValidationErrorResponse | Validation exception path | Replace body with error JSON |
| 8 | CM_SetTechnicalErrorResponse | Technical exception path | Replace body with error JSON |

## CM_SetInitialProperties

| Field | Type | Value |
| --- | --- | --- |
| inboundReceivedAt | Exchange Property | Current UTC timestamp |
| jmsQueueName | Exchange Property | JMS_SO_INBOUND |
| rawContentType | Exchange Property | Header Content-Type |
| validationStatus | Exchange Property | PENDING |
| payloadPreservationMode | Exchange Property | ORIGINAL_SAP_JSON |

Implementation note: do not alter the message body.

## CM_SetHeaderValidationContext

| Field | Type | Value |
| --- | --- | --- |
| receivedCorrelationHeader | Exchange Property | Header X-Correlation-ID |
| idempotencyKey | Exchange Property | Header Idempotency-Key |
| consumerId | Exchange Property | Header X-Consumer-ID |
| expectedContentType | Exchange Property | application/json |

Idempotency-Key must come from the HTTP header only. Do not inspect body idempotency fields as authoritative.

## CM_SetGeneratedCorrelationId

| Field | Type | Value |
| --- | --- | --- |
| correlationId | Exchange Property | X-Correlation-ID when present, otherwise generated UUID |
| X-Correlation-ID | Message Header | correlationId |

If X-Correlation-ID is missing, generate UUID before validation response, JMS publish, or monitoring fields are written.

## CM_SetPayloadValidationStatus

| Field | Type | Value |
| --- | --- | --- |
| validationStatus | Exchange Property | SUCCESS |
| SAP_MessageProcessingLogCustomStatus | Message Header | SUCCESS |

Set only after JSON Schema Validation and monitoring extraction succeed.

## CM_SetJmsHeaders

| JMS property | Source |
| --- | --- |
| correlationId | Exchange property correlationId |
| idempotencyKey | Exchange property idempotencyKey |
| consumerId | Exchange property consumerId |
| purchaseOrderByCustomer | Exchange property purchaseOrderByCustomer |
| soldToParty | Exchange property soldToParty |
| itemCount | Exchange property itemCount |
| inboundReceivedAt | Exchange property inboundReceivedAt |
| salesOrderType | Exchange property salesOrderType |
| salesOrganization | Exchange property salesOrganization |
| distributionChannel | Exchange property distributionChannel |
| incotermsClassification | Exchange property incotermsClassification when present |

The JMS body must remain the original SAP Sales Order JSON.

## CM_SetAckResponse

Execution rule: run only after JMS Receiver confirms successful publish.

| Response element | Value |
| --- | --- |
| HTTP status | 202 |
| Content-Type | application/json |
| X-Correlation-ID | correlationId |
| status | ACCEPTED |
| correlationId | correlationId |
| idempotencyKey | idempotencyKey |
| message | Sales order accepted for asynchronous processing |

## CM_SetValidationErrorResponse

| Failure class | HTTP status | JMS publish |
| --- | --- | --- |
| Header validation | 400 | No |
| Invalid Content-Type | 400 | No |
| Malformed JSON | 400 | No |
| JSON schema failure | 422 | No |

Response fields: status REJECTED, correlationId, errorCode, message.

## CM_SetTechnicalErrorResponse

| Failure class | HTTP status | JMS publish state |
| --- | --- | --- |
| JMS publish failure | 500 | Failed |
| Unexpected runtime failure | 500 | Unknown |

Response fields: status FAILED, correlationId, errorCode, message. Never return HTTP 202 when JMS publish fails.

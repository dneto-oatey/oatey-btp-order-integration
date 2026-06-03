# IFL_SO_INBOUND Content Modifier Matrix

## Scope

Build-ready SAP Integration Suite Content Modifier specification for IFL_SO_INBOUND. The executable flow uses Groovy-based payload validation and a one-way Send to JMS Receiver. There is no JSON Schema Validation step.

Supported Source Types: Constant, Header, Property, Expression, XPath, JSONPath.

## Modifier Sequence

| Order | Content Modifier | CPI tab used | Runs when | Body behavior |
| --- | --- | --- | --- | --- |
| 1 | CM_SetInitialProperties | Exchange Property | Immediately after HTTPS Sender | Preserve original SAP JSON body |
| 2 | CM_SetHeaderValidationContext | Exchange Property | Before GS_ValidateHeaders.groovy | Preserve original SAP JSON body |
| 3 | CM_SetGeneratedCorrelationId | Message Header and Exchange Property | After GS_EnsureCorrelationId.groovy | Preserve original SAP JSON body |
| 4 | CM_SetPayloadValidationStatus | Exchange Property and Message Header | After GS_ExtractMonitoringFields.groovy succeeds | Preserve original SAP JSON body |
| 5 | CM_SetJmsHeaders | Message Header | Immediately before Send to JMS Receiver | Preserve original SAP JSON body |
| 6 | CM_SetAckResponse | Message Body and Message Header | Only after successful JMS send | Replace body with ACK JSON |
| 7 | CM_SetValidationErrorResponse | Message Body and Message Header | Validation exception subprocess | Replace body with validation error JSON |
| 8 | CM_SetTechnicalErrorResponse | Message Body and Message Header | Technical exception subprocess | Replace body with technical error JSON |

## CM_SetInitialProperties

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Exchange Property | inboundReceivedAt | Expression | date now UTC yyyy-MM-dd'T'HH:mm:ss'Z' |
| Create | Exchange Property | jmsQueueName | Constant | JMS_SO_INBOUND |
| Create | Exchange Property | rawContentType | Header | Content-Type |
| Create | Exchange Property | validationStatus | Constant | PENDING |
| Create | Exchange Property | payloadPreservationMode | Constant | ORIGINAL_SAP_JSON |

## CM_SetHeaderValidationContext

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Exchange Property | receivedCorrelationHeader | Header | X-Correlation-ID |
| Create | Exchange Property | idempotencyKey | Header | Idempotency-Key |
| Create | Exchange Property | consumerId | Header | X-Consumer-ID |
| Create | Exchange Property | expectedContentType | Constant | application/json |

Idempotency-Key is copied only from the HTTP header. Do not read idempotency from the body.

## CM_SetGeneratedCorrelationId

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Exchange Property | correlationId | Property | correlationId |
| Create | Message Header | X-Correlation-ID | Property | correlationId |

## CM_SetPayloadValidationStatus

Run only after GS_ExtractMonitoringFields.groovy completes successfully.

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Update | Exchange Property | validationStatus | Constant | SUCCESS |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | SUCCESS |

## CM_SetJmsHeaders

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Message Header | X-Correlation-ID | Property | correlationId |
| Create | Message Header | Idempotency-Key | Property | idempotencyKey |
| Create | Message Header | X-Consumer-ID | Property | consumerId |
| Create | Message Header | correlationId | Property | correlationId |
| Create | Message Header | idempotencyKey | Property | idempotencyKey |
| Create | Message Header | consumerId | Property | consumerId |
| Create | Message Header | purchaseOrderByCustomer | Property | purchaseOrderByCustomer |
| Create | Message Header | soldToParty | Property | soldToParty |
| Create | Message Header | itemCount | Property | itemCount |
| Create | Message Header | inboundReceivedAt | Property | inboundReceivedAt |
| Create | Message Header | salesOrderType | Property | salesOrderType |
| Create 

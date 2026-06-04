# IFL_SO_INBOUND Content Modifier Matrix

## Scope

Build-ready Content Modifier specification for IFL_SO_INBOUND. The executable flow uses Groovy-based validation and one-way Send to JMS Receiver. There is no JSON Schema Validation step.

## Field Conventions

Unless explicitly stated otherwise, all Headers, Exchange Properties, JMS Properties, and MPL custom fields use Data Type java.lang.String.

Every Content Modifier field is documented with Action, Type, Name, Source Type, Source Value, and Data Type.

## Current Executable Main Flow

Start / HTTPS Sender -> CM_SetInitialProperties -> CM_SetHeaderValidationContext -> GS_ValidateHeaders -> GS_EnsureCorrelationId -> GS_ExtractMonitoringFields -> CM_SetPayloadValidationStatus -> GS_PrepareJmsMessage -> CM_SetJmsHeaders -> Send to JMS Receiver -> CM_SetAckResponse -> End

## CM_SetInitialProperties

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | inboundReceivedAt | Expression | current UTC timestamp | java.lang.String |
| Create | Exchange Property | jmsQueueName | Constant | JMS_SO_INBOUND | java.lang.String |
| Create | Exchange Property | rawContentType | Header | Content-Type | java.lang.String |
| Create | Exchange Property | validationStatus | Constant | PENDING | java.lang.String |
| Create | Exchange Property | payloadPreservationMode | Constant | ORIGINAL_SAP_JSON | java.lang.String |

## CM_SetHeaderValidationContext

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | receivedCorrelationHeader | Header | X-Correlation-ID | java.lang.String |
| Create | Exchange Property | idempotencyKey | Header | Idempotency-Key | java.lang.String |
| Create | Exchange Property | consumerId | Header | X-Consumer-ID | java.lang.String |
| Create | Exchange Property | expectedContentType | Constant | application/json | java.lang.String |

## CM_SetPayloadValidationStatus

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Update | Exchange Property | validationStatus | Constant | SUCCESS | java.lang.String |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | SUCCESS | java.lang.String |

## CM_SetJmsHeaders

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | X-Correlation-ID | Property | correlationId | java.lang.String |
| Create | Message Header | Idempotency-Key | Property | idempotencyKey | java.lang.String |
| Create | Message Header | X-Consumer-ID | Property | consumerId | java.lang.String |
| Create | Message Header | correlationId | Property | correlationId | java.lang.String |
| Create | Message Header | idempotencyKey | Property | idempotencyKey | java.lang.String |
| Create | Message Header | consumerId | Property | consumerId | java.lang.String |
| Create | Message Header | purchaseOrderByCustomer | Property | purchaseOrderByCustomer | java.lang.String |
| Create | Message Header | soldToParty | Property | soldToParty | java.lang.String |
| Create | Message Header | itemCount | Property | itemCount | java.lang.String |
| Create | Message Header | inboundReceivedAt | Property | inboundReceivedAt | java.lang.String |

Transfer Exchange Properties must be enabled on the JMS Receiver so these values are available to IFL_SO_ORCHESTRATION.

## CM_SetAckResponse

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | Content-Type | Constant | application/json | java.lang.String |
| Create | Message Header | X-Correlation-ID | Property | correlationId | java.lang.String |
| Create | Message Header | CamelHttpResponseCode | Constant | 202 | java.lang.String |
| Update | Message Body | Body | Expression | JSON ACK with status ACCEPTED and correlationId | java.lang.String |

## CM_SetErrorResponse

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | Content-Type | Constant | application/json | java.lang.String |
| Create | Message Header | X-Correlation-ID | Property | correlationId | java.lang.String |
| Create | Message Header | CamelHttpResponseCode | Property | httpStatus | java.lang.String |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Property | validationStatus | java.lang.String |
| Update | Message Body | Body | Expression | JSON error response using errorCode and errorMessage | java.lang.String |

## JMS And Validation Notes

Do not use JMS Request Reply. Use one-way Send to JMS Receiver with Queue JMS_SO_INBOUND, Access Type Non-Exclusive, Retention 7 days, Transfer Exchange Properties Enabled, Compression enabled if configured in tenant, and Encryption enabled if configured in tenant.

EDI Validator and XML Validator are not valid substitutes for JSON payload validation. Payload validation is implemented through Groovy logic.

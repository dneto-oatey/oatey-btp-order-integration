# IFL_SO_INBOUND Content Modifier Matrix

## Scope

Build-ready Content Modifier specification for `IFL_SO_INBOUND`. The executable flow uses Groovy-based validation and one-way Send to JMS Receiver. There is no JSON Schema Validation step.

SAP Integration Suite Content Modifier actions must use `Create` or `Delete`. This document uses `Create` only.

## Current Executable Main Flow

```text
Start / HTTPS Sender
-> CM_SetInitialProperties
-> GS_ValidateHeaders
-> GS_EnsureCorrelationId
-> GS_ExtractMonitoringFields
-> CM_SetPayloadValidationStatus
-> GS_PrepareJmsMessage
-> CM_SetJmsHeaders
-> Send to JMS Receiver
-> CM_SetAckResponse
-> End
```

`CM_SetHeaderValidationContext` is not part of the executable flow.

## Header Normalization

Capture `Content-Type`, `X-Correlation-ID`, `X-Consumer-ID`, and `Idempotency-Key` immediately after HTTPS Sender and store them as Exchange Properties. Processing should use Exchange Properties rather than direct HTTP header access.

## CM_SetInitialProperties

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | inboundReceivedAt | Expression | current UTC timestamp | java.lang.String |
| Create | Exchange Property | rawContentType | Header | Content-Type | java.lang.String |
| Create | Exchange Property | receivedCorrelationHeader | Header | X-Correlation-ID | java.lang.String |
| Create | Exchange Property | consumerId | Header | X-Consumer-ID | java.lang.String |
| Create | Exchange Property | idempotencyKey | Header | Idempotency-Key | java.lang.String |
| Create | Exchange Property | expectedContentType | Constant | application/json | java.lang.String |
| Create | Exchange Property | jmsQueueName | Constant | JMS_SO_INBOUND | java.lang.String |
| Create | Exchange Property | validationStatus | Constant | PENDING | java.lang.String |
| Create | Exchange Property | payloadPreservationMode | Constant | ORIGINAL_SAP_JSON | java.lang.String |

## CM_SetPayloadValidationStatus

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | validationStatus | Constant | SUCCESS | java.lang.String |
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

## CM_SetAckResponse

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | Content-Type | Constant | application/json | java.lang.String |
| Create | Message Header | X-Correlation-ID | Property | correlationId | java.lang.String |
| Create | Message Header | CamelHttpResponseCode | Constant | 202 | java.lang.String |
| Create | Message Body | Body | Expression | JSON ACK with status ACCEPTED and correlationId | java.lang.String |

## CM_SetErrorResponse

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | Content-Type | Constant | application/json | java.lang.String |
| Create | Message Header | X-Correlation-ID | Property | correlationId | java.lang.String |
| Create | Message Header | CamelHttpResponseCode | Property | httpStatus | java.lang.String |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Property | validationStatus | java.lang.String |
| Create | Message Body | Body | Expression | JSON error response using errorCode and errorMessage | java.lang.String |

## MPL Custom Headers

Inbound custom headers:

- `ConsumerID`
- `correlationId`
- `IdempotencyKey`
- `validationStatus`

`GS_LogBeforeJms` or `GS_SetMplCustomHeaders`, when present, must only add custom header properties when values exist. Empty values must not be written because MPL appends values and does not overwrite prior custom header values.

## JMS And Validation Notes

Do not use JMS Request Reply. Use one-way Send to JMS Receiver with Queue `JMS_SO_INBOUND`, Access Type Non-Exclusive, Expiration Period 30 Days, Retention Alert Threshold 2 Days, Transfer Exchange Properties Enabled, Compress Stored Messages Enabled, and Encrypt Stored Messages Enabled.

EDI Validator and XML Validator are not valid substitutes for JSON payload validation. Payload validation is implemented through Groovy logic.

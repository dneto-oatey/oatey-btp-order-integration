# IFL_SO_ORCHESTRATION Content Modifier Matrix

## Purpose

Build-ready Content Modifier matrix for SAP Integration Suite. Source Types use CPI terminology: Constant, Header, Property, Expression, XPath, JSONPath.

No `GS_BuildOrchestrationErrorContext` script exists. CM_SetFailedContext may set initial error fields, but `GS_PrepareDlqPayload` is responsible for final error classification and DLQ envelope completeness.

## CM_ReadJmsMetadata

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | correlationId | Header | correlationId | java.lang.String |
| Create | Exchange Property | idempotencyKey | Header | idempotencyKey | java.lang.String |
| Create | Exchange Property | consumerId | Header | consumerId | java.lang.String |
| Create | Exchange Property | sourceQueueName | Constant | JMS_SO_INBOUND | java.lang.String |
| Create | Exchange Property | dlqQueueName | Constant | DLQ_SO_INBOUND | java.lang.String |
| Create | Exchange Property | orchestrationReceivedAt | Expression | current UTC timestamp | java.lang.String |
| Create | Exchange Property | processingStatus | Constant | RECEIVED | java.lang.String |
| Create | Exchange Property | idempotencyPocPolicy | Constant | OPTIONAL_WARN_ONLY | java.lang.String |
| Create | Exchange Property | idempotencyWarning | Expression | true when idempotencyKey is blank | java.lang.Boolean |

## CM_PrepareSapRequest

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | Content-Type | Constant | application/json | java.lang.String |
| Create | Message Header | Accept | Constant | application/json | java.lang.String |
| Create | Message Header | X-Correlation-ID | Property | correlationId | java.lang.String |
| Create | Message Header | Idempotency-Key | Property | idempotencyKey | java.lang.String |
| Create | Exchange Property | sapApiName | Constant | API_SALES_ORDER_SRV | java.lang.String |
| Create | Exchange Property | sapApiOperation | Constant | CREATE_SALES_ORDER | java.lang.String |
| Create | Exchange Property | sapEndpoint | Property | externalized SAP endpoint | java.lang.String |
| Create | Exchange Property | sapCredentialAlias | Property | security material or destination alias | java.lang.String |

## CM_SetSuccessContext

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Update | Exchange Property | processingStatus | Constant | SUCCESS | java.lang.String |
| Create | Exchange Property | sapSalesOrderNumber | Property | sapSalesOrderNumber | java.lang.String |
| Create | Exchange Property | callbackStatus | Constant | PENDING | java.lang.String |
| Create | Exchange Property | completedAt | Expression | current UTC timestamp | java.lang.String |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | SUCCESS | java.lang.String |

## CM_SetFailedContext

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Update | Exchange Property | processingStatus | Constant | FAILED | java.lang.String |
| Create | Exchange Property | failureTimestamp | Expression | current UTC timestamp | java.lang.String |
| Create | Exchange Property | errorCategory | Property | errorCategory, if available | java.lang.String |
| Create | Exchange Property | errorCode | Property | errorCode, if available | java.lang.String |
| Create | Exchange Property | errorMessage | Property | errorMessage, if available | java.lang.String |
| Create | Exchange Property | sapResponseStatusCode | Property | sapResponseStatusCode, if available | java.lang.String |
| Create | Exchange Property | sapErrorCode | Property | sapErrorCode, if available | java.lang.String |
| Create | Exchange Property | sapErrorMessage | Property | sapErrorMessage, if available | java.lang.String |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | FAILED | java.lang.String |

## CM_SetDlqContext

CM_SetDlqContext runs after `GS_PrepareDlqPayload`. It must not rebuild or overwrite the DLQ JSON envelope body.

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | correlationId | Property | correlationId | java.lang.String |
| Create | Message Header | idempotencyKey | Property | idempotencyKey | java.lang.String |
| Create | Message Header | consumerId | Property | consumerId | java.lang.String |
| Create | Message Header | errorCategory | Property | errorCategory | java.lang.String |
| Create | Message Header | errorCode | Property | errorCode | java.lang.String |
| Create | Message Header | sapResponseStatusCode | Property | sapResponseStatusCode | java.lang.String |
| Create | Message Header | failureTimestamp | Property | failureTimestamp | java.lang.String |
| Create | Message Header | dlqQueueName | Property | dlqQueueName | java.lang.String |
| Update | Exchange Property | processingStatus | Constant | DLQ_ROUTED | java.lang.String |

## Idempotency Alignment

Missing idempotencyKey must not trigger DLQ routing by itself. If another failure routes the message to DLQ and idempotencyKey is empty, `GS_PrepareDlqPayload` includes `idempotencyKey` as an empty string and replayInstruction requires idempotency review.

## Logging

Payload is allowed inside the DLQ envelope body only. Do not write originalPayload to MPL custom properties or logs.

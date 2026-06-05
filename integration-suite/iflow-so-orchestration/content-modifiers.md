# IFL_SO_ORCHESTRATION Content Modifier Matrix

## Purpose

Build-ready Content Modifier matrix for SAP Integration Suite. Source Types use CPI terminology: Constant, Header, Property, Expression, XPath, JSONPath.

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
| Create | Exchange Property | errorCategory | Property | errorCategory | java.lang.String |
| Create | Exchange Property | errorCode | Property | errorCode | java.lang.String |
| Create | Exchange Property | errorMessage | Property | errorMessage | java.lang.String |
| Create | Exchange Property | sapErrorCode | Property | sapErrorCode | java.lang.String |
| Create | Exchange Property | sapErrorMessage | Property | sapErrorMessage | java.lang.String |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | FAILED | java.lang.String |

## CM_SetDlqContext

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | correlationId | Property | correlationId | java.lang.String |
| Create | Message Header | idempotencyKey | Property | idempotencyKey | java.lang.String |
| Create | Message Header | consumerId | Property | consumerId | java.lang.String |
| Create | Message Header | errorCategory | Property | errorCategory | java.lang.String |
| Create | Message Header | errorCode | Property | errorCode | java.lang.String |
| Create | Message Header | errorMessage | Property | errorMessage | java.lang.String |
| Create | Message Header | sapErrorCode | Property | sapErrorCode | java.lang.String |
| Create | Message Header | failureTimestamp | Property | failureTimestamp | java.lang.String |
| Create | Message Header | dlqQueueName | Property | dlqQueueName | java.lang.String |
| Update | Exchange Property | processingStatus | Constant | DLQ_ROUTED | java.lang.String |

## Idempotency Alignment

Missing idempotencyKey must not trigger CM_SetDlqContext in the POC. It sets `idempotencyWarning = true` for monitoring only. Future production may enforce mandatory idempotency after an approved durable idempotency mechanism is introduced.

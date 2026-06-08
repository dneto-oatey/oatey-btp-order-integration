# IFL_SO_ORCHESTRATION Content Modifier Matrix

## Purpose

Build-ready Content Modifier matrix for SAP Integration Suite. Source Types use CPI terminology: Constant, Header, Property, Expression, XPath, JSONPath.

Content Modifier actions use Create or Delete only. Do not use Update.

No `GS_BuildOrchestrationErrorContext` script exists. CM_SetFailedContext may set initial error fields, but `GS_PrepareDlqPayload` is responsible for final error classification and DLQ envelope completeness.

## CM_ReadJmsMetadata

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | correlationId | Header | correlationId | java.lang.String |
| Create | Exchange Property | idempotencyKey | Header | idempotencyKey | java.lang.String |
| Create | Exchange Property | consumerId | Header | consumerId | java.lang.String |
| Create | Exchange Property | sourceQueueName | Property | JMS_SOURCE_QUEUE | java.lang.String |
| Create | Exchange Property | dlqQueueName | Property | JMS_DLQ_QUEUE | java.lang.String |
| Create | Exchange Property | orchestrationReceivedAt | Expression | current UTC timestamp | java.lang.String |
| Create | Exchange Property | processingStatus | Constant | RECEIVED | java.lang.String |
| Create | Exchange Property | idempotencyPocPolicy | Property | IDEMPOTENCY_POLICY | java.lang.String |
| Create | Exchange Property | idempotencyWarning | Expression | true when idempotencyKey is blank | java.lang.Boolean |

## CM_PrepareCsrfFetch

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Header | x-csrf-token | Constant | Fetch | java.lang.String |
| Create | Message Header | Accept | Constant | application/json | java.lang.String |
| Create | Exchange Property | sapRequestPayload | Expression | ${body} | java.lang.String |
| Create | Exchange Property | sapBasePath | Property | SAP_BASE_PATH | java.lang.String |
| Create | Exchange Property | sapProxyType | Property | SAP_PROXY_TYPE | java.lang.String |
| Create | Exchange Property | sapLocationId | Property | SAP_LOCATION_ID | java.lang.String |
| Create | Exchange Property | sapCredentialName | Property | SAP_CREDENTIAL_NAME | java.lang.String |
| Create | Exchange Property | sapTimeoutMinutes | Property | SAP_TIMEOUT_MINUTES | java.lang.String |

## CM_PrepareSapPostRequest

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Body | Body | Property | sapRequestPayload | java.lang.String |
| Create | Message Header | x-csrf-token | Property | csrfToken | java.lang.String |
| Create | Message Header | X-CSRF-Token | Property | csrfToken | java.lang.String |
| Create | Message Header | Cookie | Property | sapCookie | java.lang.String |
| Create | Message Header | Content-Type | Constant | application/json | java.lang.String |
| Create | Message Header | Accept | Constant | application/json | java.lang.String |
| Create | Message Header | X-Correlation-ID | Property | correlationId | java.lang.String |
| Create | Message Header | Idempotency-Key | Property | idempotencyKey | java.lang.String |
| Create | Exchange Property | sapCreatePath | Property | SAP_CREATE_PATH | java.lang.String |

## CM_SetSuccessContext

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | processingStatus | Constant | SUCCESS | java.lang.String |
| Create | Exchange Property | sapSalesOrderNumber | Property | sapSalesOrderNumber | java.lang.String |
| Create | Exchange Property | callbackStatus | Constant | PENDING | java.lang.String |
| Create | Exchange Property | completedAt | Expression | current UTC timestamp | java.lang.String |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | SUCCESS | java.lang.String |

## CM_SetFailedContext

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | processingStatus | Constant | FAILED | java.lang.String |
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
| Create | Exchange Property | processingStatus | Constant | DLQ_ROUTED | java.lang.String |

## Externalized Parameters

SAP_BASE_PATH, SAP_CREATE_PATH, SAP_PROXY_TYPE, SAP_LOCATION_ID, SAP_CREDENTIAL_NAME, SAP_TIMEOUT_MINUTES, JMS_SOURCE_QUEUE, JMS_DLQ_QUEUE, and IDEMPOTENCY_POLICY are externalized.

## Runtime Values Not Externalized

correlationId, consumerId, idempotencyKey, csrfToken, sapCookie, sapRequestPayload, sapSalesOrderNumber, and errorMessage are runtime values and must not be externalized.

## Logging

Payload is allowed inside the DLQ envelope body only. Do not write originalPayload, csrfToken, sapCookie, Authorization headers, credentials, tokens, or passwords to MPL custom properties or logs.

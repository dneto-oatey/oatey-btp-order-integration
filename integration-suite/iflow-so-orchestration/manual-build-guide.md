# IFL_SO_ORCHESTRATION Manual Build Guide

## Purpose

Manual SAP Integration Suite build guide for IFL_SO_ORCHESTRATION. This document is implementation-ready and keeps the approved architecture unchanged.

## Runtime

JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> SAP Standard Sales Order API -> Callback -> DLQ_SO_INBOUND

## 1. Create iFlow

| Field | Value |
| --- | --- |
| Package | Oatey Sales Order Integration |
| iFlow | IFL_SO_ORCHESTRATION |
| Sender | JMS Sender |
| Receiver 1 | SAP Standard Sales Order API |
| Receiver 2 | Callback Receiver, optional |
| Receiver 3 | DLQ_SO_INBOUND |

## 2. Configure JMS Sender

| Setting | Value |
| --- | --- |
| Component | JMS Sender |
| Adapter type | JMS |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Message body | Original SAP Sales Order JSON |
| Expected metadata | correlationId, idempotencyKey, consumerId |
| Completion rule | Complete after SAP outcome and callback/DLQ decision |

## 3. Add CM_ReadJmsMetadata

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Exchange Property | correlationId | Header | correlationId | java.lang.String |
| Create | Exchange Property | idempotencyKey | Header | idempotencyKey | java.lang.String |
| Create | Exchange Property | consumerId | Header | consumerId | java.lang.String |
| Create | Exchange Property | sourceQueueName | Constant | JMS_SO_INBOUND | java.lang.String |
| Create | Exchange Property | dlqQueueName | Constant | DLQ_SO_INBOUND | java.lang.String |
| Create | Exchange Property | orchestrationReceivedAt | Expression | current UTC timestamp | java.lang.String |
| Create | Exchange Property | idempotencyPocPolicy | Constant | OPTIONAL_WARN_ONLY | java.lang.String |

## 4. Add GS_ValidateConsumedMessage

| Validation | Expected behavior |
| --- | --- |
| Body exists | Required |
| Body is valid JSON | Required |
| correlationId exists | Required by orchestration stage |
| consumerId exists | Required as value or `UNKNOWN_CONSUMER` fallback |
| idempotencyKey exists | Optional in POC; warning only when missing |
| Payload has usable SAP Sales Order structure | Required for SAP call |

Do not validate customer, material, pricing, partner determination, sales area, or SAP business rules.

## 5. Add GS_PrepareSapSalesOrderRequest

| Input | Output |
| --- | --- |
| Original SAP Sales Order JSON | Same JSON body preserved for API_SALES_ORDER_SRV |
| correlationId | SAP trace header context |
| idempotencyKey | Integration metadata only |
| consumerId | Callback and monitoring context |

## 6. Add CM_PrepareSapRequest

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

## 7. Add Request Reply And SAP Receiver

| Setting | Value |
| --- | --- |
| Component | Request Reply |
| Receiver adapter type | HTTP |
| Method | POST |
| Target API | API_SALES_ORDER_SRV |
| Authentication | Secure credential material or SAP destination |
| Credentials | Never hard-code |
| CSRF | Configure token handling if required by SAP endpoint |
| Timeout | Externalized and shorter than runtime processing timeout |

## 8. Add GS_HandleSapResponse

| SAP response | Classification | Behavior |
| --- | --- | --- |
| 200/201 with Sales Order number | SUCCESS | Continue success path |
| 200/201 without Sales Order number | TECHNICAL_ERROR | Retryable |
| 400/409/422 SAP business error | SAP_BUSINESS_ERROR | No blind retry |
| 401/403 | SAP_AUTH_CONFIG_ERROR | Limited retry, then DLQ |
| 408/429/500/502/503/504 | SAP_TRANSIENT_ERROR | Retryable |
| Timeout/network error | SAP_TRANSIENT_ERROR | Retryable |

## 9. Add CM_SetSuccessContext

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Update | Exchange Property | processingStatus | Constant | SUCCESS | java.lang.String |
| Create | Exchange Property | sapSalesOrderNumber | Property | sapSalesOrderNumber | java.lang.String |
| Create | Exchange Property | callbackStatus | Constant | PENDING | java.lang.String |
| Create | Exchange Property | completedAt | Expression | current UTC timestamp | java.lang.String |

## 10. Configure Callback Receiver

| Setting | Value |
| --- | --- |
| Adapter type | HTTP |
| Trigger | SAP terminal SUCCESS or terminal FAILED |
| Payload builder | GS_PrepareCallbackPayload |
| Required field | correlationId |
| Credentials | Secure material, externalized |
| Success status | 200, 201, or 202 |

## 11. Configure Exception Subprocess

| Component | Configuration |
| --- | --- |
| Exception Start | Catch validation, SAP, callback, and technical errors |
| CM_SetFailedContext | Set processingStatus, errorCategory, errorCode, errorMessage |
| Router | Retryable vs terminal |
| Callback path | Prepare FAILED callback when terminal and context exists |
| DLQ path | Prepare DLQ payload and send to DLQ_SO_INBOUND |

## 12. Configure DLQ Sender

| Setting | Value |
| --- | --- |
| Adapter type | JMS |
| Queue | DLQ_SO_INBOUND |
| Body | DLQ envelope with original payload and error context |
| Headers | correlationId, idempotencyKey, consumerId, errorCategory, errorCode |

## Logging

Success path logs metadata only: correlationId, consumerId, idempotencyKey presence, SAP order number, processingStatus. Do not log full payload on success.

# IFL_SO_INBOUND Manual Build Guide

## Purpose

Manual SAP Integration Suite build steps for IFL_SO_INBOUND after the implementation findings from the Groovy scripts. Runtime remains APIM to IFL_SO_INBOUND to JMS_SO_INBOUND to IFL_SO_ORCHESTRATION.

Do not add CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA receiver calls, custom canonical models, or payload persistence outside JMS.

## 1. Create Package And iFlow

| Step | Action | Value |
| --- | --- | --- |
| 1 | Open SAP Integration Suite | Design > Integrations and APIs |
| 2 | Create or open package | Oatey Sales Order Inbound Integration |
| 3 | Create Integration Flow | IFL_SO_INBOUND |
| 4 | Add Sender participant | APIM forwarded HTTPS request |
| 5 | Add Receiver participant | JMS_SO_INBOUND |

## 2. Configure HTTPS Sender

| Adapter setting | Value |
| --- | --- |
| Adapter type | HTTPS |
| Address | /sales-orders |
| HTTP method | POST |
| Request content type | application/json |
| Response content type | application/json |
| Authentication and authorization | Handled by APIM |

## 3. Add Content Modifiers

Place these Content Modifiers in sequence: CM_SetInitialProperties, CM_SetHeaderValidationContext, CM_SetGeneratedCorrelationId, CM_SetPayloadValidationStatus, CM_SetJmsHeaders, and CM_SetAckResponse.

Do not change the message body before Send to JMS Receiver. CM_SetAckResponse is the first step allowed to replace the body, and it runs only after successful JMS send.

## 4. Add Groovy Script Steps

| Order | Script step | Purpose |
| --- | --- | --- |
| 1 | GS_ValidateHeaders.groovy | Validate Content-Type and normalize optional headers |
| 2 | GS_EnsureCorrelationId.groovy | Reuse or generate correlationId |
| 3 | GS_ExtractMonitoringFields.groovy | Validate SAP Sales Order JSON and extract monitoring fields |
| 4 | GS_PrepareJmsMessage.groovy | Prepare JMS metadata while preserving the body |

There is no JSON Schema Validation step in the executable flow. Payload validation is Groovy-based.

## 5. Configure JMS Receiver

Add a one-way Send step to the JMS Receiver adapter after CM_SetJmsHeaders. Do not use JMS Request Reply.

| JMS setting | Value |
| --- | --- |
| Adapter type | JMS Receiver |
| Pattern | Send to JMS Receiver |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Retention | 7 days |
| Transfer Exchange Properties | Enabled |
| Message body | Original SAP Sales Order JSON |
| Delivery mode | Persistent |
| Error handling | Local exception subprocess returns HTTP 500 on JMS send failure |

## 6. Configure Exception Subprocess

Create one local Exception Subprocess for the main integration process.

| Step | Action |
| --- | --- |
| 1 | Run GS_BuildErrorContext.groovy |
| 2 | Set MPL fields correlationId, idempotencyKey, consumerId, validationStatus, errorCategory, errorCode, errorMessage |
| 3 | Set Content-Type application/json |
| 4 | Set CamelHttpResponseCode from httpStatus |
| 5 | Build response JSON with status, correlationId, errorCode, and message |
| 6 | End route without returning HTTP 202 |

## 7. Configure MPL Custom Properties

Track correlationId, idempotencyKey, consumerId, purchaseOrderByCustomer, soldToParty, itemCount, salesOrderType, salesOrganization, distributionChannel, validationStatus, jmsQueueName, inboundReceivedAt, errorCode, and errorCategory.

Payload logging remains disabled by default.

## 8. Deploy Checklist

| Check | Required result |
| --- | --- |
| HTTPS endpoint | /sales-orders configured |
| Groovy scripts | Added in correct sequence |
| JSON Schema Validation | Not present in executable flow |
| JMS Receiver | Send to JMS_SO_INBOUND, Non-Exclusive, 7 day retention, Transfer Exchange Properties enabled |
| ACK response | Occurs only after JMS send succeeds |
| Exception subprocess | Returns 400, 422, or 500 correctly |
| Excluded services | CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, and S/4HANA receiver absent |

## 9. Postman Test Sequence

| Test | Expected result |
| --- | --- |
| Happy Path | HTTP 202 and JMS message |
| Missing SoldToParty | HTTP 422 PAYLO

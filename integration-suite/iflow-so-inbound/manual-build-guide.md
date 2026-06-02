# IFL_SO_INBOUND Manual Build Guide

## Purpose

This guide describes the manual SAP Integration Suite build steps for IFL_SO_INBOUND. It is based on design.md, implementation-checklist.md, content-modifiers.md, groovy-scripts.md, exception-subprocess.md, and test-plan.md.

The iFlow runtime path remains APIM to IFL_SO_INBOUND to JMS_SO_INBOUND to IFL_SO_ORCHESTRATION. Do not add CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA receiver calls, custom canonical models, or payload persistence outside JMS.

## 1. Create Package And iFlow

| Step | Action | Value |
| --- | --- | --- |
| 1 | Open SAP Integration Suite | Design > Integrations and APIs |
| 2 | Create or open package | Oatey Sales Order Inbound Integration |
| 3 | Create Integration Flow | IFL_SO_INBOUND |
| 4 | Set version | Week 5 manual build draft |
| 5 | Add description | Receives SAP Sales Order JSON from APIM, validates, publishes to JMS_SO_INBOUND, and returns ACK |
| 6 | Save artifact | Package contains IFL_SO_INBOUND |

Required iFlow participants:

| Participant | Type | Purpose |
| --- | --- | --- |
| Sender | Sender participant | APIM forwarded HTTPS request |
| Integration Process | Main process | Header validation, schema validation, enrichment, JMS publish, ACK |
| Receiver | Receiver participant | JMS_SO_INBOUND |

## 2. Configure HTTPS Sender

Create an HTTPS Sender adapter from Sender to Start event.

| Adapter setting | Value |
| --- | --- |
| Adapter type | HTTPS |
| Address | /sales-orders |
| HTTP method | POST |
| Request content type | application/json |
| Response content type | application/json |
| Authentication | Handled by SAP API Management before forwarding |
| Authorization | Handled by SAP API Management before forwarding |
| Direct external exposure | No |

Required inbound headers:

| Header | Requirement |
| --- | --- |
| X-Correlation-ID | Optional; generate UUID when missing |
| Idempotency-Key | Mandatory; source of idempotency |
| X-Consumer-ID | Mandatory; APIM consumer identifier |
| Content-Type | Mandatory; must contain application/json |

## 3. Add Content Modifiers

Place Content Modifiers in this exact sequence.

| Order | Content Modifier | Location | Body behavior |
| --- | --- | --- | --- |
| 1 | CM_SetInitialProperties | Directly after Start event | Preserve original SAP JSON |
| 2 | CM_SetHeaderValidationContext | Before GS_ValidateHeaders.groovy | Preserve original SAP JSON |
| 3 | CM_SetPayloadValidationStatus | After JSON Schema Validation and monitoring extraction | Preserve original SAP JSON |
| 4 | CM_SetJmsHeaders | Immediately before JMS Receiver | Preserve original SAP JSON |
| 5 | CM_SetAckResponse | Immediately after successful JMS Receiver | Replace body with ACK JSON |

CM_SetInitialProperties fields:

| Property | Value |
| --- | --- |
| inboundReceivedAt | Current UTC timestamp |
| jmsQueueName | JMS_SO_INBOUND |
| rawContentType | Header Content-Type |
| validationStatus | PENDING |
| payloadPreservationMode | ORIGINAL_SAP_JSON |

CM_SetHeaderValidationContext fields:

| Property | Value |
| --- | --- |
| receivedCorrelationHeader | Header X-Correlation-ID |
| idempotencyKey | Header Idempotency-Key |
| consumerId | Header X-Consumer-ID |
| expectedContentType | application/json |

CM_SetJmsHeaders fields:

| JMS property | Source property |
| --- | --- |
| correlationId | correlationId |
| idempotencyKey | idempotencyKey |
| consumerId | consumerId |
| purchaseOrderByCustomer | purchaseOrderByCustomer |
| soldToParty | soldToParty |
| itemCount | itemCount |
| inboundReceivedAt | inboundReceivedAt |
| salesOrderType | salesOrderType |
| salesOrganization | salesOrganization |
| distributionChannel | distributionChannel |
| incotermsClassification | incotermsClassification when present |

## 4. Add Groovy Script Steps

Add Groovy Script steps in this exact order.

| Order | Script step | Purpose |
| --- | --- | --- |
| 1 | GS_ValidateHeaders.groovy | Validate Idempotency-Key, X-Consumer-ID, Content-Type, and supplied X-Correlation-ID |
| 2 | GS_EnsureCorrelationId.groovy | Preserve X-Correlation-ID or generate UUID |
| 3 | GS_ExtractMonitoringFields.groovy | Extract SAP order metadata after schema validation |
| 4 | GS_PrepareJmsMessage.groovy | Assert JMS metadata completeness before publish |

Header script rules:

| Rule | Failure response |
| --- | --- |
| Missing or blank Idempotency-Key | HTTP 400 MISSING_IDEMPOTENCY_KEY |
| Missing or blank X-Consumer-ID | HTTP 400 MISSING_CONSUMER_ID |
| Content-Type missing application/json | HTTP 400 INVALID_CONTENT_TYPE |
| Supplied X-Correlation-ID is blank | HTTP 400 INVALID_CORRELATION_ID |

Correlation script rules:

| Condition | Result |
| --- | --- |
| X-Correlation-ID present | Set correlationId to trimmed header value |
| X-Correlation-ID missing | Generate UUID and set correlationId |

Monitoring extraction fields:

| Property | JSON field |
| --- | --- |
| purchaseOrderByCustomer | PurchaseOrderByCustomer |
| soldToParty | SoldToParty |
| itemCount | Count of to_Item.results |
| salesOrderType | SalesOrderType |
| salesOrganization | SalesOrganization |
| distributionChannel | DistributionChannel |
| incotermsClassification | IncotermsClassification when present |

## 5. Add JSON Schema Validation

Add a JSON Schema Validation step after GS_EnsureCorrelationId.groovy and before GS_ExtractMonitoringFields.groovy.

| Setting | Value |
| --- | --- |
| Schema source | openapi/schemas/sales-order-request.json |
| Payload format | SAP Sales Order API JSON |
| Failure status | HTTP 422 |
| Failure errorCode | PAYLOAD_VALIDATION_FAILED |
| JMS publish on failure | No |

Required validation coverage:

| Area | Required fields |
| --- | --- |
| Header | SalesOrderType, SalesOrganization, DistributionChannel, OrganizationDivision, SoldToParty, PurchaseOrderByCustomer, CustomerPurchaseOrderType, dates, to_Item |
| Item | UnderlyingPurchaseOrderItem, Material, PricingDate, RequestedQuantity, RequestedQuantityUnit, to_PricingElement |
| Pricing | ConditionType, ConditionQuantity, ConditionRateValue, ConditionCurrency |

The body must remain the original SAP Sales Order JSON. Do not map to a custom canonical model.

## 6. Configure JMS Receiver To JMS_SO_INBOUND

Add Receiver participant and JMS Receiver adapter after CM_SetJmsHeaders.

| JMS setting | Value |
| --- | --- |
| Adapter type | JMS Receiver |
| Queue name | JMS_SO_INBOUND |
| Message body | Original SAP Sales Order JSON |
| Delivery mode | Persistent |
| Correlation ID | correlationId |
| Transaction rule | Publish must succeed before ACK |
| Error handling | Exception subprocess returns HTTP 500 on publish failure |

Required JMS properties are correlationId, idempotencyKey, consumerId, purchaseOrderByCustomer, soldToParty, itemCount, inboundReceivedAt, salesOrderType, salesOrganization, and distributionChannel.

## 7. Configure Exception Subprocess

Create one local Exception Subprocess for the main integration process.

| Exception class | HTTP status | errorCode examples | JMS publish |
| --- | --- | --- | --- |
| Header validation | 400 | MISSING_IDEMPOTENCY_KEY, MISSING_CONSUMER_ID, INVALID_CONTENT_TYPE, INVALID_CORRELATION_ID | No |
| Malformed JSON | 400 | INVALID_JSON | No |
| Schema validation | 422 | PAYLOAD_VALIDATION_FAILED | No |
| JMS publish failure | 500 | JMS_PUBLISH_FAILED | Failed |
| Runtime failure | 500 | TECHNICAL_ERROR or SCRIPT_RUNTIME_ERROR | No or unknown |

Exception subprocess steps:

| Step | Action |
| --- | --- |
| 1 | Run GS_BuildErrorContext.groovy |
| 2 | Set MPL fields correlationId, idempotencyKey, consumerId, validationStatus, errorCategory, errorCode, errorMessage |
| 3 | Set Content-Type application/json |
| 4 | Set HTTP status based on error mapping |
| 5 | Build response JSON with status, correlationId, errorCode, and message |
| 6 | End route without JMS publish for validation failures |

## 8. Configure MPL Custom Properties

Add MPL custom properties after monitoring extraction and inside exception subprocess.

| MPL field | Source |
| --- | --- |
| correlationId | correlationId |
| idempotencyKey | idempotencyKey |
| consumerId | consumerId |
| purchaseOrderByCustomer | purchaseOrderByCustomer |
| soldToParty | soldToParty |
| itemCount | itemCount |
| salesOrderType | salesOrderType |
| salesOrganization | salesOrganization |
| distributionChannel | distributionChannel |
| validationStatus | SUCCESS, REJECTED, or FAILED |
| jmsQueueName | JMS_SO_INBOUND |
| inboundReceivedAt | inboundReceivedAt |
| errorCode | Error path only |
| errorCategory | Error path only |

Payload logging must remain disabled by default. Enable only in controlled non-production troubleshooting.

## 9. Deploy Checklist

| Check | Required result |
| --- | --- |
| Package saved | Oatey Sales Order Inbound Integration exists |
| iFlow saved | IFL_SO_INBOUND exists |
| HTTPS endpoint | /sales-orders configured |
| Header validation scripts | Added in correct sequence |
| JSON Schema Validation | Configured before JMS publish |
| JMS Receiver | Points to JMS_SO_INBOUND |
| ACK response | Occurs only after JMS publish |
| Exception subprocess | Returns 400, 422, or 500 correctly |
| MPL custom properties | Configured for success and error paths |
| No S/4HANA receiver | Confirmed absent |
| No excluded services | CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services absent |

Deploy only after all checks pass.

## 10. Postman Test Sequence

| Test | Method and URL | Headers | Body | Expected result |
| --- | --- | --- | --- | --- |
| Valid K-Cimarron | POST APIM /sales-orders | Content-Type, Idempotency-Key, X-Consumer-ID, X-Correlation-ID | K-Cimarron real payload | HTTP 202 and JMS message |
| Valid Affiliated | POST APIM /sales-orders | Content-Type, Idempotency-Key, X-Consumer-ID | Affiliated real payload | HTTP 202 with generated correlationId |
| Missing Idempotency-Key | POST APIM /sales-orders | Content-Type, X-Consumer-ID | Valid payload | HTTP 400 |
| Missing X-Consumer-ID | POST APIM /sales-orders | Content-Type, Idempotency-Key | Valid payload | HTTP 400 |
| Invalid Content-Type | POST APIM /sales-orders | text/plain, Idempotency-Key, X-Consumer-ID | Valid payload | HTTP 400 |
| Missing SoldToParty | POST APIM /sales-orders | Required headers | missing-sold-to-party.json | HTTP 422 |
| Missing Material | POST APIM /sales-orders | Required headers | missing-material.json | HTTP 422 |
| Missing RequestedQuantity | POST APIM /sales-orders | Required headers | missing-requested-quantity.json | HTTP 422 |
| Missing pricing element | POST APIM /sales-orders | Required headers | missing-pricing-element.json | HTTP 422 |
| JMS publish failure | POST APIM /sales-orders | Required headers | Valid payload with JMS disabled in test | HTTP 500 |

## 11. Expected Results

Success response after JMS publish:

| Field | Expected value |
| --- | --- |
| HTTP status | 202 |
| status | ACCEPTED |
| correlationId | Supplied X-Correlation-ID or generated UUID |
| idempotencyKey | Idempotency-Key header |
| message | Sales order accepted for asynchronous processing |

Validation error response:

| Field | Expected value |
| --- | --- |
| HTTP status | 400 or 422 |
| status | REJECTED |
| correlationId | Existing or generated correlationId |
| errorCode | Specific validation code |
| message | Support-friendly validation message |

Technical error response:

| Field | Expected value |
| --- | --- |
| HTTP status | 500 |
| status | FAILED |
| correlationId | Existing or generated correlationId |
| errorCode | JMS_PUBLISH_FAILED, SCRIPT_RUNTIME_ERROR, or TECHNICAL_ERROR |
| message | Support-friendly technical message |

JMS expected result: valid requests create one message in JMS_SO_INBOUND with original SAP Sales Order JSON body and required JMS metadata. Invalid requests create no JMS message.

## Final Guardrails

IFL_SO_INBOUND ends at JMS_SO_INBOUND. It does not call S/4HANA, does not create a canonical model, does not persist outside JMS, and does not introduce CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, or custom Z services.

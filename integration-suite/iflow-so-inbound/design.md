# IFL_SO_INBOUND Build Specification

## Purpose

IFL_SO_INBOUND is the synchronous inbound iFlow exposed behind SAP API Management for the inbound Sales Order POC. It receives a SAP Sales Order API JSON payload, validates headers and payload with Groovy scripts, enriches the exchange with monitoring metadata, sends the unchanged payload to JMS_SO_INBOUND, and returns HTTP 202 only after the JMS send succeeds.

This iFlow does not call S/4HANA. IFL_SO_ORCHESTRATION consumes JMS_SO_INBOUND and invokes the standard SAP Sales Order API.

## Executable Flow

| Step | Component | Implementation action | Output |
| --- | --- | --- | --- |
| 1 | SAP API Management | Authenticate caller, authorize API product access, apply rate limit, capture analytics, and separate consumers | Request forwarded to IFL_SO_INBOUND |
| 2 | HTTPS Sender Adapter | Receive POST /sales-orders with application/json body | CPI exchange created |
| 3 | CM_SetInitialProperties | Set inboundReceivedAt, jmsQueueName, rawContentType, validationStatus, and payloadPreservationMode | Base properties ready |
| 4 | CM_SetHeaderValidationContext | Copy inbound HTTP header values to exchange properties | Header context ready |
| 5 | GS_ValidateHeaders.groovy | Validate Content-Type, set optional Idempotency-Key, and default missing X-Consumer-ID to UNKNOWN_CONSUMER | Header validation passed or controlled error raised |
| 6 | GS_EnsureCorrelationId.groovy | Reuse X-Correlation-ID or generate UUID when missing | correlationId property and header ready |
| 7 | GS_ExtractMonitoringFields.groovy | Parse the SAP Sales Order JSON and validate required payload fields | Monitoring properties ready or controlled validation error raised |
| 8 | CM_SetPayloadValidationStatus | Mark validationStatus SUCCESS | Exchange marked valid |
| 9 | GS_PrepareJmsMessage.groovy | Set JMS-ready headers and assert required JMS metadata | JMS metadata ready |
| 10 | Send to JMS Receiver | Send the original SAP Sales Order JSON body to JMS_SO_INBOUND | Durable queue message created |
| 11 | CM_SetAckResponse | Create HTTP 202 response body and response headers | ACK returned to APIM and caller |
| 12 | Exception Subprocess | Convert validation or technical errors into consistent error responses | HTTP 400, 422, or 500 returned |

There is no JSON Schema Validation step in the executable flow. Payload validation is implemented in Groovy so the POC remains simple and the original SAP Sales Order JSON remains unchanged.

## HTTPS Sender Adapter

| Setting | Value |
| --- | --- |
| Adapter type | HTTPS Sender |
| Address | /sales-orders |
| HTTP method | POST |
| Request content type | application/json |
| Response content type | application/json |
| Authentication | Handled by SAP API Management before forwarding |
| Authorization | Handled by SAP API Management before forwarding |
| ACK timing | Return ACK after successful JMS send, not after SAP order creation |

## Header Rules

| Header | Required for POC | Runtime behavior | Usage |
| --- | --- | --- | --- |
| Content-Type | Yes | Must contain application/json or return HTTP 400 INVALID_CONTENT_TYPE | Payload parser selection |
| X-Correlation-ID | No | Reuse when present; generate UUID when missing | End-to-end trace ID |
| Idempotency-Key | No | Store as empty string when missing; do not fail | Duplicate reference passed downstream when provided |
| X-Consumer-ID | No | Store UNKNOWN_CONSUMER when missing; do not fail | Consumer traceability when provided |

Idempotency-Key is authoritative only as an HTTP header. The request body must not be treated as the source of idempotency.

## Groovy-Based Payload Validation

GS_ExtractMonitoringFields.groovy parses the inbound SAP Sales Order JSON and validates the POC-required structure before JMS send. Validation failures return HTTP 422 PAYLOAD_VALIDATION_FAILED and must not send to JMS.

Required header-level payload fields: SalesOrderType, SalesOrganization, DistributionChannel, OrganizationDivision, SoldToParty, PurchaseOrderByCustomer, CustomerPurchaseOrderType, CustomerPurchaseOrderDate, SalesOrderDate, PricingDate, RequestedDeliveryDate, and to_Item.results.

Required item-level payload fields: UnderlyingPurchaseOrderItem, Material, PricingDate, RequestedQuantity, RequestedQuantityUnit, and to_PricingElement.results.

Required pricing element fields: ConditionType, ConditionQuantity, ConditionRateValue, and ConditionCurrency.

Optional supported fields include IncotermsClassification, to_Text.results, MaterialByCustomer, ZZ1_OriginalOrderQuant_SDI, and ZZ1_OriginalOrderQuant_SDIU.

## JMS Receiver Configuration

Use a one-way Send step to a JMS Receiver adapter. Do not use JMS Request Reply for IFL_SO_INBOUND.

| JMS setting | Value |
| --- | --- |
| Adapter type | JMS Receiver |
| Pattern | Send to JMS Receiver |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Retention | 7 days |
| Transfer Exchange Properties | Enabled |
| Message body | Original SAP Sales Order JSON body |
| Delivery mode | Persistent |
| Error handling | Exception subprocess returns HTTP 500 on JMS send failure |

Required JMS metadata includes correlationId, idempotencyKey, consumerId, purchaseOrderByCustomer, soldToParty, itemCount, inboundReceivedAt, salesOrderType, salesOrganization, and distributionChannel.

## ACK Response

ACK is returned only after successful Groovy validation and successful JMS send.

| Field | Value |
| --- | --- |
| HTTP status | 202 Accepted |
| Content-Type | application/json |
| X-Correlation-ID | correlationId |
| status | ACCEPTED |
| correlationId | correlationId |
| idempotencyKey | idempotencyKey, empty when omitted |
| consumerId | consumerId, UNKNOWN_CONSUMER when omitted |
| message | Sales order accepted for asynchronous processing |

The ACK confirms queue acceptance only. It does not confirm SAP Sales Order creation.

## Exception Subprocess

The local exception subprocess runs GS_BuildErrorContext.groovy, sets MPL fields, sets Content-Type application/json, sets CamelHttpResponseCode from httpStatus, and builds a JSON error response. It must never return HTTP 202.

| Failure | HTTP status | errorCode | JMS send |
| --- | --- | --- | --- |
| Missing or invalid Content-Type | 400 | INVALID_CONTENT_TYPE | No |
| Malformed JSON | 400 | INVALID_JSON | No |
| Missing required SAP Sales Order field | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing required item or pricing field | 422 | PAYLOAD_VALIDATION_FAILED | No |
| JMS Receiver send failure | 500 | JMS_PUBLISH_FAILED | Failed |
| Unexpected Groovy or iFlow runtime failure | 500 | TECHNICAL_ERROR | No or unknown |

## Test Scenarios

| Scenario | Expected result |
| --- | --- |
| Happy Path with valid SAP Sales Order JSON | HTTP 202 and one JMS_SO_INBOUND message |
| Missing SoldToParty | HTTP 422 PAYLOAD_VALIDATION_FAILED and no JMS message |
| Missing Content-Type | HTTP 400 INVALID_CONTENT_TYPE and no JMS message |
| Missing X-Consumer-ID | HTTP 202, consumerId UNKNOWN_CONSUMER, and JMS message |
| Missing X-Correlation-ID | HTTP 202 with generated correlationId and JMS message |
| JMS send failure | HTTP 500 JMS_PUBLISH_FAILED and no ACK |

## Architecture Guardrails

This build specification does not redesign the approved architecture. IFL_SO_INBOUND is limited to inbound validation, metadata enrichment, JMS send, and ACK response. It does not introduce CAP, PostgreSQL, Event Mesh, UI, custom persistence, S/4HANA calls, RFC, BAPI, custom Z services, or a custom canonical model.

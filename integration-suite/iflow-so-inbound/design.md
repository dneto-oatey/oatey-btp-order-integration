# IFL_SO_INBOUND Build Specification

## 1. End-to-End Processing Flow

IFL_SO_INBOUND is the synchronous inbound iFlow exposed behind SAP API Management for the inbound Sales Order POC. The iFlow receives a SAP Sales Order API JSON payload, validates headers and body, enriches the exchange with trace metadata, publishes the unchanged payload to JMS_SO_INBOUND, and returns an HTTP 202 acknowledgement.

Processing sequence:

| Step | Component | Implementation action | Output |
| --- | --- | --- | --- |
| 1 | SAP API Management | Authenticate caller, authorize API product access, apply rate limit, capture analytics, and separate consumers | Request forwarded to IFL_SO_INBOUND |
| 2 | HTTPS Sender Adapter | Receive POST /sales-orders with application/json body | CPI exchange created |
| 3 | CM_SetInitialProperties | Initialize correlationId, idempotencyKey, consumerId, inboundReceivedAt, and jmsQueueName | Exchange properties ready |
| 4 | GS_ValidateHeaders.groovy | Validate X-Correlation-ID, Idempotency-Key, X-Consumer-ID, and Content-Type | Header validation passed or exception raised |
| 5 | JSON Schema Validation | Validate payload against openapi/schemas/sales-order-request.json | Payload validation passed or exception raised |
| 6 | GS_ExtractMonitoringFields.groovy | Extract PurchaseOrderByCustomer, SoldToParty, itemCount, and optional IncotermsClassification | Monitoring properties ready |
| 7 | CM_SetJmsHeaders | Set message headers and JMS properties for downstream orchestration | Message metadata ready |
| 8 | JMS Receiver Adapter | Publish original SAP Sales Order JSON to JMS_SO_INBOUND | Durable message persisted |
| 9 | CM_SetAckResponse | Create HTTP 202 response body and response headers | ACK returned to APIM and caller |
| 10 | Exception Subprocess | Convert validation or technical errors into consistent error responses | HTTP 400, 422, or 500 returned |

This iFlow must not call S/4HANA. IFL_SO_ORCHESTRATION consumes JMS_SO_INBOUND and calls the standard SAP Sales Order API.

## 2. HTTPS Sender Adapter Configuration

| Setting | Value |
| --- | --- |
| Adapter type | HTTPS Sender |
| Address | /sales-orders |
| HTTP method | POST |
| Request content type | application/json |
| Response content type | application/json |
| Authentication | Handled by SAP API Management before forwarding |
| Authorization | Handled by SAP API Management before forwarding |
| Cross-origin handling | Not required at iFlow level for the POC |
| Timeout expectation | Return ACK after JMS publish, not after SAP order creation |
| Direct exposure | Not intended for direct external access |

The iFlow endpoint is an internal runtime endpoint behind APIM. APIM owns security policies, rate limiting, analytics, and consumer separation.

## 3. Required Headers

| Header | Required | Validation | Usage |
| --- | --- | --- | --- |
| X-Correlation-ID | No | If present, must be non-blank. If absent, generate UUID. | End-to-end trace ID |
| Idempotency-Key | Yes | Must be present and non-blank. | Duplicate reference passed to JMS and orchestration |
| X-Consumer-ID | Yes | Must be present and non-blank. | Consumer separation and monitoring |
| Content-Type | Yes | Must contain application/json. | Payload parser selection |

Idempotency-Key is authoritative as an HTTP header. The request body must not be treated as the source of idempotency.

## 4. Exchange Properties

| Property | Type | Source | Required | Purpose |
| --- | --- | --- | --- | --- |
| correlationId | string | X-Correlation-ID or generated UUID | Yes | ACK, errors, JMS metadata, monitoring |
| idempotencyKey | string | Idempotency-Key header | Yes | Duplicate handling reference |
| consumerId | string | X-Consumer-ID header | Yes | Consumer traceability |
| inboundReceivedAt | string | Runtime UTC timestamp | Yes | Operational timestamp |
| jmsQueueName | string | Constant JMS_SO_INBOUND | Yes | Queue traceability |
| validationStatus | string | Validation steps | Yes | SUCCESS or FAILED |
| purchaseOrderByCustomer | string | Payload PurchaseOrderByCustomer | Yes after payload validation | Business trace field |
| soldToParty | string | Payload SoldToParty | Yes after payload validation | Customer trace field |
| itemCount | integer | Count of to_Item.results | Yes after payload validation | Monitoring and support |
| salesOrderType | string | Payload SalesOrderType | Yes after payload validation | Monitoring |
| salesOrganization | string | Payload SalesOrganization | Yes after payload validation | Monitoring |
| distributionChannel | string | Payload DistributionChannel | Yes after payload validation | Monitoring |
| incotermsClassification | string | Payload IncotermsClassification | Optional | Monitoring when present |
| errorCategory | string | Exception subprocess | Only on error | VALIDATION or TECHNICAL |
| errorCode | string | Exception subprocess | Only on error | Machine-readable error code |
| errorMessage | string | Exception subprocess | Only on error | Support-friendly error summary |

## 5. Message Headers

| Header | Set by | Value | Target |
| --- | --- | --- | --- |
| X-Correlation-ID | CM_SetJmsHeaders | correlationId | ACK response and JMS message |
| Idempotency-Key | CM_SetJmsHeaders | idempotencyKey | JMS message |
| X-Consumer-ID | CM_SetJmsHeaders | consumerId | JMS message |
| Content-Type | CM_SetAckResponse or error handler | application/json | HTTP response |
| SAP_MessageProcessingLogCustomStatus | Exception subprocess | SUCCESS, REJECTED, or FAILED | Monitoring convention |

JMS properties should mirror correlationId, idempotencyKey, consumerId, purchaseOrderByCustomer, soldToParty, itemCount, and inboundReceivedAt so IFL_SO_ORCHESTRATION can process and monitor without reparsing metadata first.

## 6. Content Modifiers

| Content Modifier | Location | Implementation detail |
| --- | --- | --- |
| CM_SetInitialProperties | Immediately after HTTPS sender | Set inboundReceivedAt, jmsQueueName, raw request content type, and initialize empty validationStatus |
| CM_SetGeneratedCorrelationId | After initial properties when needed | If X-Correlation-ID is missing, set correlationId to UUID generated by script or expression |
| CM_SetJmsHeaders | After JSON schema validation | Set exchange headers/properties used by JMS receiver adapter |
| CM_SetAckResponse | After successful JMS publish | Set response body, HTTP status 202, Content-Type application/json, and X-Correlation-ID |
| CM_SetValidationErrorResponse | Validation exception path | Set HTTP 400 or 422, error body, Content-Type application/json, and X-Correlation-ID |
| CM_SetTechnicalErrorResponse | Technical exception path | Set HTTP 500, error body, Content-Type application/json, and X-Correlation-ID |

ACK response JSON contains status ACCEPTED, correlationId, idempotencyKey, and message Sales order accepted for asynchronous processing.

## 7. JSON Schema Validation

The JSON Schema validation step uses openapi/schemas/sales-order-request.json as the schema baseline. The body remains SAP Sales Order API shaped and must not be mapped to a custom canonical model in IFL_SO_INBOUND.

Required header-level payload fields:

| Field | Validation |
| --- | --- |
| SalesOrderType | Required non-blank string |
| SalesOrganization | Required non-blank string |
| DistributionChannel | Required non-blank string |
| OrganizationDivision | Required non-blank string |
| SoldToParty | Required non-blank string |
| PurchaseOrderByCustomer | Required non-blank string |
| CustomerPurchaseOrderType | Required non-blank string |
| CustomerPurchaseOrderDate | Required SAP local timestamp pattern |
| SalesOrderDate | Required SAP local timestamp pattern |
| PricingDate | Required SAP local timestamp pattern |
| RequestedDeliveryDate | Required SAP local timestamp pattern |
| to_Item.results | Required array with at least one item |

Required item-level fields:

| Field | Validation |
| --- | --- |
| UnderlyingPurchaseOrderItem | Required non-blank string |
| Material | Required non-blank string |
| PricingDate | Required SAP local timestamp pattern |
| RequestedQuantity | Required non-blank string |
| RequestedQuantityUnit | Required non-blank string |
| to_PricingElement.results | Required array with at least one pricing element |

Required pricing element fields:

| Field | Validation |
| --- | --- |
| ConditionType | Required non-blank string |
| ConditionQuantity | Required non-blank string |
| ConditionRateValue | Required non-blank string |
| ConditionCurrency | Required non-blank string |

Supported optional fields include IncotermsClassification, to_Text.results, MaterialByCustomer, ZZ1_OriginalOrderQuant_SDI, and ZZ1_OriginalOrderQuant_SDIU.

Schema validation failures produce HTTP 422 and must not publish to JMS.

## 8. JMS Receiver Adapter Configuration

| Setting | Value |
| --- | --- |
| Adapter type | JMS Receiver |
| Queue name | JMS_SO_INBOUND |
| Message body | Original SAP Sales Order JSON body |
| Delivery mode | Persistent |
| Correlation ID | correlationId |
| JMS property idempotencyKey | idempotencyKey |
| JMS property consumerId | consumerId |
| JMS property purchaseOrderByCustomer | purchaseOrderByCustomer |
| JMS property soldToParty | soldToParty |
| JMS property itemCount | itemCount |
| Transaction behavior | Publish must complete before ACK response |
| Error handling | Technical exception subprocess on publish failure |

The JMS message is the handoff to IFL_SO_ORCHESTRATION. Do not use Event Mesh for this POC path.

## 9. ACK Response Structure

ACK is returned only after successful validation and successful JMS publish.

| Field | Value |
| --- | --- |
| HTTP status | 202 Accepted |
| Content-Type | application/json |
| X-Correlation-ID | correlationId |
| status | ACCEPTED |
| correlationId | correlationId |
| idempotencyKey | idempotencyKey |
| message | Sales order accepted for asynchronous processing |

The ACK confirms queue acceptance only. It does not confirm SAP Sales Order creation.

## 10. Exception Subprocess

The iFlow has one local exception subprocess for validation and technical failures.

Validation branch:

| Error type | HTTP status | errorCode | JMS publish |
| --- | --- | --- | --- |
| Missing Idempotency-Key | 400 | MISSING_IDEMPOTENCY_KEY | No |
| Missing X-Consumer-ID | 400 | MISSING_CONSUMER_ID | No |
| Invalid Content-Type | 400 | INVALID_CONTENT_TYPE | No |
| Malformed JSON | 400 | INVALID_JSON | No |
| Schema or required field failure | 422 | PAYLOAD_VALIDATION_FAILED | No |

Technical branch:

| Error type | HTTP status | errorCode | JMS publish |
| --- | --- | --- | --- |
| JMS receiver failure | 500 | JMS_PUBLISH_FAILED | Failed or unknown |
| Groovy runtime failure | 500 | SCRIPT_RUNTIME_ERROR | No if before JMS, unknown if during JMS preparation |
| Unexpected iFlow exception | 500 | TECHNICAL_ERROR | Unknown |

Error response JSON contains status REJECTED or FAILED, correlationId, errorCode, and a support-friendly message.

## 11. Logging Strategy

| Logging area | Strategy |
| --- | --- |
| MPL custom fields | Always log correlationId, idempotencyKey, consumerId, purchaseOrderByCustomer, soldToParty, itemCount, validationStatus, and jmsQueueName |
| Payload logging | Disabled by default |
| POC troubleshooting | Payload logging may be temporarily enabled in a controlled non-production tenant |
| Sensitive data | Do not log full payload in normal operation |
| Exceptions | Log concise exception category, errorCode, and support-friendly message |
| Traceability | Every response and JMS message carries correlationId |

Logging must support operations without turning the iFlow into a persistence mechanism.

## 12. Monitoring Fields

| MPL field | Value |
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
| inboundReceivedAt | UTC timestamp |
| errorCode | Populated only on error |
| errorCategory | Populated only on error |

## 13. Test Scenarios

| Scenario | Input | Expected result |
| --- | --- | --- |
| Valid K-Cimarron payload | Real SAP JSON plus required headers | HTTP 202 and JMS message created |
| Valid Affiliated Dist payload | Real SAP JSON plus required headers | HTTP 202 and JMS message created |
| Caller supplies X-Correlation-ID | Valid payload and all headers | Same correlationId returned and sent to JMS |
| Caller omits X-Correlation-ID | Valid payload and required headers | New UUID generated, returned, and sent to JMS |
| Optional IncotermsClassification present | Affiliated Dist payload | Accepted and monitored when present |
| Optional IncotermsClassification absent | K-Cimarron payload | Accepted |
| Header text present | to_Text.results supplied | Accepted |
| Duplicate Idempotency-Key header | Same key submitted twice | idempotencyKey is propagated to JMS for downstream duplicate handling |

## 14. Error Scenarios

| Scenario | Expected HTTP status | Expected errorCode | JMS publish |
| --- | --- | --- | --- |
| Missing Idempotency-Key | 400 | MISSING_IDEMPOTENCY_KEY | No |
| Missing X-Consumer-ID | 400 | MISSING_CONSUMER_ID | No |
| Content-Type is not JSON | 400 | INVALID_CONTENT_TYPE | No |
| Malformed JSON body | 400 | INVALID_JSON | No |
| Missing SoldToParty | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing Material on any item | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing RequestedQuantity on any item | 422 | PAYLOAD_VALIDATION_FAILED | No |
| Missing to_PricingElement.results | 422 | PAYLOAD_VALIDATION_FAILED | No |
| JMS publish failure | 500 | JMS_PUBLISH_FAILED | Failed |
| Unexpected script exception | 500 | SCRIPT_RUNTIME_ERROR | No or unknown depending on failure point |

## Architecture Guardrails

This build specification does not redesign the approved architecture. IFL_SO_INBOUND is limited to inbound validation, metadata enrichment, JMS publish, and ACK response. It does not introduce CAP, PostgreSQL, Event Mesh, UI, custom persistence, or direct S/4HANA calls.

# IFL_SO_INBOUND Groovy Script Specifications

## Scope

Build-ready script specifications for SAP Integration Suite. The executable IFL_SO_INBOUND flow uses Groovy-based header and payload validation. It does not include a JSON Schema Validation step.

Scripts must not call S/4HANA, persist payloads, create a canonical model, or mutate the original message body before JMS send.

## Script Sequence

| Order | Script | Purpose | Body mutation |
| --- | --- | --- | --- |
| 1 | GS_ValidateHeaders.groovy | Validate Content-Type and normalize optional inbound headers | None |
| 2 | GS_EnsureCorrelationId.groovy | Preserve X-Correlation-ID or generate UUID | None |
| 3 | GS_ExtractMonitoringFields.groovy | Parse and validate SAP Sales Order JSON; extract monitoring fields | None |
| 4 | GS_PrepareJmsMessage.groovy | Prepare JMS headers/properties before one-way send | None |
| 5 | GS_BuildErrorContext.groovy | Classify exception and set controlled error fields | Error path only |

## GS_ValidateHeaders.groovy

| Input | Source | POC rule |
| --- | --- | --- |
| Content-Type | HTTP header | Mandatory; must contain application/json |
| Idempotency-Key | HTTP header | Optional; set idempotencyKey to empty when missing |
| X-Consumer-ID | HTTP header | Optional; set consumerId to UNKNOWN_CONSUMER when missing |
| X-Correlation-ID | HTTP header | Optional; handled by GS_EnsureCorrelationId.groovy |

Controlled error output for invalid Content-Type: errorCode INVALID_CONTENT_TYPE, errorCategory VALIDATION, httpStatus 400.

## GS_EnsureCorrelationId.groovy

| Condition | Action |
| --- | --- |
| X-Correlation-ID present and non-blank | Trim and reuse as correlationId |
| X-Correlation-ID missing | Generate UUID using java.util.UUID |

Output fields: exchange property correlationId and message header X-Correlation-ID.

## GS_ExtractMonitoringFields.groovy

Execution point: after header validation and correlation setup. This script performs payload validation by parsing the SAP Sales Order JSON.

Required header-level fields: SalesOrderType, SalesOrganization, DistributionChannel, OrganizationDivision, SoldToParty, PurchaseOrderByCustomer, CustomerPurchaseOrderType, CustomerPurchaseOrderDate, SalesOrderDate, PricingDate, RequestedDeliveryDate, and to_Item.results.

Required item-level fields: UnderlyingPurchaseOrderItem, Material, PricingDate, RequestedQuantity, RequestedQuantityUnit, and to_PricingElement.results.

Required pricing fields: ConditionType, ConditionQuantity, ConditionRateValue, and ConditionCurrency.

Monitoring properties extracted: purchaseOrderByCustomer, soldToParty, itemCount, salesOrderType, salesOrganization, distributionChannel, and incotermsClassification when present.

Controlled validation errors set errorCode PAYLOAD_VALIDATION_FAILED, errorCategory VALIDATION, httpStatus 422. Malformed JSON sets INVALID_JSON with httpStatus 400.

## GS_PrepareJmsMessage.groovy

This script prepares metadata for the JMS Receiver send. It must keep the body unchanged.

| Header or property | Source |
| --- | --- |
| X-Correlation-ID | correlationId property |
| Idempotency-Key | idempotencyKey property, empty allowed |
| X-Consumer-ID | consumerId property, UNKNOWN_CONSUMER allowed |
| purchaseOrderByCustomer | Extracted payload property |
| soldToParty | Extracted payload property |
| itemCount | Extracted payload property |
| inboundReceivedAt | Initial runtime property |
| salesOrderType | Extracted payload property |
| salesOrganization | Extracted payload property |
| distributionChannel | Extracted payload property |

Required before JMS send: correlationId, consumerId, purchaseOrderByCustomer, soldToParty, itemCount, inboundReceivedAt, salesOrderType, salesOrganization, and distributionChannel.

## GS_BuildErrorContext.groovy

| Exception source | errorCategory | errorCode | HTTP status |
| --- | --- | --- | --- |
| Invalid Content-Type | VALIDATION | INVALID_CONTENT_TYPE | 400 |
| Malformed JSON | VALIDATION | INVALID_JSON | 400 |
| Missing SAP Sales Order required field | VALIDATION | PAYLOAD_VALIDATION_FAILED | 422 |
| JMS Receiver failure | TECHNICAL | JMS_PUBLISH_FAILED | 500 |
| Unexpected script/runtime failure | TECHNICAL | TECHNICAL_ERROR | 500 |

Output properties: errorCode, errorCategory, errorMessage, httpStatus, and validationStatus. Validation status is REJECTED for validation errors and FAILED for technical errors.

## Guardrails

No SAP call, no payload persistence, no canonical mapping, no body mutation before JMS send, and no Event Mesh, CAP, PostgreSQL, UI, RFC, BAPI, or custom Z service.

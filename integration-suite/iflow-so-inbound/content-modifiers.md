# IFL_SO_INBOUND Content Modifier Matrix

## Scope

Build-ready SAP Integration Suite Content Modifier specification for IFL_SO_INBOUND. Source of truth remains integration-suite/iflow-so-inbound/design.md. The original SAP Sales Order JSON body must be preserved until successful publish to JMS_SO_INBOUND.

Supported Source Types: Constant, Header, Property, Expression, XPath, JSONPath.

## Modifier Sequence

| Order | Content Modifier | CPI tab used | Runs when | Body behavior |
| --- | --- | --- | --- | --- |
| 1 | CM_SetInitialProperties | Exchange Property | Immediately after HTTPS Sender | Preserve original SAP JSON body |
| 2 | CM_SetHeaderValidationContext | Exchange Property | Before GS_ValidateHeaders.groovy | Preserve original SAP JSON body |
| 3 | CM_SetGeneratedCorrelationId | Message Header and Exchange Property | After GS_EnsureCorrelationId.groovy | Preserve original SAP JSON body |
| 4 | CM_SetPayloadValidationStatus | Exchange Property and Message Header | After JSON Schema Validation and monitoring extraction | Preserve original SAP JSON body |
| 5 | CM_SetJmsHeaders | Message Header | Immediately before JMS Receiver | Preserve original SAP JSON body |
| 6 | CM_SetAckResponse | Message Body and Message Header | Only after successful JMS publish | Replace body with ACK JSON |
| 7 | CM_SetValidationErrorResponse | Message Body and Message Header | Validation exception subprocess | Replace body with validation error JSON |
| 8 | CM_SetTechnicalErrorResponse | Message Body and Message Header | Technical exception subprocess | Replace body with technical error JSON |

## CM_SetInitialProperties

Purpose: initialize base runtime properties before any validation. Configure these rows in the Exchange Property tab.

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Exchange Property | inboundReceivedAt | Expression | \${date:now:yyyy-MM-dd'T'HH:mm:ss'Z'} |
| Create | Exchange Property | jmsQueueName | Constant | JMS_SO_INBOUND |
| Create | Exchange Property | rawContentType | Header | Content-Type |
| Create | Exchange Property | validationStatus | Constant | PENDING |
| Create | Exchange Property | payloadPreservationMode | Constant | ORIGINAL_SAP_JSON |

Body tab: no body entry. The inbound SAP Sales Order JSON must remain unchanged.

## CM_SetHeaderValidationContext

Purpose: copy inbound HTTP headers into exchange properties used by GS_ValidateHeaders.groovy. Configure these rows in the Exchange Property tab.

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Exchange Property | receivedCorrelationHeader | Header | X-Correlation-ID |
| Create | Exchange Property | idempotencyKey | Header | Idempotency-Key |
| Create | Exchange Property | consumerId | Header | X-Consumer-ID |
| Create | Exchange Property | expectedContentType | Constant | application/json |

Implementation rule: Idempotency-Key is authoritative only from the HTTP header. Do not use JSONPath to read idempotency from the body.

## CM_SetGeneratedCorrelationId

Purpose: expose correlationId as both exchange property and message header after GS_EnsureCorrelationId.groovy has preserved or generated it.

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Exchange Property | correlationId | Property | correlationId |
| Create | Message Header | X-Correlation-ID | Property | correlationId |

Build note: GS_EnsureCorrelationId.groovy must run before this Content Modifier. If X-Correlation-ID was missing, the script generates UUID and stores it in property correlationId.

## CM_SetPayloadValidationStatus

Purpose: mark the exchange as successfully validated after JSON Schema Validation and monitoring extraction.

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Update | Exchange Property | validationStatus | Constant | SUCCESS |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | SUCCESS |

Do not run this Content Modifier before schema validation. Validation failures must not publish to JMS.

## CM_SetJmsHeaders

Purpose: create JMS-ready metadata before the JMS Receiver Adapter. Configure these rows in the Message Header tab or adapter-supported JMS property mapping as appropriate in the tenant.

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Message Header | X-Correlation-ID | Property | correlationId |
| Create | Message Header | Idempotency-Key | Property | idempotencyKey |
| Create | Message Header | X-Consumer-ID | Property | consumerId |
| Create | Message Header | correlationId | Property | correlationId |
| Create | Message Header | idempotencyKey | Property | idempotencyKey |
| Create | Message Header | consumerId | Property | consumerId |
| Create | Message Header | purchaseOrderByCustomer | Property | purchaseOrderByCustomer |
| Create | Message Header | soldToParty | Property | soldToParty |
| Create | Message Header | itemCount | Property | itemCount |
| Create | Message Header | inboundReceivedAt | Property | inboundReceivedAt |
| Create | Message Header | salesOrderType | Property | salesOrderType |
| Create | Message Header | salesOrganization | Property | salesOrganization |
| Create | Message Header | distributionChannel | Property | distributionChannel |
| Create | Message Header | incotermsClassification | Property | incotermsClassification |

Body tab: no body entry. JMS body must remain the original SAP Sales Order JSON payload.

## CM_SetAckResponse

Purpose: build HTTP 202 ACK only after JMS Receiver publish succeeds. Configure headers in Message Header tab and response payload in Message Body tab.

Header rows:

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Message Header | Content-Type | Constant | application/json |
| Create | Message Header | X-Correlation-ID | Property | correlationId |
| Create | Message Header | CamelHttpResponseCode | Constant | 202 |

Body row:

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Update | Message Body | Body | Expression | { "status": "ACCEPTED", "correlationId": "\${property.correlationId}", "idempotencyKey": "\${property.idempotencyKey}", "message": "Sales order accepted for asynchronous processing" } |

Rule: this Content Modifier must be placed after JMS Receiver Adapter. HTTP 202 must never be returned before JMS publish success.

## CM_SetValidationErrorResponse

Purpose: build validation error response in the exception subprocess. Configure headers in Message Header tab and response payload in Message Body tab.

Header rows:

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Message Header | Content-Type | Constant | application/json |
| Create | Message Header | X-Correlation-ID | Property | correlationId |
| Create | Message Header | CamelHttpResponseCode | Property | httpStatusCode |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | REJECTED |

Body row:

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Update | Message Body | Body | Expression | { "status": "REJECTED", "correlationId": "\${property.correlationId}", "errorCode": "\${property.errorCode}", "message": "\${property.errorMessage}" } |

Required behavior: validation failures return HTTP 400 or 422 and do not publish to JMS_SO_INBOUND.

## CM_SetTechnicalErrorResponse

Purpose: build technical error response in the exception subprocess, including JMS publish failure.

Header rows:

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Message Header | Content-Type | Constant | application/json |
| Create | Message Header | X-Correlation-ID | Property | correlationId |
| Create | Message Header | CamelHttpResponseCode | Constant | 500 |
| Create | Message Header | SAP_MessageProcessingLogCustomStatus | Constant | FAILED |

Body row:

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Update | Message Body | Body | Expression | { "status": "FAILED", "correlationId": "\${property.correlationId}", "errorCode": "\${property.errorCode}", "message": "\${property.errorMessage}" } |

Required behavior: JMS publish failure returns HTTP 500 with errorCode JMS_PUBLISH_FAILED. Do not return HTTP 202.

## Optional JSONPath Extraction Reference

GS_ExtractMonitoringFields.groovy is the preferred extraction mechanism. If a Content Modifier JSONPath extraction is used instead, use these rows only after JSON Schema Validation succeeds.

| Action | Type | Name | Source Type | Source Value |
| --- | --- | --- | --- | --- |
| Create | Exchange Property | purchaseOrderByCustomer | JSONPath | $.PurchaseOrderByCustomer |
| Create | Exchange Property | soldToParty | JSONPath | $.SoldToParty |
| Create | Exchange Property | salesOrderType | JSONPath | $.SalesOrderType |
| Create | Exchange Property | salesOrganization | JSONPath | $.SalesOrganization |
| Create | Exchange Property | distributionChannel | JSONPath | $.DistributionChannel |
| Create | Exchange Property | incotermsClassification | JSONPath | $.IncotermsClassification |

For itemCount, use Groovy because CPI Content Modifier JSONPath support may not reliably calculate array size across tenants.

## Build Guardrails

| Guardrail | Required behavior |
| --- | --- |
| Payload preservation | No Content Modifier may alter the body before JMS Receiver |
| Idempotency | Do not read idempotencyKey from JSON body |
| Correlation | X-Correlation-ID is optional; use generated UUID when missing |
| Consumer | X-Consumer-ID is mandatory and must be copied from header |
| Validation failure | No JMS publish |
| JMS failure | HTTP 500, not HTTP 202 |
| Runtime scope | No S/4HANA call, CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z service, canonical model, or persistence outside JMS |

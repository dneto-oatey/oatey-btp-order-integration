# IFL_SO_INBOUND Groovy Script Specifications

## Scope

Build-ready script specifications for SAP Integration Suite. These are not deployable source files yet. Scripts must not call S/4HANA, must not persist payloads outside JMS, and must not convert the body to a custom canonical model.

## Script Sequence

| Order | Script | Purpose | Body mutation |
| --- | --- | --- | --- |
| 1 | GS_ValidateHeaders.groovy | Validate mandatory inbound headers and Content-Type | None |
| 2 | GS_EnsureCorrelationId.groovy | Preserve or generate correlationId | None |
| 3 | GS_ExtractMonitoringFields.groovy | Extract SAP payload fields for monitoring and JMS properties | None |
| 4 | GS_PrepareJmsMessage.groovy | Ensure JMS metadata is complete before publish | None |
| 5 | GS_BuildErrorContext.groovy | Classify exception and set error fields | Error response path only |

## GS_ValidateHeaders.groovy

| Input | Source | Required |
| --- | --- | --- |
| Idempotency-Key | HTTP header | Yes |
| X-Consumer-ID | HTTP header | Yes |
| X-Correlation-ID | HTTP header | No |
| Content-Type | HTTP header | Yes |

| Validation | HTTP status | errorCode |
| --- | --- | --- |
| Idempotency-Key missing or blank | 400 | MISSING_IDEMPOTENCY_KEY |
| X-Consumer-ID missing or blank | 400 | MISSING_CONSUMER_ID |
| Content-Type does not contain application/json | 400 | INVALID_CONTENT_TYPE |
| X-Correlation-ID supplied but blank | 400 | INVALID_CORRELATION_ID |

Output properties: idempotencyKey, consumerId, rawContentType, validationStatus HEADER_VALIDATED. Idempotency-Key must never be read from the payload body.

## GS_EnsureCorrelationId.groovy

| Condition | Action |
| --- | --- |
| X-Correlation-ID present and non-blank | Trim and reuse as correlationId |
| X-Correlation-ID missing | Generate UUID using standard Java UUID |
| X-Correlation-ID blank | Raise INVALID_CORRELATION_ID |

Output fields: exchange property correlationId and message header X-Correlation-ID. Body remains unchanged.

## GS_ExtractMonitoringFields.groovy

Execution point: after JSON Schema Validation succeeds.

| Property | JSON field |
| --- | --- |
| purchaseOrderByCustomer | PurchaseOrderByCustomer |
| soldToParty | SoldToParty |
| itemCount | Size of to_Item.results |
| salesOrderType | SalesOrderType |
| salesOrganization | SalesOrganization |
| distributionChannel | DistributionChannel |
| incotermsClassification | IncotermsClassification when present |

Failure behavior: parsing failure after schema validation is TECHNICAL_ERROR with HTTP 500. Do not publish to JMS if this script fails.

## GS_PrepareJmsMessage.groovy

| Required JMS property | Source |
| --- | --- |
| correlationId | Exchange property correlationId |
| idempotencyKey | Exchange property idempotencyKey |
| consumerId | Exchange property consumerId |
| purchaseOrderByCustomer | Exchange property purchaseOrderByCustomer |
| soldToParty | Exchange property soldToParty |
| itemCount | Exchange property itemCount |
| inboundReceivedAt | Exchange property inboundReceivedAt |
| salesOrderType | Exchange property salesOrderType |
| salesOrganization | Exchange property salesOrganization |
| distributionChannel | Exchange property distributionChannel |

Pre-publish assertion: all required JMS properties exist and are non-blank. The body sent to JMS_SO_INBOUND must equal the original inbound SAP Sales Order JSON.

## GS_BuildErrorContext.groovy

| Exception source | errorCategory | errorCode | HTTP status |
| --- | --- | --- | --- |
| Missing Idempotency-Key | VALIDATION | MISSING_IDEMPOTENCY_KEY | 400 |
| Missing X-Consumer-ID | VALIDATION | MISSING_CONSUMER_ID | 400 |
| Invalid Content-Type | VALIDATION | INVALID_CONTENT_TYPE | 400 |
| Invalid X-Correlation-ID | VALIDATION | INVALID_CORRELATION_ID | 400 |
| Malformed JSON | VALIDATION | INVALID_JSON | 400 |
| JSON schema validation | VALIDATION | PAYLOAD_VALIDATION_FAILED | 422 |
| JMS Receiver failure | TECHNICAL | JMS_PUBLISH_FAILED | 500 |
| Unexpected script/runtime failure | TECHNICAL | TECHNICAL_ERROR | 500 |

Output properties: errorCategory, errorCode, errorMessage, validationStatus. Validation status must be REJECTED for validation errors and FAILED for technical errors.

## Script-Level Guardrails

| Guardrail | Required behavior |
| --- | --- |
| No SAP call | Scripts must not invoke S/4HANA |
| No persistence | Scripts must not write to files, databases, CAP, or external stores |
| No canonical mapping | Scripts must not transform to custom canonical model |
| Original body | Scripts must not mutate body before JMS publish |
| Header idempotency | Scripts must trust only HTTP Idempotency-Key header |

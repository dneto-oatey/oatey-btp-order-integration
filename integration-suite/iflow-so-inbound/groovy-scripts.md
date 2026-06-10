# IFL_SO_INBOUND Groovy Script Specifications

## Scope

Build-ready Groovy script specification for SAP Integration Suite. The executable IFL_SO_INBOUND flow uses Groovy-based header and payload validation. It does not include a JSON Schema Validation component.

Scripts must not call S/4HANA, persist payloads, create a canonical model, or mutate the original message body before JMS Send.

## JSON Validation Decision

| Finding | Implementation decision |
| --- | --- |
| Native JSON Schema Validator unavailable in target tenant | Do not use JSON Schema Validation in executable iFlow |
| EDI Validator | Not a valid substitute for JSON validation |
| XML Validator | Not a valid substitute for JSON validation |
| POC validation | Implement payload checks in Groovy |
| Future option | Add JSON Schema validation only if available in the tenant |

## Script Sequence

| Order | Script | Purpose | Body mutation |
| --- | --- | --- | --- |
| 1 | GS_ValidateHeaders.groovy | Validate Content-Type and normalize optional headers | None |
| 2 | GS_EnsureCorrelationId.groovy | Preserve X-Correlation-ID or generate UUID | None |
| 3 | GS_ExtractMonitoringFields.groovy | Parse SAP Sales Order JSON with Reader and extract monitoring fields | None |
| 4 | GS_PrepareJmsMessage.groovy | Prepare JMS headers/properties before one-way Send | None |
| 5 | GS_BuildErrorContext.groovy | Classify exception and set controlled error fields | Error path only |

## Header Behavior

| Header | Rule |
| --- | --- |
| Content-Type | Mandatory; must contain application/json |
| X-Correlation-ID | Optional; preserve when provided and generate UUID when missing |
| Idempotency-Key | Optional for POC; preserve when provided and do not reject when missing |
| X-Consumer-ID | Optional for POC; UNKNOWN_CONSUMER when missing |

## GS_ExtractMonitoringFields.groovy

SAP Integration Suite script readiness checks require streaming Reader parsing, not String body plus parseText.

```groovy
def reader = message.getBody(java.io.Reader)
def json = new JsonSlurper().parse(reader)
```

The script must preserve the original message body for JMS publishing and must not call message.setBody before JMS Send.

## JMS Preparation

GS_PrepareJmsMessage prepares metadata for one-way Send to JMS Receiver. Queue is JMS_SO_INBOUND, Access Type is Non-Exclusive, Expiration Period is 30 Days, Retention Alert Threshold is 2 Days, Transfer Exchange Properties is Enabled, Compress Stored Messages is Enabled, and Encrypt Stored Messages is Enabled.

## Exception Script

GS_BuildErrorContext sets errorCode, errorCategory, errorMessage, httpStatus, and validationStatus. The local exception subprocess returns JSON 400/422 for validation errors and 500 for technical/JMS errors. It must not publish to JMS.

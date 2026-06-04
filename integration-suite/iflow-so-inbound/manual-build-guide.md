# IFL_SO_INBOUND Manual Build Guide

## Purpose

Manual SAP Integration Suite build guide for the completed IFL_SO_INBOUND POC implementation. Runtime remains APIM -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> standard SAP Sales Order API.

No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA call inside IFL_SO_INBOUND, canonical model, or payload persistence outside JMS.

## Current Executable Main Flow

Start / HTTPS Sender -> CM_SetInitialProperties -> CM_SetHeaderValidationContext -> GS_ValidateHeaders -> GS_EnsureCorrelationId -> GS_ExtractMonitoringFields -> CM_SetPayloadValidationStatus -> GS_PrepareJmsMessage -> CM_SetJmsHeaders -> Send to JMS Receiver -> CM_SetAckResponse -> End

No JSON Schema Validation step is present.

## Header Handling Lessons Learned

Capture Content-Type, X-Correlation-ID, X-Consumer-ID, and Idempotency-Key immediately after HTTPS Sender and store them as Exchange Properties. Use Exchange Properties throughout processing because direct HTTP header propagation is not always consistent in CPI runtime.

## Header Rules

| Header | Rule |
| --- | --- |
| Content-Type | Mandatory; must contain application/json |
| X-Correlation-ID | Optional; CPI generates UUID when missing |
| Idempotency-Key | Optional for POC; store empty value when missing |
| X-Consumer-ID | Optional for POC; store UNKNOWN_CONSUMER when missing |

## Validation Approach

Payload validation is Groovy-based. JSON Schema Validation is not used in the executable iFlow because the target tenant did not provide a native JSON Schema Validator. EDI Validator and XML Validator are not valid substitutes. JSON schema validation remains a future option depending on tenant capabilities.

## JMS Receiver

| Setting | Value |
| --- | --- |
| Pattern | One-way Send to JMS Receiver |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |

Do not use JMS Request Reply. HTTP 202 is returned only after successful JMS Send. JMS send failure returns HTTP 500 through the local exception subprocess.

## Exception Subprocess

Exception Subprocess -> GS_BuildErrorContext -> CM_SetErrorResponse -> End

The subprocess must not publish to JMS. It returns JSON error responses, sets Content-Type application/json, sets CamelHttpResponseCode from httpStatus, returns 400/422 for validation errors, and returns 500 for technical or JMS errors.

## Monitoring

Payload logging is not performed for successful transactions. Success path monitoring uses standard MPL, correlationId, and queue monitoring. Error path monitoring uses controlled error responses and error context logging.

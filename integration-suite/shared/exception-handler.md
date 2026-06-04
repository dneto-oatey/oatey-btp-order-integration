# Shared Exception Handler

Purpose: standardize Integration Suite error handling, logging, retry classification, and DLQ routing.

## IFL_SO_INBOUND Exception Subprocess

Implemented local flow:

Exception Subprocess -> GS_BuildErrorContext -> CM_SetErrorResponse -> End

Rules:

| Rule | Behavior |
| --- | --- |
| JMS publish | Exception subprocess must not publish to JMS |
| Response format | JSON error response |
| Content-Type | application/json |
| HTTP status | CamelHttpResponseCode is set from property httpStatus |
| Validation errors | Return 400 or 422 |
| Technical/JMS errors | Return 500 |

## Error Context Logging

Required log fields: correlationId, consumerId, idempotencyKey when provided, flowName, processingStatus, errorCategory, errorCode, errorMessage, and timestamp.

Payload logging is not performed for successful transactions. Error path logging captures controlled error context without turning the integration flow into payload persistence.

## Retry Classification

Retry categories: TRANSIENT and SYSTEM.

Non-retry categories: VALIDATION, BUSINESS_RULE, AUTHENTICATION.

IFL_SO_INBOUND does not perform JMS retry orchestration. JMS decoupling and downstream replay/retry are handled in the orchestration and operations layer.

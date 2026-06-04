# Shared Exception Handler

Purpose: standardize Integration Suite error handling, logging, retry classification, and DLQ routing.

## IFL_SO_INBOUND Status

Status: COMPLETED. The local exception subprocess was successfully validated in SAP Integration Suite runtime.

## IFL_SO_INBOUND Exception Subprocess

Implemented local flow:

Exception Subprocess -> GS_BuildErrorContext -> CM_SetErrorResponse -> End

| Rule | Behavior |
| --- | --- |
| JMS publish | Exception subprocess must not publish to JMS |
| Response format | JSON error response |
| Content-Type | application/json |
| HTTP status | CamelHttpResponseCode is set from property httpStatus |
| Validation errors | Return 400 or 422 |
| Technical/JMS errors | Return 500 |

## Logging Strategy

Success path: no payload logging, only operational metadata.

Error path: no payload persistence by default. Use Trace mode for deep troubleshooting.

Reasons: security, storage optimization, and operational best practices.

## Monitoring Lesson Learned

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Production uses INFO log level. Troubleshooting may temporarily use Debug or Trace. Normal operation does not depend on MPL custom properties.

## Responsibility Boundary

IFL_SO_INBOUND handles transport and integration concerns. SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, and sales order business rules are deferred to IFL_SO_ORCHESTRATION and SAP S/4HANA.

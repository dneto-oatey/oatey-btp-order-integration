# IFL_SO_ORCHESTRATION

Purpose: consume queued sales orders, transform them to SAP Sales Order API format, call S/4HANA, classify results, and trigger callback processing.

## Flow Steps

1. Consume message from `JMS_SO_INBOUND`.
2. Parse payload, metadata, correlation ID, and idempotency key.
3. Check idempotency before SAP submission.
4. Transform canonical payload to `C_SALESORDERAPI_ORDERS` format.
5. Call the standard SAP Sales Order API.
6. Classify response as success, validation, business, transient, or system failure.
7. Trigger confirmation callback if configured.
8. Retry transient/system failures with exponential backoff.
9. Route exhausted or non-recoverable failures to `DLQ_SO_INBOUND`.

## Clean Core Boundary

Use standard SAP APIs only. Do not use RFC, BAPI, custom Z services, or S/4HANA core modifications.

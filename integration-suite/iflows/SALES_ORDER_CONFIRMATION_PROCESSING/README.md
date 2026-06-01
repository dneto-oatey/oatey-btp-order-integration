# Sales Order Confirmation Processing

Purpose: notify asynchronous consumers about final sales order processing results.

## Flow Steps

1. Receive processing result from `IFL_SO_ORCHESTRATION` or S/4HANA confirmation event.
2. Resolve the consumer callback URL from consumer metadata/configuration.
3. Build the callback payload using `openapi/schemas/callback-payload.json`.
4. POST the callback to the consumer endpoint.
5. Retry failed callback delivery up to five times.
6. Log final callback status with correlation ID.

## Callback Status Values

- `SUCCESS`
- `FAILED`
- `PARTIAL`

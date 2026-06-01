# IFL_SO_INBOUND

Purpose: first-stage validation, normalization, metadata enrichment, and JMS publication for asynchronous consumers.

## Flow Steps

1. Receive request from SAP API Management.
2. Validate JSON/XML structure.
3. Validate required fields for the consumer contract.
4. Generate `X-Correlation-ID` when absent.
5. Enrich message with consumer ID, API product, timestamp, and consumer version.
6. Build the JMS envelope for `JMS_SO_INBOUND`.
7. Publish the message to JMS.
8. Return HTTP 202 ACK with the correlation ID.

## Error Handling

- Validation failure: HTTP 400.
- Schema violation: HTTP 422.
- System failure: HTTP 500 with correlation ID.

## Required Artifacts

- `SCR_CORRELATION_ID` for correlation ID generation.
- `VM_CONSUMER_MAPPING` for consumer-specific field mapping.
- Shared exception handling through `SP_EXCEPTION_HANDLER`.

# JMS_SO_INBOUND

Purpose: durable queue for accepted asynchronous inbound sales order messages between IFL_SO_INBOUND and IFL_SO_ORCHESTRATION.

## Implementation Status

JMS publication from IFL_SO_INBOUND to JMS_SO_INBOUND was successfully validated through SAP Integration Suite runtime testing. IFL_SO_INBOUND returns HTTP 202 only after successful JMS publication.

## Actual Queue Configuration

| Setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Producer | IFL_SO_INBOUND |
| Consumer | IFL_SO_ORCHESTRATION |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |
| Send Pattern | One-way Send to JMS Receiver |

Do not use JMS Request Reply for IFL_SO_INBOUND.

## Message Body

The message body is the original SAP Sales Order API JSON payload received by IFL_SO_INBOUND. The inbound iFlow must not mutate the payload, create a canonical model, or persist the payload outside JMS.

## Message Metadata

Inbound HTTP headers are normalized into Exchange Properties immediately after the HTTPS Sender. Transfer Exchange Properties is enabled so IFL_SO_ORCHESTRATION can consume the payload with operational context.

| Property | Source |
| --- | --- |
| correlationId | X-Correlation-ID or generated UUID |
| idempotencyKey | Idempotency-Key header when provided, otherwise empty |
| consumerId | X-Consumer-ID header when provided, otherwise UNKNOWN_CONSUMER |
| purchaseOrderByCustomer | SAP payload |
| soldToParty | SAP payload when available |
| itemCount | SAP payload item count |
| inboundReceivedAt | IFL_SO_INBOUND runtime timestamp |

## Monitoring

Success path monitoring uses standard MPL, correlationId, and queue monitoring. Payload logging is not performed for successful transactions. Error path monitoring uses the local exception subprocess, controlled error responses, and error context logging.

## Guardrails

Do not introduce Event Mesh, CAP, PostgreSQL, UI, RFC, BAPI, custom Z services, custom persistence, or payload logging for successful transactions.

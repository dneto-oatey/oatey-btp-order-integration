# JMS_SO_INBOUND

Purpose: durable queue for accepted asynchronous inbound sales order messages.

## Configuration

| Setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Producer | IFL_SO_INBOUND |
| Consumer | IFL_SO_ORCHESTRATION |
| Access Type | Non-Exclusive |
| Retention | 7 days |
| Message Persistence | Enabled |
| Transfer Exchange Properties | Enabled from IFL_SO_INBOUND JMS Receiver |
| Send Pattern | One-way Send to JMS Receiver |

## Message Body

The message body is the original SAP Sales Order API JSON payload received by IFL_SO_INBOUND. The inbound iFlow must not mutate the payload, create a canonical model, or persist the payload outside JMS.

## Message Metadata

Transfer exchange properties is enabled so the orchestration flow can consume the payload with operational context.

| Property | Source |
| --- | --- |
| correlationId | X-Correlation-ID or generated UUID |
| idempotencyKey | Idempotency-Key header when provided, otherwise empty |
| consumerId | X-Consumer-ID header when provided, otherwise UNKNOWN_CONSUMER |
| purchaseOrderByCustomer | SAP payload |
| soldToParty | SAP payload |
| itemCount | SAP payload item count |
| inboundReceivedAt | IFL_SO_INBOUND runtime timestamp |
| salesOrderType | SAP payload |
| salesOrganization | SAP payload |
| distributionChannel | SAP payload |

## Guardrails

Do not use JMS Request Reply for IFL_SO_INBOUND. Do not introduce Event Mesh, CAP, PostgreSQL, UI, RFC, BAPI, custom Z services, or custom persistence.

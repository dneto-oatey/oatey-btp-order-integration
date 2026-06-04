# JMS_SO_INBOUND

Purpose: durable queue for accepted asynchronous inbound sales order messages between IFL_SO_INBOUND and IFL_SO_ORCHESTRATION.

## Implementation Status

Status: COMPLETED for IFL_SO_INBOUND publication.

JMS publication from IFL_SO_INBOUND to JMS_SO_INBOUND was successfully validated through SAP Integration Suite runtime testing. Messages were successfully published and visible in JMS monitoring. IFL_SO_INBOUND returns HTTP 202 only after successful JMS publication.

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

Inbound HTTP headers are normalized into Exchange Properties immediately after the HTTPS Sender.

| Property | Source |
| --- | --- |
| correlationId | X-Correlation-ID or generated UUID |
| idempotencyKey | Idempotency-Key header when provided, otherwise empty |
| consumerId | X-Consumer-ID header when provided, otherwise UNKNOWN_CONSUMER |
| inboundReceivedAt | IFL_SO_INBOUND runtime timestamp |

## Monitoring

Success path monitoring uses standard MPL, correlationId, and queue monitoring. Payload logging is not performed for successful transactions. SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace, so production operation uses INFO and does not depend on custom MPL fields.

## Next Phase

IFL_SO_ORCHESTRATION will consume JMS_SO_INBOUND and implement idempotency enforcement, SAP Sales Order API invocation, retry strategy, DLQ strategy, callback notifications, and SAP business validation.

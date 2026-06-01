# DLQ_SO_INBOUND

Purpose: hold messages that cannot be processed after retry exhaustion or non-recoverable failure classification.

Required data: originalMessage, failureReason, failureTimestamp, failureCount, correlationId, consumerId, idempotencyKey.

Operations: DLQ depth greater than zero triggers alerting. Replay requires idempotency validation.

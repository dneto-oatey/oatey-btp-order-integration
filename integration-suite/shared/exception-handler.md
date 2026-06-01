# Shared Exception Handler

Purpose: standardize Integration Suite error handling, logging, retry classification, and DLQ routing.

Required log fields: correlationId, consumerId, idempotencyKey, flowName, processingStatus, errorCategory, timestamp.

Retry categories: TRANSIENT and SYSTEM.
Non-retry categories: VALIDATION, BUSINESS_RULE, AUTHENTICATION.

Retry schedule: 5 seconds, 25 seconds, 125 seconds. After retry exhaustion, route to DLQ_SO_INBOUND.

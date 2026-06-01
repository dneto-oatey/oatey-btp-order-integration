# Scripts

## SCR_CORRELATION_ID

Generates a UUID v4 correlation ID when the incoming request does not provide `X-Correlation-ID`.

## SCR_RETRY_HANDLER

Applies the approved exponential backoff policy:

- Retry 1: 5 seconds
- Retry 2: 25 seconds
- Retry 3: 125 seconds

## SP_EXCEPTION_HANDLER

Centralized exception handling for technical failures, logging, alerting, and DLQ routing.

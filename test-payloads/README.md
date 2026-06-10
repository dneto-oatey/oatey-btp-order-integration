# Test Payloads

These files contain request or callback bodies only. Idempotency-Key is authoritative as an HTTP header, not as a sales order body field.

Use the duplicate-idempotency-key.json body with header Idempotency-Key: EDI-ORDER-0001 to test duplicate handling.

Use X-Correlation-ID as an optional trace header. IFL_SO_INBOUND generates a correlationId when the header is absent.

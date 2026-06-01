# Week 1 Review

## Findings

The generated Week 1 artifacts stay aligned with the approved APIM plus SAP Integration Suite plus JMS plus standard SAP Sales Order API runtime architecture. No new CAP, PostgreSQL, Event Mesh, or UI implementation artifacts were introduced by the Week 1 work.

The repository still contains a pre-existing cap README from the baseline repository. This was not introduced by the Week 1 foundation, but it can confuse reviewers because the current approved runtime explicitly excludes CAP for this phase.

API Management is documented as policy and entry-point governance only. The APIM README limits the layer to authentication, authorization, consumer separation, quota, rate limiting, spike arrest, header enforcement, correlation pass-through, and analytics. It does not assign mapping, orchestration, retry, DLQ handling, persistence, or UI responsibilities to APIM.

IFL_SO_INBOUND is documented with the correct responsibility boundary. It receives requests from API Management, validates headers and payload fields, preserves or generates correlationId, requires Idempotency-Key, adds consumer metadata, publishes to JMS_SO_INBOUND, and returns HTTP 202 acknowledgement. It explicitly excludes SAP calls, persistence, CAP, PostgreSQL, Event Mesh, and UI.

IFL_SO_ORCHESTRATION is documented with the correct processing boundary. It consumes JMS_SO_INBOUND, checks idempotency, maps to the standard SAP Sales Order API, calls the standard SAP API only, retries transient and system failures, routes exhausted failures to DLQ_SO_INBOUND, and supports callback processing.

JMS_SO_INBOUND and DLQ_SO_INBOUND are documented consistently with the architecture. JMS_SO_INBOUND is persistent, has seven-day TTL, retries three times, and routes to DLQ_SO_INBOUND. DLQ_SO_INBOUND captures failed messages and requires original message, failure reason, timestamp, failure count, correlationId, consumerId, and idempotencyKey.

The OpenAPI set follows the two approved patterns at skeleton level. sales-order-inbound-api.yaml returns 202 for asynchronous processing. sales-rep-portal-api.yaml models the synchronous portal path to the standard SAP Sales Order API. callback-notification-api.yaml models consumer callback notification.

The test payload set covers success, missing required field validation, duplicate idempotency key, callback success, and callback failure.

## Gaps

The Sales Rep Portal OpenAPI contract is valid YAML because JSON is valid YAML, but it is currently formatted as single-line JSON. It is readable by tooling, but less readable for human review than the other YAML contracts.

The Sales Rep Portal OpenAPI skeleton does not currently declare the X-Correlation-ID and Idempotency-Key headers, even though the architecture and APIM README expect correlation and idempotency controls across the integration.

The asynchronous OpenAPI contract declares 202, 400, 422, and 500 responses, but does not yet include 401, 403, and 429 responses even though API Management owns authentication, authorization, quota, and rate limiting.

The duplicate-idempotency-key test payload includes idempotencyKey in the JSON body. The OpenAPI contract models Idempotency-Key as a required header. This is acceptable as a test note, but Week 2 should make the header-versus-body convention explicit.

The callback OpenAPI contract documents callback payload shape, but does not yet define callback authentication requirements. That is an expected placeholder for Week 1 but must be decided before consumer onboarding.

## Recommended Fixes

Reformat sales-rep-portal-api.yaml into normal multi-line YAML and re-add X-Correlation-ID and Idempotency-Key headers.

Add 401, 403, and 429 responses to sales-order-inbound-api.yaml to match the APIM policy responsibilities.

Clarify idempotency handling by documenting Idempotency-Key as the authoritative API header and treating any payload idempotencyKey field as sample metadata only.

Add callback authentication strategy to callback-notification-api.yaml after the consumer callback security pattern is confirmed.

Add a short note to cap/README.md or docs/README.md stating that CAP is explicitly out of Week 1 and Week 2 runtime scope unless a future approved decision changes the architecture.

## Readiness Status for Week 2 Build

Status: conditionally ready.

The Week 1 foundation is structurally aligned with the approved architecture and is suitable as a starting point for Week 2 implementation planning. The recommended contract fixes should be addressed before building deployable API proxies or CPI artifacts, but no architectural redesign is required.

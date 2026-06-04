# Technical Blueprint

## 1. Objective

Document the final validated POC state for the Oatey SAP BTP inbound Sales Order integration. The approved architecture remains APIM + SAP Integration Suite + JMS + SAP standard Sales Order APIs.

## 2. IFL_SO_INBOUND Status

Status: COMPLETED.

Validated in SAP Integration Suite runtime:

| Capability | Result |
| --- | --- |
| Deployment | Successful |
| OAuth authentication | Successful |
| HTTPS endpoint | Operational |
| Header validation | Operational |
| Correlation ID preservation | Operational |
| Correlation ID auto-generation | Operational |
| Consumer fallback | Operational |
| Idempotency fallback | Operational |
| JMS publication | Operational |
| ACK 202 response | Operational |
| Exception subprocess | Operational |

The inbound integration flow is operational and ready for orchestration phase implementation.

## 3. Validated Test Results

| Scenario | Result | Status |
| --- | --- | --- |
| Happy Path | 202 ACCEPTED | PASS |
| Missing X-Correlation-ID | UUID generated automatically | PASS |
| Missing X-Consumer-ID | UNKNOWN_CONSUMER fallback | PASS |
| Missing Idempotency-Key | Accepted with empty idempotency key | PASS |
| Invalid Content-Type | Rejected | PASS |

All scenarios above were successfully tested in the SAP Integration Suite runtime.

## 4. Runtime Topology

Consumer -> SAP API Management -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> SAP standard Sales Order API -> Callback Notification.

## 5. IFL_SO_INBOUND Responsibility

Responsible for OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

Not responsible for SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules.

## 6. Correlation Strategy

X-Correlation-ID is optional. IFL_SO_INBOUND preserves an incoming value when provided, generates a UUID when missing, returns correlationId in the ACK response, and uses correlationId for operational traceability.

## 7. Idempotency Strategy

Idempotency-Key is optional in the current POC. Current inbound flow preserves idempotency key when provided but does not reject requests when missing. Missing Idempotency-Key is accepted with an empty idempotency key. Idempotency validation is deferred to IFL_SO_ORCHESTRATION. Future production implementation may enforce mandatory idempotency.

## 8. Header Handling Lessons Learned

Inbound HTTP headers were not consistently accessible throughout runtime processing. The final pattern is to capture Content-Type, X-Correlation-ID, X-Consumer-ID, and Idempotency-Key into Exchange Properties immediately after HTTPS Sender and use Exchange Properties during subsequent processing.

## 9. Validation Approach

JSON Schema Validation is not used in the executable iFlow. Current POC validation is implemented through Groovy logic. SAP business validation is deferred to IFL_SO_ORCHESTRATION and the SAP Sales Order API. JSON schema validation remains a future option depending on tenant capabilities and runtime availability.

## 10. JMS Validation

JMS publication to JMS_SO_INBOUND was successfully validated through runtime testing. Messages were successfully published and visible in JMS monitoring.

| Setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |

## 11. Monitoring And Logging Strategy

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Production uses INFO log level. Troubleshooting may temporarily use Debug or Trace. Normal operation does not depend on MPL custom properties.

Success path: no payload logging, only operational metadata. Error path: no payload persistence by default; use Trace mode for deep troubleshooting. Reasons: security, storage optimization, and operational best practices.

## 12. POC Decisions

| Decision | Title | Reason |
| --- | --- | --- |
| D-001 | No CAP Layer | Approved architecture is APIM + Integration Suite + JMS + SAP Standard APIs |
| D-002 | No payload logging on successful processing | Security and operational best practices |
| D-003 | Header normalization immediately after HTTPS Sender | Runtime header propagation inconsistencies |
| D-004 | JMS decoupling between inbound and orchestration layers | Resilience, replay capability, retry support, and consumer decoupling |
| D-005 | Business Validation Deferred to Orchestration Layer | Avoid duplication of SAP business logic inside Integration Suite and maintain Clean Core principles |

## 13. Roadmap

| Phase | Scope | Status |
| --- | --- | --- |
| Current | IFL_SO_INBOUND | COMPLETED |
| Next | IFL_SO_ORCHESTRATION | JMS consumption, idempotency enforcement, SAP Sales Order API invocation, retry strategy, DLQ strategy, callback notifications, and SAP business validation |

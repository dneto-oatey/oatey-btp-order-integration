# Technical Blueprint

## 1. Objective

Document the actual POC implementation decisions for the Oatey SAP BTP inbound Sales Order integration. The approved architecture remains APIM + SAP Integration Suite + JMS + SAP standard Sales Order APIs.

## 2. Implementation Status

IFL_SO_INBOUND status: Completed.

Validated in SAP Integration Suite runtime:

| Capability | Status |
| --- | --- |
| HTTPS Endpoint | Validated |
| OAuth Authentication | Validated |
| Header Validation | Validated |
| Correlation Handling | Validated |
| JMS Publication | Validated |
| HTTP 202 ACK Response | Validated |
| Exception Subprocess | Validated |
| Runtime Deployment | Validated |

The inbound integration flow is operational and ready for orchestration phase implementation.

## 3. Runtime Topology

Consumer -> SAP API Management -> IFL_SO_INBOUND -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> SAP standard Sales Order API -> Callback Notification.

## 4. Correlation Strategy

X-Correlation-ID is optional. IFL_SO_INBOUND preserves an incoming value when provided, generates a UUID when missing, returns correlationId in the ACK response, and uses correlationId for operational traceability.

## 5. Idempotency Strategy

Idempotency-Key is optional in the current POC. Current inbound flow preserves idempotency key when provided but does not reject requests when missing. Idempotency validation is deferred to IFL_SO_ORCHESTRATION. Future production implementation may enforce mandatory idempotency.

## 6. Header Normalization Lessons Learned

Inbound HTTP headers are not always consistently available throughout the CPI runtime. The implemented best practice is to capture inbound headers immediately after HTTPS Sender, store values as Exchange Properties, and use Exchange Properties throughout processing.

| Header | Captured use |
| --- | --- |
| Content-Type | Mandatory application/json validation |
| X-Correlation-ID | Preserve or generate UUID |
| X-Consumer-ID | Optional POC value; UNKNOWN_CONSUMER fallback |
| Idempotency-Key | Optional POC value; empty fallback |

## 7. Validation Approach

JSON Schema Validation is not used in the executable iFlow. The target SAP Integration Suite tenant did not provide a native JSON Schema Validator component. EDI Validator and XML Validator are not valid substitutes for JSON payload validation. Current POC validation is implemented through Groovy logic. JSON schema validation remains a future option depending on tenant capabilities and runtime availability.

## 8. JMS Configuration

JMS publication was successfully validated through runtime testing.

| Setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |

HTTP 202 is returned only after successful JMS publication. JMS send failure returns HTTP 500 through the exception subprocess.

## 9. Monitoring Strategy

Payload logging is not performed for successful transactions. Success path monitoring uses standard MPL, correlationId, and queue monitoring. Error path monitoring uses exception subprocess, controlled error responses, and error context logging. This is intentional for operational best practices and payload minimization.

## 10. Security Strategy

Current POC uses OAuth2 Client Credentials against the Integration Suite Runtime Endpoint. Future production runtime keeps Integration Suite behind SAP API Management. API Management will provide OAuth enforcement, consumer separation, rate limiting, spike arrest, and analytics.

## 11. POC Decisions

| Decision | Description | Reason |
| --- | --- | --- |
| D-001 | No CAP Layer | Approved architecture is APIM + Integration Suite + JMS + SAP Standard APIs |
| D-002 | No payload logging on successful processing | Security and operational best practices |
| D-003 | Header normalization immediately after HTTPS Sender | Runtime header propagation inconsistencies |
| D-004 | JMS decoupling between inbound and orchestration layers | Resilience, replay capability, retry support, and consumer decoupling |

## 12. Build Sequence Status

| Step | Status |
| --- | --- |
| Finalize OpenAPI contracts | Complete for POC |
| Build IFL_SO_INBOUND | Complete and runtime validated |
| Configure JMS_SO_INBOUND | Complete and runtime validated |
| Build IFL_SO_ORCHESTRATION | Next phase |
| Add callback confirmation processing | Future orchestration phase |

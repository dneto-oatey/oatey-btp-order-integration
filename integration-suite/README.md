# Integration Suite

Integration Suite hosts the approved inbound Sales Order runtime components for the Oatey POC.

Runtime scope: IFL_SO_INBOUND validates inbound requests with Groovy-based validation, normalizes headers into Exchange Properties, sends accepted payloads to JMS_SO_INBOUND, and returns HTTP 202 only after successful JMS publication. IFL_SO_ORCHESTRATION is the next implementation phase and will consume JMS messages and call the standard SAP Sales Order API.

## IFL_SO_INBOUND Status

Status: Completed and runtime validated.

Validated capabilities: HTTPS Endpoint, OAuth Authentication, Header Validation, Correlation Handling, JMS Publication, 202 ACK Response, Exception Subprocess, and Runtime Deployment.

## Lessons Learned

Inbound HTTP headers are not always consistently available throughout the CPI runtime. The adopted pattern is to capture Content-Type, X-Correlation-ID, X-Consumer-ID, and Idempotency-Key immediately after HTTPS Sender and use Exchange Properties throughout processing.

JSON Schema Validation is not used in the executable iFlow. The target tenant did not provide a native JSON Schema Validator, and EDI/XML Validators are not substitutes for JSON payload validation. Payload validation is implemented in Groovy.

## Monitoring

Successful transactions do not perform payload logging. Success path monitoring uses standard MPL, correlationId, and queue monitoring. Error path monitoring uses the exception subprocess, controlled error responses, and error context logging.

## Out Of Scope

No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z service, custom S/4HANA core modification, canonical model, or payload persistence outside JMS.

# Integration Suite

Integration Suite hosts the approved inbound Sales Order runtime components for the Oatey POC.

Runtime scope: IFL_SO_INBOUND validates transport and integration concerns, normalizes headers into Exchange Properties, sends accepted payloads to JMS_SO_INBOUND, and returns HTTP 202 only after successful JMS publication. IFL_SO_ORCHESTRATION is the next implementation phase and will consume JMS messages and call the standard SAP Sales Order API.

## IFL_SO_INBOUND Status

Status: COMPLETED.

Runtime validated: deployment, OAuth authentication, HTTPS endpoint, header validation, correlation ID preservation, correlation ID auto-generation, consumer fallback, idempotency fallback, JMS publication, ACK 202 response, and exception subprocess.

## Validated Test Results

| Scenario | Result | Status |
| --- | --- | --- |
| Happy Path | 202 ACCEPTED | PASS |
| Missing X-Correlation-ID | UUID generated automatically | PASS |
| Missing X-Consumer-ID | UNKNOWN_CONSUMER fallback | PASS |
| Missing Idempotency-Key | Accepted with empty idempotency key | PASS |
| Invalid Content-Type | Rejected | PASS |

## Responsibility Boundary

IFL_SO_INBOUND owns OAuth authentication, HTTPS endpoint, header validation, correlation handling, consumer identification, idempotency preservation, basic JSON validation, JMS publication, ACK response, and exception handling.

IFL_SO_INBOUND does not own SAP business validation, customer validation, material validation, pricing validation, partner determination, sales area validation, or sales order business rules. Those responsibilities move to IFL_SO_ORCHESTRATION and the SAP Sales Order API.

## Lessons Learned

Inbound HTTP headers were not consistently accessible throughout runtime processing. The adopted pattern is to capture Content-Type, X-Correlation-ID, X-Consumer-ID, and Idempotency-Key immediately after HTTPS Sender and use Exchange Properties throughout processing.

SAP Integration Suite only exposes Script-added MPL properties when log level is Debug or Trace. Production uses INFO log level. Normal operation does not depend on MPL custom properties.

## Logging Strategy

Successful transactions do not perform payload logging. Error paths do not persist payloads by default. Trace mode is reserved for deep troubleshooting.

## Next Phase

Next phase: IFL_SO_ORCHESTRATION. Scope includes JMS consumption, idempotency enforcement, SAP Sales Order API invocation, retry strategy, DLQ strategy, callback notifications, and SAP business validation.

## Out Of Scope

No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z service, custom S/4HANA core modification, canonical model, or payload persistence outside JMS.

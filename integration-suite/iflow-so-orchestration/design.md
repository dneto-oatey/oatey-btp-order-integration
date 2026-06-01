# IFL_SO_ORCHESTRATION Design

Purpose: consume JMS_SO_INBOUND messages, transform payloads, call the standard SAP Sales Order API, classify outcomes, and trigger callbacks.

Runtime path: JMS_SO_INBOUND to IFL_SO_ORCHESTRATION to standard SAP Sales Order API to callback processing.

Responsibilities: read queued messages, check idempotency before SAP submission, map to SAP Sales Order API format, call the standard SAP API only, retry transient and system failures, and route exhausted failures to DLQ_SO_INBOUND.

Out of scope: RFC, BAPI, custom Z services, CAP, PostgreSQL, Event Mesh, and UI.

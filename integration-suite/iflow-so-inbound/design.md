# IFL_SO_INBOUND Design

Purpose: validate asynchronous inbound sales order requests and publish accepted messages to JMS_SO_INBOUND.

Runtime path: consumer to SAP API Management to IFL_SO_INBOUND to JMS_SO_INBOUND.

Responsibilities: receive requests from SAP API Management, validate required headers and payload fields, preserve or generate correlationId, require Idempotency-Key, add consumer metadata, publish to JMS_SO_INBOUND, and return HTTP 202 acknowledgement.

Out of scope: SAP calls, persistence, CAP, PostgreSQL, Event Mesh, and UI.

# Integration Suite

Week 1 Integration Suite foundation for the approved Oatey inbound sales order architecture.

Runtime scope: iflow-so-inbound validates asynchronous requests and publishes to JMS_SO_INBOUND. iflow-so-orchestration consumes JMS messages and calls the standard SAP Sales Order API. shared/exception-handler.md defines logging, retry classification, and DLQ routing. jms/jms-so-inbound.md documents the inbound queue. jms/dlq-so-inbound.md documents failed message handling.

Repository structure: iflow-so-inbound/design.md, iflow-so-orchestration/design.md, shared/exception-handler.md, jms/jms-so-inbound.md, and jms/dlq-so-inbound.md.

Out of scope: CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z service, and custom S/4HANA core modification.

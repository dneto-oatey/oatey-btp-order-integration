# Week 3 JMS Build Specification

## Purpose

This document defines the JMS build, operations, monitoring, alerting, and replay specification for the Oatey inbound Sales Order POC. Runtime remains SAP API Management, SAP Integration Suite, JMS, and the standard SAP Sales Order API.

Do not introduce CAP, PostgreSQL, Event Mesh, UI, custom persistence, RFC, BAPI, or custom Z services.

## Queue Configuration

| Queue | Type | Producer | Consumer | Purpose |
| --- | --- | --- | --- | --- |
| JMS_SO_INBOUND | Primary durable queue | IFL_SO_INBOUND | IFL_SO_ORCHESTRATION | Stores accepted inbound SAP Sales Order JSON payloads after Groovy validation and ACK |
| DLQ_SO_INBOUND | Dead-letter queue | IFL_SO_ORCHESTRATION exception handling | Operations replay process | Stores messages that cannot be processed after retry exhaustion or non-recoverable failure classification |

## JMS_SO_INBOUND Required Settings

| Setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Retention | 7 days |
| Message Persistence | Enabled |
| Transfer Exchange Properties | Enabled from IFL_SO_INBOUND JMS Receiver |
| Producer Pattern | One-way Send to JMS Receiver |

Do not use JMS Request Reply for IFL_SO_INBOUND.

## DLQ_SO_INBOUND Required Settings

| Setting | Value |
| --- | --- |
| Queue | DLQ_SO_INBOUND |
| Retention | 14 days minimum for POC |
| Message Persistence | Enabled |
| Replay owner | Operations |

## Retry Count And Timing

| Processing area | Retry count | Recommended timing |
| --- | --- | --- |
| SAP Sales Order API transient failure | 3 total attempts | Immediate, 5 minutes, 15 minutes |
| Network timeout or SAP 5xx | 3 total attempts | Immediate, 5 minutes, 15 minutes |
| Callback transient failure | 3 total attempts | Immediate, 5 minutes, 15 minutes |
| Malformed consumed message or missing required JMS metadata | 0 retries | Route directly to DLQ_SO_INBOUND |
| SAP business validation error | 0 technical retries | Trigger FAILED callback; DLQ only when operations policy requires retention |

## Message Body And Metadata

JMS_SO_INBOUND body is the original SAP Sales Order API JSON payload. IFL_SO_INBOUND must not mutate the body, create a canonical model, or persist the payload outside JMS.

| Metadata field | Source | Required |
| --- | --- | --- |
| correlationId | X-Correlation-ID or generated UUID | Yes |
| idempotencyKey | Idempotency-Key header when provided, otherwise empty | No |
| consumerId | X-Consumer-ID header when provided, otherwise UNKNOWN_CONSUMER | Yes |
| purchaseOrderByCustomer | SAP payload | Yes |
| soldToParty | SAP payload | Yes |
| itemCount | SAP payload item count | Yes |
| inboundReceivedAt | IFL_SO_INBOUND timestamp | Yes |

## Monitoring And Alerting

Monitor JMS_SO_INBOUND depth, DLQ_SO_INBOUND depth, oldest message age, retry count, SAP transient errors, callback failures, correlationId, idempotencyKey, and PurchaseOrderByCustomer.

Alert on DLQ depth greater than zero, stale JMS messages, repeated SAP 5xx or timeout errors, repeated callback failures, and missing required JMS metadata in DLQ.

## Replay Procedure

Operations identify the DLQ message by correlationId, idempotencyKey, consumerId, or PurchaseOrderByCustomer. Before replay, confirm whether SAP Sales Order was already created and review duplicate risk. Replay only eligible messages back to JMS_SO_INBOUND or through the approved SAP Integration Suite replay mechanism.

## Architecture Guardrails

JMS is used only as the durable asynchronous handoff between IFL_SO_INBOUND and IFL_SO_ORCHESTRATION, with DLQ_SO_INBOUND for exhausted or non-recoverable failures. Do not introduce CAP, PostgreSQL, Event Mesh, UI, custom persistence, RFC, BAPI, or custom Z services.

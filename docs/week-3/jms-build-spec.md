# Week 3 JMS Build Specification

## Purpose

This document defines the JMS build, operations, monitoring, alerting, and replay specification for the Oatey inbound Sales Order POC. The approved runtime remains SAP API Management, SAP Integration Suite, JMS, and the standard SAP Sales Order API. This specification does not introduce CAP, PostgreSQL, Event Mesh, UI, custom persistence, RFC, BAPI, or custom Z services.

## Queue Names

| Queue | Type | Producer | Consumer | Purpose |
| --- | --- | --- | --- | --- |
| JMS_SO_INBOUND | Primary durable queue | IFL_SO_INBOUND | IFL_SO_ORCHESTRATION | Stores accepted inbound SAP Sales Order JSON payloads after synchronous validation and ACK |
| DLQ_SO_INBOUND | Dead-letter queue | IFL_SO_ORCHESTRATION exception handling | Operations replay process | Stores messages that cannot be processed after retry exhaustion or non-recoverable failure classification |

Queue names must be externalized where the SAP Integration Suite tenant supports deploy-time parameters. The POC default queue names are JMS_SO_INBOUND and DLQ_SO_INBOUND.

## DLQ Names

| Primary queue | Dead-letter queue | Routing owner | Routing trigger |
| --- | --- | --- | --- |
| JMS_SO_INBOUND | DLQ_SO_INBOUND | IFL_SO_ORCHESTRATION | Retry exhaustion, malformed consumed message, missing required JMS metadata, callback failure after policy exhaustion, or non-recoverable SAP/API classification |

DLQ_SO_INBOUND must preserve originalMessage, failureReason, failureTimestamp, failureCount, correlationId, consumerId, and idempotencyKey when available.

## TTL

| Queue | TTL | Rationale |
| --- | --- | --- |
| JMS_SO_INBOUND | 7 days | Allows recovery from temporary SAP/API or runtime outage without keeping stale order submissions indefinitely |
| DLQ_SO_INBOUND | 14 days minimum for POC | Gives operations enough time to inspect, decide, and replay or reject failed messages |

Messages older than TTL should be treated as expired operational data. Replay after TTL expiry must come from the original source system or a formally retained payload outside this integration POC.

## Retry Count

| Processing area | Retry count | Owner |
| --- | --- | --- |
| SAP Sales Order API transient failure | 3 total attempts | IFL_SO_ORCHESTRATION |
| Network timeout or 5xx SAP/API failure | 3 total attempts | IFL_SO_ORCHESTRATION |
| Callback transient failure | 3 total attempts | IFL_SO_ORCHESTRATION callback step |
| Malformed payload or missing JMS metadata | 0 retries | Route directly to DLQ_SO_INBOUND |
| SAP business validation error | 0 technical retries | Trigger FAILED callback; DLQ only when operations policy requires retention/replay |

Retry count means initial attempt plus redelivery attempts should not exceed three processing attempts for the POC unless explicitly changed by operations.

## Retry Timing

| Attempt | Recommended timing | Notes |
| --- | --- | --- |
| Attempt 1 | Immediate | Normal processing after JMS consumption |
| Attempt 2 | 5 minutes after first transient failure | Allows brief SAP/API recovery |
| Attempt 3 | 15 minutes after second transient failure | Allows longer transient recovery before DLQ routing |
| After Attempt 3 | Route to DLQ_SO_INBOUND | Include failureCount and failureReason |

If tenant-level JMS redelivery settings cannot express the exact delay pattern, configure the closest supported retry timing and document the final tenant values in deployment notes.

## Message Persistence

| Requirement | Specification |
| --- | --- |
| Primary queue persistence | Enabled for JMS_SO_INBOUND |
| DLQ persistence | Enabled for DLQ_SO_INBOUND |
| Message body | Original SAP Sales Order API JSON payload |
| Required metadata | correlationId, idempotencyKey, consumerId, purchaseOrderByCustomer, soldToParty, itemCount, inboundReceivedAt |
| Delivery guarantee | Durable queue handoff between IFL_SO_INBOUND and IFL_SO_ORCHESTRATION |
| Payload mutation | IFL_SO_INBOUND and IFL_SO_ORCHESTRATION must not convert the message to a custom canonical model |

Persistence is limited to JMS and DLQ behavior. Do not add PostgreSQL, CAP persistence, Event Mesh persistence, or custom payload storage.

## Message Metadata

| Metadata field | Source | Required | Usage |
| --- | --- | --- | --- |
| correlationId | X-Correlation-ID or generated UUID | Yes | End-to-end trace and replay search |
| idempotencyKey | Idempotency-Key header | Yes | Duplicate prevention and replay review |
| consumerId | X-Consumer-ID header | Yes | Consumer-specific monitoring |
| purchaseOrderByCustomer | SAP payload | Yes | Business lookup |
| soldToParty | SAP payload | Yes | Customer lookup |
| itemCount | SAP payload item count | Yes | Operations triage |
| inboundReceivedAt | IFL_SO_INBOUND timestamp | Yes | Aging and SLA monitoring |
| failureCount | Runtime retry tracking | On failure | DLQ and replay decision |
| failureReason | Exception classification | On failure | DLQ and alert detail |

## Monitoring Requirements

| Metric or field | Target | Purpose |
| --- | --- | --- |
| JMS_SO_INBOUND depth | Primary queue | Detect backlog |
| DLQ_SO_INBOUND depth | Dead-letter queue | Detect failed messages requiring action |
| Oldest message age | Both queues | Detect stuck processing or stale failures |
| Processing attempt count | IFL_SO_ORCHESTRATION | Track retry behavior |
| SAP transient error count | IFL_SO_ORCHESTRATION | Detect SAP/API instability |
| SAP business error count | IFL_SO_ORCHESTRATION | Detect payload or master data issues |
| Callback failure count | IFL_SO_ORCHESTRATION | Detect consumer callback outage |
| CorrelationId search | MPL and JMS metadata | Support end-to-end investigation |
| IdempotencyKey search | MPL and JMS metadata | Support replay and duplicate review |
| PurchaseOrderByCustomer search | MPL and JMS metadata | Business support lookup |

Monitoring must use SAP Integration Suite message monitoring and JMS queue monitoring capabilities. Full payload logging is disabled by default.

## Alerting Requirements

| Alert condition | Severity | Recommended action |
| --- | --- | --- |
| DLQ_SO_INBOUND depth greater than 0 | High | Operations reviews failed message and starts runbook |
| JMS_SO_INBOUND backlog above agreed threshold | Medium | Check IFL_SO_ORCHESTRATION status, SAP API availability, and callback availability |
| Oldest JMS_SO_INBOUND message older than 30 minutes | Medium | Investigate processing slowdown or stuck consumer |
| Oldest DLQ_SO_INBOUND message older than 1 business day | High | Decide replay, reject, or source-system resubmission |
| Repeated SAP 5xx or timeout errors | High | Engage SAP/S/4HANA support path and pause replay until stable |
| Repeated callback failures | Medium | Engage callback endpoint owner |
| Missing required JMS metadata in DLQ | Medium | Review IFL_SO_INBOUND enrichment logic |

Thresholds are POC defaults and should be tuned during volume testing.

## Replay Procedure

| Step | Owner | Action |
| --- | --- | --- |
| 1 | Operations | Open DLQ_SO_INBOUND and identify message by correlationId, idempotencyKey, consumerId, or PurchaseOrderByCustomer |
| 2 | Operations | Review failureReason, failureCount, failureTimestamp, and originalMessage |
| 3 | Functional owner | Confirm whether SAP Sales Order was already created before replay |
| 4 | Operations | Validate idempotencyKey and duplicate risk before any replay |
| 5 | Operations | Correct configuration or wait for SAP/callback endpoint recovery when failure was technical |
| 6 | Operations | Replay only eligible messages back to JMS_SO_INBOUND or restart processing using the approved SAP Integration Suite replay mechanism |
| 7 | Operations | Monitor IFL_SO_ORCHESTRATION through completion |
| 8 | Operations | Confirm SUCCESS callback or final FAILED callback outcome |
| 9 | Operations | Record replay decision and result using correlationId and idempotencyKey |

Replay must not bypass idempotency review. If an SAP Sales Order may already exist, do not replay until the business owner confirms the correct action.

## Replay Eligibility Matrix

| Failure reason | Replay allowed? | Decision rule |
| --- | --- | --- |
| SAP transient outage exhausted retries | Yes | Replay after SAP API is stable and duplicate check is complete |
| Network timeout exhausted retries | Yes | Replay after connectivity is stable and duplicate check is complete |
| Callback endpoint unavailable | Conditional | Replay callback or processing only after endpoint owner confirms readiness |
| Missing JMS metadata | No direct replay | Fix upstream enrichment, then resubmit from source or controlled payload process |
| Malformed SAP JSON payload | No direct replay | Correct source payload and resubmit through APIM/IFL_SO_INBOUND |
| SAP business validation error | Conditional | Replay only after master data or payload issue is corrected and business approves |
| Suspected duplicate order | No until resolved | Confirm SAP order state first |

## Operational Runbook

### Daily Health Check

| Step | Check | Expected result |
| --- | --- | --- |
| 1 | Verify IFL_SO_INBOUND deployed and started | Running |
| 2 | Verify IFL_SO_ORCHESTRATION deployed and started | Running |
| 3 | Check JMS_SO_INBOUND queue depth | Near zero under normal flow |
| 4 | Check DLQ_SO_INBOUND queue depth | Zero |
| 5 | Review message monitor for FAILED or RETRYING statuses | No unexplained failures |
| 6 | Confirm callbacks are being sent for successful SAP order creation | Callback status SENT |

### Backlog Response

| Step | Action |
| --- | --- |
| 1 | Check IFL_SO_ORCHESTRATION runtime status |
| 2 | Check SAP Sales Order API availability and response times |
| 3 | Check callback endpoint availability |
| 4 | Review oldest message age and retry attempts |
| 5 | If SAP/API outage is confirmed, avoid manual replay until service is stable |
| 6 | If iFlow is stopped, restart or redeploy according to environment procedure |

### DLQ Response

| Step | Action |
| --- | --- |
| 1 | Capture correlationId, idempotencyKey, consumerId, PurchaseOrderByCustomer, and failureReason |
| 2 | Classify failure as technical, callback, metadata, malformed payload, or business validation |
| 3 | Check whether SAP Sales Order was already created |
| 4 | Decide replay, reject, or source resubmission |
| 5 | Execute replay only when eligible |
| 6 | Monitor replay result and close the incident with final status |

### Callback Failure Response

| Step | Action |
| --- | --- |
| 1 | Confirm callback endpoint URL and credential configuration |
| 2 | Check endpoint owner status and availability |
| 3 | Retry callback according to policy |
| 4 | Route to DLQ_SO_INBOUND after retry exhaustion when callback cannot be completed |
| 5 | Notify consumer owner with correlationId and idempotencyKey |

## Build Checklist

| Area | Required configuration |
| --- | --- |
| JMS_SO_INBOUND | Durable queue, persistent messages, 7 day TTL, producer IFL_SO_INBOUND, consumer IFL_SO_ORCHESTRATION |
| DLQ_SO_INBOUND | Durable DLQ, persistent messages, 14 day POC TTL, alert on depth greater than zero |
| IFL_SO_INBOUND | Publishes original SAP JSON with required JMS metadata |
| IFL_SO_ORCHESTRATION | Consumes JMS_SO_INBOUND, applies retry strategy, routes DLQ, and triggers callback |
| Monitoring | Queue depth, oldest age, retry count, DLQ depth, correlationId, idempotencyKey, PO number |
| Alerting | DLQ depth, backlog, stale messages, SAP transient errors, callback failures |
| Replay | Idempotency review, SAP order existence check, controlled replay, documented result |

## Architecture Guardrails

This JMS specification does not redesign the approved solution. JMS is used only as the durable asynchronous handoff between IFL_SO_INBOUND and IFL_SO_ORCHESTRATION, with DLQ_SO_INBOUND for exhausted or non-recoverable failures. Do not introduce CAP, PostgreSQL, Event Mesh, UI, custom persistence, RFC, BAPI, or custom Z services.

# Week 3 JMS Build Specification

## Purpose

This document defines the operational JMS configuration validated for the Oatey inbound Sales Order runtime. Runtime remains SAP API Management, SAP Integration Suite, JMS, and the SAP standard Sales Order API.

## JMS Queue Inventory

| Queue | Purpose | Producer | Consumer / Owner |
| --- | --- | --- | --- |
| `JMS_SO_INBOUND` | Durable queue for accepted asynchronous inbound Sales Order messages between inbound and orchestration | `IFL_SO_INBOUND`; eligible replay from `IFL_SO_REPROCESS_DLQ` | `IFL_SO_ORCHESTRATION` |
| `DLQ_SO_INBOUND` | Holds orchestration failures after retry exhaustion or non-recoverable failure classification | `IFL_SO_ORCHESTRATION` exception path | Operations review; optional manual replay via `IFL_SO_REPROCESS_DLQ` |
| `REJECTED_REPLAY_SO_INBOUND` | Operational parking queue for DLQ messages consumed by `IFL_SO_REPROCESS_DLQ` but rejected by replay eligibility rules | `IFL_SO_REPROCESS_DLQ` rejected route only | Operations review / manual investigation |

`REJECTED_REPLAY_SO_INBOUND` does not replace `DLQ_SO_INBOUND`. Normal failures must continue to route to `DLQ_SO_INBOUND`, not directly to `REJECTED_REPLAY_SO_INBOUND`.

## JMS_SO_INBOUND Actual Configuration

| Setting | Value |
| --- | --- |
| Queue | `JMS_SO_INBOUND` |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |
| Producer Pattern | One-way Send to JMS Receiver |

Do not use JMS Request Reply for `IFL_SO_INBOUND`.

## DLQ_SO_INBOUND Usage

`DLQ_SO_INBOUND` remains the target for failed orchestration messages.

Only `IFL_SO_ORCHESTRATION` failure handling should publish normal processing failures to `DLQ_SO_INBOUND`.

DLQ messages may later be reviewed by operations and replayed through `IFL_SO_REPROCESS_DLQ` during an approved replay window.

## REJECTED_REPLAY_SO_INBOUND Usage

`REJECTED_REPLAY_SO_INBOUND` is used only by the rejected route of `IFL_SO_REPROCESS_DLQ`.

Messages are routed here when replay eligibility fails, including missing replay approval, missing originalPayload, missing correlationId, replay count limit reached, auth/config errors, validation errors without explicit approval, or business errors without confirmed correction.

Messages in this queue are parked for operations review and must not be blindly requeued to `JMS_SO_INBOUND`.

## Operational Monitoring

Success path uses standard MPL, correlationId, and queue monitoring. Payload logging is not performed for successful transactions. Script-added MPL properties are visible only in Debug or Trace log level, so production operation uses INFO and does not depend on custom MPL properties.

Monitor all three queues independently:

- `JMS_SO_INBOUND` for accepted/replayed work awaiting orchestration
- `DLQ_SO_INBOUND` for orchestration failures requiring review
- `REJECTED_REPLAY_SO_INBOUND` for replay attempts blocked by governance rules

## Replay And Retry Rationale

JMS decoupling between inbound and orchestration layers provides resilience, replay capability, retry support, and consumer decoupling. `IFL_SO_ORCHESTRATION` owns downstream retry behavior, DLQ strategy, and SAP business validation.

`IFL_SO_REPROCESS_DLQ` owns manual/ad hoc replay from `DLQ_SO_INBOUND`. Eligible messages go to `JMS_SO_INBOUND`; ineligible messages go to `REJECTED_REPLAY_SO_INBOUND`.

## Guardrails

Do not add Event Mesh, PostgreSQL, custom persistence, UI, RFC, BAPI, or custom Z services.

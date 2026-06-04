# Week 3 JMS Build Specification

## Purpose

This document defines the operational JMS configuration validated for the Oatey inbound Sales Order POC. Runtime remains SAP API Management, SAP Integration Suite, JMS, and the SAP standard Sales Order API.

## Status

IFL_SO_INBOUND JMS publication status: COMPLETED.

Runtime validation successful. Messages were successfully published to JMS_SO_INBOUND and visible in JMS monitoring.

## JMS_SO_INBOUND Actual Configuration

| Setting | Value |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |
| Producer Pattern | One-way Send to JMS Receiver |

Do not use JMS Request Reply for IFL_SO_INBOUND.

## Operational Monitoring

Success path uses standard MPL, correlationId, and queue monitoring. Payload logging is not performed for successful transactions. Script-added MPL properties are visible only in Debug or Trace log level, so production operation uses INFO and does not depend on custom MPL properties.

## Replay And Retry Rationale

JMS decoupling between inbound and orchestration layers provides resilience, replay capability, retry support, and consumer decoupling. IFL_SO_ORCHESTRATION owns downstream retry behavior, DLQ strategy, SAP business validation, and callback notifications.

## Guardrails

Do not add Event Mesh, CAP, PostgreSQL, custom persistence, UI, RFC, BAPI, or custom Z services.

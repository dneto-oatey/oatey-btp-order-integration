# Week 3 JMS Build Specification

## Purpose

This document defines the operational JMS configuration validated for the Oatey inbound Sales Order POC. Runtime remains SAP API Management, SAP Integration Suite, JMS, and the SAP standard Sales Order API.

Do not introduce CAP, PostgreSQL, Event Mesh, UI, custom persistence, RFC, BAPI, or custom Z services.

## JMS_SO_INBOUND Actual Configuration

JMS publication from IFL_SO_INBOUND was successfully validated through runtime testing.

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

Payload logging is not performed for successful transactions. Success path monitoring uses standard MPL, correlationId, and queue monitoring. Error path monitoring uses the exception subprocess, controlled error responses, and error context logging.

## Replay And Retry Rationale

JMS decoupling between inbound and orchestration layers provides resilience, replay capability, retry support, and consumer decoupling. IFL_SO_ORCHESTRATION owns downstream retry behavior and DLQ routing once the message is consumed.

## Guardrails

JMS is used only as durable asynchronous handoff between IFL_SO_INBOUND and IFL_SO_ORCHESTRATION. Do not add Event Mesh, CAP, PostgreSQL, custom persistence, UI, RFC, BAPI, or custom Z services.

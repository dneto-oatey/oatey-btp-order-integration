# IFL_SO_INBOUND Implementation Checklist

## Source Of Truth

Checklist for the completed SAP Integration Suite POC implementation of IFL_SO_INBOUND.

## Implementation Status

Status: Completed. Validated: HTTPS Endpoint, OAuth Authentication, Header Validation, Correlation Handling, JMS Publication, 202 ACK Response, Exception Subprocess, and Runtime Deployment.

## Current Executable Main Flow

Start / HTTPS Sender -> CM_SetInitialProperties -> CM_SetHeaderValidationContext -> GS_ValidateHeaders -> GS_EnsureCorrelationId -> GS_ExtractMonitoringFields -> CM_SetPayloadValidationStatus -> GS_PrepareJmsMessage -> CM_SetJmsHeaders -> Send to JMS Receiver -> CM_SetAckResponse -> End

## Required Checks

| Area | Done when |
| --- | --- |
| Correlation | X-Correlation-ID optional; preserve when provided; generate UUID when missing; return correlationId in ACK |
| Idempotency | Idempotency-Key optional; preserve when provided; do not reject when missing |
| Header normalization | Content-Type, X-Correlation-ID, X-Consumer-ID, and Idempotency-Key captured into Exchange Properties after HTTPS Sender |
| Validation | Groovy-based validation; no JSON Schema Validation, EDI Validator, or XML Validator for JSON payload validation |
| JMS pattern | One-way Send to JMS Receiver; no Request Reply |
| JMS queue | JMS_SO_INBOUND |
| JMS Access Type | Non-Exclusive |
| Expiration Period | 30 Days |
| Retention Alert Threshold | 2 Days |
| Transfer Exchange Properties | Enabled |
| Compress Stored Messages | Enabled |
| Encrypt Stored Messages | Enabled |
| ACK rule | HTTP 202 only after successful JMS Send |
| JMS failure | HTTP 500 through local exception subprocess |
| Monitoring | No payload logging for successful transactions |
| Scope | No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, custom Z services, S/4HANA call inside inbound, canonical model, or payload persistence outside JMS |

# IFL_SO_INBOUND Implementation Checklist

## Source Of Truth

This checklist implements integration-suite/iflow-so-inbound/design.md after the implementation findings from the generated Groovy scripts.

## Architecture Guardrails

| Rule | Required implementation behavior |
| --- | --- |
| Runtime path | APIM to IFL_SO_INBOUND to JMS_SO_INBOUND to IFL_SO_ORCHESTRATION |
| SAP call | No S/4HANA call in IFL_SO_INBOUND |
| Payload model | Preserve original SAP Sales Order JSON payload |
| Canonical model | Do not create a custom canonical model |
| Payload validation | Use Groovy-based validation, not executable JSON Schema Validation |
| JMS pattern | Use one-way Send to JMS Receiver, not JMS Request Reply |
| Payload storage | No persistence outside JMS |
| Excluded services | No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, or custom Z service |

## Build Sequence

| Step | Artifact or iFlow shape | Exact build action | Exit condition |
| --- | --- | --- | --- |
| 1 | HTTPS Sender | Configure POST endpoint /sales-orders behind APIM | Request reaches iFlow as application/json |
| 2 | CM_SetInitialProperties | Set inboundReceivedAt, jmsQueueName, and rawContentType | Base properties exist |
| 3 | GS_ValidateHeaders.groovy | Validate Content-Type and normalize optional Idempotency-Key and X-Consumer-ID | Header validation passed or controlled error raised |
| 4 | GS_EnsureCorrelationId.groovy | Preserve X-Correlation-ID or create UUID | correlationId exists |
| 5 | GS_ExtractMonitoringFields.groovy | Validate SAP Sales Order JSON and extract PO, SoldToParty, itemCount, SalesOrg, DistributionChannel, Incoterms when present | Monitoring properties exist |
| 6 | CM_SetPayloadValidationStatus | Set validationStatus SUCCESS | Exchange marked valid |
| 7 | GS_PrepareJmsMessage.groovy | Set JMS-ready headers/properties | JMS metadata ready |
| 8 | JMS Receiver | Send unchanged body to JMS_SO_INBOUND | Durable send succeeds |
| 9 | CM_SetAckResponse | Build HTTP 202 ACK only after JMS send | ACK returned to APIM |
| 10 | Exception Subprocess | Build validation or technical error response | No invalid message reaches JMS |

## Required Runtime Parameters

| Parameter | Value |
| --- | --- |
| inboundResourcePath | /sales-orders |
| expectedContentType | application/json |
| jmsQueueName | JMS_SO_INBOUND |
| jmsAccessType | Non-Exclusive |
| jmsRetention | 7 days |
| transferExchangeProperties | Enabled |
| payloadLoggingEnabled | false |

## Definition Of Done

| Requirement | Done when |
| --- | --- |
| HTTP 202 rule | iFlow returns 202 only after JMS Receiver send succeeds |
| Payload preservation | JMS body equals original SAP Sales Order JSON body |
| Header idempotency | idempotencyKey property is populated only from Idempotency-Key header and may be empty |
| Optional consumer | Missing X-Consumer-ID uses UNKNOWN_CONSUMER and does not fail |
| Optional correlation | Missing X-Correlation-ID produces UUID correlationId |
| JSON content type | Missing or non-JSON Content-Type returns HTTP 400 and no JMS send |
| Payload validation | Missing SoldToParty, Material, RequestedQuantity, or pricing element returns HTTP 422 and no JMS send |
| JMS failure | JMS send failure returns HTTP 500 |
| Scope | No S/4HANA receiver, schema validation step, JMS Request Reply, canonical mapping, custom persistence, or excluded service appears in the iFlow |

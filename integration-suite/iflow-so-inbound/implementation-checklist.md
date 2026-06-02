# IFL_SO_INBOUND Implementation Checklist

## Source Of Truth

This checklist implements integration-suite/iflow-so-inbound/design.md. Build scope is specification only for SAP Integration Suite. Do not create deployable CPI XML in Week 4.

## Architecture Guardrails

| Rule | Required implementation behavior |
| --- | --- |
| Runtime path | APIM to IFL_SO_INBOUND to JMS_SO_INBOUND to IFL_SO_ORCHESTRATION |
| SAP call | No S/4HANA call in IFL_SO_INBOUND |
| Payload model | Preserve original SAP Sales Order JSON payload |
| Canonical model | Do not create a custom canonical model |
| Idempotency source | Idempotency-Key must come from HTTP header only |
| Correlation source | X-Correlation-ID is optional; generate UUID if missing |
| Consumer source | X-Consumer-ID is mandatory |
| Payload storage | No persistence outside JMS |
| Excluded services | No CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, or custom Z service |

## Build Sequence

| Step | Artifact or iFlow shape | Exact build action | Exit condition |
| --- | --- | --- | --- |
| 1 | HTTPS Sender | Configure POST endpoint /sales-orders behind APIM | Request reaches iFlow as application/json |
| 2 | CM_SetInitialProperties | Set inboundReceivedAt, jmsQueueName, and rawContentType | Base properties exist |
| 3 | GS_ValidateHeaders.groovy | Validate Idempotency-Key, X-Consumer-ID, Content-Type, and optional X-Correlation-ID | Header validation passed or controlled error raised |
| 4 | GS_EnsureCorrelationId.groovy | Preserve X-Correlation-ID or create UUID | correlationId exists |
| 5 | JSON Schema Validation | Validate body using openapi/schemas/sales-order-request.json | Invalid payloads stop before JMS |
| 6 | GS_ExtractMonitoringFields.groovy | Extract PO, SoldToParty, itemCount, SalesOrg, DistributionChannel, Incoterms when present | Monitoring properties exist |
| 7 | CM_SetJmsHeaders | Set JMS message headers/properties | JMS metadata ready |
| 8 | JMS Receiver | Publish unchanged body to JMS_SO_INBOUND | Durable publish succeeds |
| 9 | CM_SetAckResponse | Build HTTP 202 ACK only after JMS publish | ACK returned to APIM |
| 10 | Exception Subprocess | Build validation or technical error response | No invalid message reaches JMS |

## Required Runtime Parameters

| Parameter | Default or value | Notes |
| --- | --- | --- |
| inboundResourcePath | /sales-orders | HTTPS Sender resource path |
| expectedContentType | application/json | Accept when header contains this value |
| jmsQueueName | JMS_SO_INBOUND | Receiver queue |
| validationSchema | openapi/schemas/sales-order-request.json | Build-time schema source |
| payloadLoggingEnabled | false | Enable only for controlled POC troubleshooting |

## Required Exchange Properties

| Property | Required before JMS publish | Source |
| --- | --- | --- |
| correlationId | Yes | X-Correlation-ID or generated UUID |
| idempotencyKey | Yes | Idempotency-Key header |
| consumerId | Yes | X-Consumer-ID header |
| inboundReceivedAt | Yes | Runtime UTC timestamp |
| jmsQueueName | Yes | Constant JMS_SO_INBOUND |
| validationStatus | Yes | SUCCESS after schema validation |
| purchaseOrderByCustomer | Yes | Payload PurchaseOrderByCustomer |
| soldToParty | Yes | Payload SoldToParty |
| itemCount | Yes | Count of to_Item.results |
| salesOrderType | Yes | Payload SalesOrderType |
| salesOrganization | Yes | Payload SalesOrganization |
| distributionChannel | Yes | Payload DistributionChannel |

## Definition Of Done

| Requirement | Done when |
| --- | --- |
| HTTP 202 rule | iFlow returns 202 only after JMS Receiver publish succeeds |
| Payload preservation | JMS body equals original SAP Sales Order JSON body |
| Header idempotency | idempotencyKey property is populated only from Idempotency-Key header |
| Optional correlation | Missing X-Correlation-ID still produces UUID correlationId |
| Mandatory consumer | Missing X-Consumer-ID returns HTTP 400 and no JMS publish |
| JSON content type | Non-JSON Content-Type returns HTTP 400 and no JMS publish |
| Schema validation | Missing SoldToParty, Material, RequestedQuantity, or pricing element returns HTTP 422 and no JMS publish |
| JMS failure | JMS publish failure returns HTTP 500 |
| Scope | No S/4HANA receiver, canonical mapping, custom persistence, or excluded service appears in the iFlow |

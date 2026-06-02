# IFL_SO_ORCHESTRATION Build Specification

## Purpose

IFL_SO_ORCHESTRATION is the asynchronous processing iFlow for accepted inbound SAP Sales Order payloads. It consumes messages from JMS_SO_INBOUND, invokes the standard SAP Sales Order API, handles SAP responses, triggers the consumer callback, and routes exhausted or non-recoverable failures to DLQ_SO_INBOUND.

This iFlow does not expose a public API endpoint. It does not use CAP, PostgreSQL, Event Mesh, UI, RFC, BAPI, or custom Z services. The approved runtime remains SAP Integration Suite + JMS + standard SAP Sales Order API, with APIM governing inbound access before IFL_SO_INBOUND.

## 1. JMS Sender Adapter Configuration

| Setting | Value |
| --- | --- |
| Adapter type | JMS Sender |
| Queue name | JMS_SO_INBOUND |
| Consumer iFlow | IFL_SO_ORCHESTRATION |
| Message body | Original SAP Sales Order JSON payload from IFL_SO_INBOUND |
| Polling mode | Continuous JMS consumption as configured in SAP Integration Suite |
| Transaction behavior | Message is completed only after SAP API response and callback processing are handled according to retry logic |
| Retry source | JMS redelivery and iFlow exception handling |
| Expected properties | correlationId, idempotencyKey, consumerId, purchaseOrderByCustomer, soldToParty, itemCount, inboundReceivedAt |
| DLQ target | DLQ_SO_INBOUND after retry exhaustion or non-recoverable failure classification |

JMS_SO_INBOUND is the only source queue for this iFlow. Do not consume from Event Mesh or any custom persistence layer.

## 2. Message Consumption Flow

| Step | Component | Implementation action | Output |
| --- | --- | --- | --- |
| 1 | JMS Sender Adapter | Consume one message from JMS_SO_INBOUND | CPI exchange with SAP Sales Order JSON body |
| 2 | CM_ReadJmsMetadata | Read JMS properties into exchange properties | correlationId, idempotencyKey, consumerId, purchaseOrderByCustomer, soldToParty, itemCount |
| 3 | GS_ValidateConsumedMessage.groovy | Confirm required metadata and payload structure are present | Message accepted for SAP call or exception raised |
| 4 | CM_PrepareSapRequest | Set SAP request headers and target endpoint properties | HTTP request ready |
| 5 | Request Mapping | Preserve SAP Sales Order API JSON shape and apply only required technical normalization | SAP request body ready |
| 6 | HTTP Receiver Adapter | POST to standard SAP Sales Order API | SAP response received |
| 7 | GS_HandleSapResponse.groovy | Classify SAP result and extract sales order number or errors | processingStatus and callback data ready |
| 8 | Callback Trigger | Send callback payload to configured consumer callback endpoint | SUCCESS or FAILED callback attempted |
| 9 | Completion | Complete JMS message when processing is successful or terminal failure has been handled | JMS message removed or routed |
| 10 | Exception Subprocess | Retry transient failures or route exhausted/non-recoverable failures to DLQ_SO_INBOUND | Controlled failure outcome |

## 3. SAP Sales Order API Invocation

| Setting | Value |
| --- | --- |
| Receiver adapter | HTTP Receiver |
| API type | Standard SAP Sales Order API |
| Operation | POST sales order create request |
| Request body | SAP Sales Order JSON from JMS message |
| Authentication | SAP destination or configured credential artifact for S/4HANA |
| Content-Type | application/json |
| Accept | application/json |
| Correlation propagation | Send X-Correlation-ID or equivalent trace header when supported |
| Timeout | Environment-specific; must be shorter than the iFlow/JMS processing timeout |
| Success statuses | 200 or 201 depending on SAP API behavior |
| Business error statuses | 400, 409, 422, or SAP OData business error payloads |
| Technical error statuses | 401, 403, 408, 429, 500, 502, 503, 504, network or timeout failures |

The exact SAP endpoint and destination names should be externalized as deployable parameters. Do not hard-code tenant-specific hostnames in the iFlow artifacts.

## 4. Request Mapping Requirements

The payload consumed from JMS is already shaped for the standard SAP Sales Order API. IFL_SO_ORCHESTRATION should not map it to a custom canonical object.

| Requirement | Implementation detail |
| --- | --- |
| Preserve SAP payload | Keep SalesOrderType, SalesOrganization, DistributionChannel, OrganizationDivision, SoldToParty, PurchaseOrderByCustomer, dates, to_Item.results, to_PricingElement.results, and to_Text.results as received |
| Preserve optional fields | Pass IncotermsClassification, MaterialByCustomer, ZZ extension fields, and text entries when present |
| Technical headers | Add Content-Type, Accept, authentication, CSRF handling if required by the chosen SAP API runtime, and correlation header |
| Idempotency metadata | Do not place Idempotency-Key in the SAP request body unless SAP API design explicitly requires it; keep it as integration metadata |
| Date handling | Do not reformat SAP local timestamp values unless SAP API requires a specific alternate OData representation |
| Payload logging | Do not log full request body by default |

If a CSRF token is required by the SAP API endpoint, implement token fetch/reuse with a receiver channel or pre-call step according to SAP Integration Suite standards.

## 5. Response Handling

| SAP outcome | Classification | Handling |
| --- | --- | --- |
| HTTP 200 or 201 with sales order number | SUCCESS | Extract salesOrderNumber, trigger SUCCESS callback, complete JMS message |
| HTTP 200 or 201 without expected sales order number | TECHNICAL_ERROR | Treat as unexpected response, retry according to technical retry strategy |
| SAP validation/business error | BUSINESS_ERROR | Trigger FAILED callback, route to DLQ only if replay/manual analysis is required by operations |
| HTTP 409 duplicate or idempotency conflict | BUSINESS_ERROR or DUPLICATE | Trigger FAILED or duplicate-aware callback; do not blindly retry |
| HTTP 401 or 403 | TECHNICAL_ERROR | Retry only after credential/config issue policy; otherwise DLQ after exhaustion |
| HTTP 408, 429, 500, 502, 503, 504 | TRANSIENT_TECHNICAL_ERROR | Retry |
| Network timeout | TRANSIENT_TECHNICAL_ERROR | Retry |
| Malformed SAP response | TECHNICAL_ERROR | Retry and then DLQ after exhaustion |

The callback payload follows openapi/schemas/callback-payload.json and includes correlationId, status, consumerId, processingTimestamp, salesOrderNumber when available, and errors when failed.

## 6. Retry Strategy

| Failure class | Retry? | Strategy |
| --- | --- | --- |
| SAP transient HTTP 408, 429, 500, 502, 503, 504 | Yes | Retry through JMS redelivery/iFlow exception handling up to 3 attempts |
| Network timeout or connection failure | Yes | Retry up to 3 attempts |
| JMS or callback transient technical failure | Yes | Retry callback or route according to callback failure policy |
| SAP business validation error | No | Trigger FAILED callback and complete or DLQ according to DLQ rule |
| Missing required JMS metadata | No | Route to DLQ_SO_INBOUND |
| Malformed consumed payload | No | Route to DLQ_SO_INBOUND |
| Authentication/configuration error | Limited | Retry if classified transient; otherwise DLQ after configured attempts |

Recommended retry configuration for POC: maximum 3 attempts, with increasing delay between attempts when the tenant/runtime supports it. Retry count must be captured as failureCount or retryAttempt in monitoring fields.

## 7. DLQ Routing Logic

Route to DLQ_SO_INBOUND when a message cannot be processed safely or retry attempts are exhausted.

| DLQ condition | Required DLQ payload metadata |
| --- | --- |
| Retry exhaustion for transient SAP/API failure | originalMessage, failureReason, failureTimestamp, failureCount, correlationId, consumerId, idempotencyKey |
| Malformed consumed message | originalMessage, failureReason, failureTimestamp, correlationId when available |
| Missing correlationId/idempotencyKey/consumerId | originalMessage, failureReason, failureTimestamp, available metadata |
| Unexpected response shape after retry exhaustion | originalMessage, SAP response summary, failureReason, failureTimestamp, failureCount |
| Callback failure after policy exhaustion | originalMessage, callback payload, callback endpoint reference, failureReason, failureTimestamp |

DLQ messages must preserve enough context for manual replay. Replay requires idempotency review before resubmitting to avoid duplicate SAP Sales Orders.

## 8. Callback Trigger Logic

Callback is triggered after SAP processing is classified as SUCCESS or terminal FAILED.

| Callback field | Source |
| --- | --- |
| correlationId | JMS property correlationId |
| status | SUCCESS or FAILED |
| salesOrderNumber | SAP response when created |
| consumerId | JMS property consumerId |
| processingTimestamp | Current UTC timestamp |
| errors | SAP business errors or technical terminal errors |

Callback rules:

| Condition | Callback behavior |
| --- | --- |
| SAP order created | Send SUCCESS callback with salesOrderNumber |
| SAP business validation failure | Send FAILED callback with SAP error details |
| Retry exhaustion | Send FAILED callback when callback endpoint is available, then route to DLQ if required |
| Callback endpoint unavailable | Retry according to callback retry policy; route to DLQ after exhaustion |
| Missing callback endpoint configuration | Route to DLQ or operations alert according to environment configuration |

The callback endpoint and credentials should be externalized. The callback should not be sent before the SAP Sales Order API outcome is known.

## 9. Exception Subprocess

The orchestration iFlow should use a local exception subprocess to classify failures and decide retry, callback, or DLQ routing.

| Exception category | Examples | Action |
| --- | --- | --- |
| VALIDATION_ERROR | Missing JMS metadata, malformed payload | Route to DLQ_SO_INBOUND, no SAP call |
| SAP_BUSINESS_ERROR | SAP rejected order due to field or business rule | Trigger FAILED callback; optionally DLQ based on operations policy |
| SAP_TRANSIENT_ERROR | Timeout, 429, 5xx, network failure | Retry until max attempts, then DLQ and failed callback |
| SAP_AUTH_CONFIG_ERROR | 401, 403, credential/destination issue | Retry if transient; otherwise DLQ after exhaustion |
| CALLBACK_ERROR | Callback endpoint failure | Retry callback, then DLQ after exhaustion |
| TECHNICAL_ERROR | Script or adapter runtime error | Retry when safe, otherwise DLQ |

The exception subprocess must set errorCategory, errorCode, errorMessage, failureTimestamp, and failureCount before routing or completing the message.

## 10. Monitoring Strategy

| Monitoring field | Source | Purpose |
| --- | --- | --- |
| correlationId | JMS metadata | End-to-end trace |
| idempotencyKey | JMS metadata | Duplicate investigation |
| consumerId | JMS metadata | Consumer traceability |
| purchaseOrderByCustomer | JMS metadata or payload | Business support lookup |
| soldToParty | JMS metadata or payload | Customer support lookup |
| itemCount | JMS metadata or payload | Payload size/support |
| sapSalesOrderNumber | SAP response | Created document reference |
| processingStatus | Runtime classification | SUCCESS, FAILED, RETRYING, DLQ_ROUTED |
| retryAttempt | Runtime/JMS | Retry tracking |
| errorCategory | Exception subprocess | Failure grouping |
| errorCode | Exception subprocess | Machine-readable failure |
| callbackStatus | Callback step | SENT, FAILED, NOT_CONFIGURED |
| jmsQueueName | Constant | JMS_SO_INBOUND |
| dlqQueueName | Constant when used | DLQ_SO_INBOUND |

Monitoring should allow support to search by correlationId, idempotencyKey, customer PO, sold-to party, SAP Sales Order number, and processing status.

## 11. Logging Strategy

| Area | Strategy |
| --- | --- |
| Normal success | Log metadata only: correlationId, idempotencyKey, consumerId, PO, soldToParty, itemCount, SAP order number |
| SAP error | Log SAP error code/message summary, not full payload by default |
| Callback error | Log callback status, endpoint alias, errorCode, and correlationId |
| DLQ routing | Log DLQ reason, failureCount, and replay guidance metadata |
| Payload logging | Disabled by default; enable only in controlled non-production troubleshooting |
| Sensitive data | Do not log full payloads, credentials, tokens, or full auth headers |

Logging must not become a persistence layer. The original failed message is preserved only through JMS/DLQ handling.

## 12. Test Scenarios

| Scenario | Input | Expected result |
| --- | --- | --- |
| Valid K-Cimarron JMS message | Real SAP JSON with JMS metadata | SAP API called, SUCCESS callback sent, JMS message completed |
| Valid Affiliated Dist JMS message | Real SAP JSON with IncotermsClassification | SAP API called, SUCCESS callback sent, JMS message completed |
| SAP business validation error | SAP returns business error | FAILED callback sent; no retry unless configured; DLQ based on policy |
| SAP transient 503 | SAP returns 503 | Retry up to max attempts; success completes message or exhaustion routes DLQ |
| SAP timeout | Receiver timeout | Retry and then DLQ after exhaustion |
| Missing JMS idempotencyKey | Message lacks required metadata | No SAP call; route to DLQ_SO_INBOUND |
| Malformed JSON from queue | Invalid body | No SAP call; route to DLQ_SO_INBOUND |
| Callback success | SAP success and callback endpoint available | Callback status SENT and message completed |
| Callback transient failure | SAP success but callback returns 500 | Retry callback or route to DLQ after policy exhaustion |
| Duplicate/replay review | DLQ replay candidate | Validate idempotency before resubmitting |

## Architecture Guardrails

This build specification does not redesign the approved architecture. IFL_SO_ORCHESTRATION consumes JMS_SO_INBOUND, calls the standard SAP Sales Order API, handles response/callback, and routes exhausted failures to DLQ_SO_INBOUND. It does not introduce CAP, PostgreSQL, Event Mesh, UI, custom persistence, RFC, BAPI, or custom Z services.

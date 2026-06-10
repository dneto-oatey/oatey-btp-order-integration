# Integration Suite

Integration Suite hosts the validated inbound Sales Order runtime components for Oatey.

## Current Validated Architecture

```text
APIM
-> IFL_SO_INBOUND
-> JMS_SO_INBOUND
-> IFL_SO_ORCHESTRATION
-> SAP Standard API API_SALES_ORDER_SRV via HTTP Receiver
-> DLQ_SO_INBOUND on failure
-> Optional manual replay using IFL_SO_REPROCESS_DLQ
   -> JMS_SO_INBOUND if replay eligible
   -> REJECTED_REPLAY_SO_INBOUND if replay is not eligible
```

## Runtime Status

| iFlow | Status | Runtime Role |
| --- | --- | --- |
| `IFL_SO_INBOUND` | Completed and runtime validated | HTTPS intake, transport validation, correlation handling, JMS publish, 202 async ACK |
| `IFL_SO_ORCHESTRATION` | Runtime connectivity validated | JMS consumption, CSRF fetch, SAP HTTP POST to API_SALES_ORDER_SRV, DLQ routing on failure |
| `IFL_SO_REPROCESS_DLQ` | Manual/ad hoc utility | Operations-controlled replay from `DLQ_SO_INBOUND` to `JMS_SO_INBOUND` or `REJECTED_REPLAY_SO_INBOUND` |

## Key Implementation Decisions

- `IFL_SO_ORCHESTRATION` uses HTTP Receiver instead of OData Receiver because the OData adapter attempted to parse JSON deep insert payloads as XML/Atom.
- CSRF is handled explicitly with HTTP GET `/sap/opu/odata/sap/API_SALES_ORDER_SRV/`, `x-csrf-token = Fetch`, token extraction, SAP cookie extraction, and HTTP POST to `/sap/opu/odata/sap/API_SALES_ORDER_SRV/A_SalesOrder`.
- `sapRequestPayload` is preserved before CSRF GET because the GET response replaces the message body.
- `GS_PrepareDlqPayload` resolves `originalPayload` using this priority: `sapRequestPayload`, `originalPayload`, current message body.
- DLQ replay governance is controlled by DLQ envelope metadata, not JMS technical redelivery count.
- `IFL_SO_REPROCESS_DLQ` must remain Not Deployed by default, deployed only during an approved replay window, and undeployed immediately after replay completion.

## Validated Test Results

| Scenario | Result | Status |
| --- | --- | --- |
| Inbound accepts message and returns async response | HTTP 202 accepted after JMS publish | PASS |
| Orchestration consumes `JMS_SO_INBOUND` | JMS consumption works | PASS |
| CSRF token fetch | Token fetch works | PASS |
| SAP session cookie handling | Cookie extraction and POST header preparation works | PASS |
| SAP POST reaches API_SALES_ORDER_SRV | SAP returned business validation response | PASS |
| DEV payload SAP response | `Sales area 1000 10 00 does not exist` | PASS |
| Orchestration failure routing | Message routed to `DLQ_SO_INBOUND` | PASS |
| Reprocess without replay approval | Routed to `REJECTED_REPLAY_SO_INBOUND` | PASS |
| Reprocess missing `originalPayload` | Routed to `REJECTED_REPLAY_SO_INBOUND` | PASS |
| Replay governance headers | Visible in MPL custom headers when populated | PASS |
| Payload logging | No payload logged to MPL in normal/custom header logs | PASS |

## MPL Custom Headers

Scripts that set MPL custom headers must only add a custom header property when the value exists. Empty values must not be written because MPL appends values and does not overwrite previous custom header values.

Inbound custom headers:

- `ConsumerID`
- `correlationId`
- `IdempotencyKey`
- `validationStatus`

Orchestration custom headers:

- `ConsumerID`
- `correlationId`
- `IdempotencyKey`
- `processingStatus`
- `errorCategory`
- `sapResponseStatusCode`
- `replayCount`
- `maxReplayCount`

Reprocess custom headers:

- `ConsumerID`
- `correlationId`
- `IdempotencyKey`
- `processingStatus`
- `replayEligible`
- `replayCount`
- `maxReplayCount`
- `replayTarget`
- `replayRejectionCode`
- `sapResponseStatusCode`
- `validationStatus` when inherited

## Responsibility Boundary

`IFL_SO_INBOUND` owns transport and integration concerns only. SAP business validation remains in `IFL_SO_ORCHESTRATION` and SAP S/4HANA through `API_SALES_ORDER_SRV`.

`IFL_SO_ORCHESTRATION` invokes SAP standard APIs and classifies SAP responses. Callback remains optional and is not part of the validated core path.

`IFL_SO_REPROCESS_DLQ` is a manual replay utility, not a continuously running runtime flow.

## Guardrails

Keep Clean Core and SAP standard API usage. Do not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, custom persistence, UI, or payload logging to MPL. Do not log CSRF token, SAP cookie, Authorization header, bearer token, password, or secrets.

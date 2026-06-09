# IFL_SO_REPROCESS_DLQ Design

## Purpose

`IFL_SO_REPROCESS_DLQ` supports manual and ad hoc replay of reviewed Sales Order DLQ messages from `DLQ_SO_INBOUND` back to `JMS_SO_INBOUND`.

This iFlow is an operations-controlled replay utility only. It does not call SAP, does not perform SAP business validation, and does not modify the Sales Order payload.

## Approved Runtime Flow

```text
JMS Sender DLQ_SO_INBOUND
-> GS_ValidateDlqReplayEligibility
-> Router eligible vs rejected

Eligible:
  -> GS_ExtractOriginalPayloadFromDlq
  -> Router extraction still eligible vs rejected
     -> replayEligible = true:
        CM_PrepareRequeueToInbound
        JMS Receiver JMS_SO_INBOUND
     -> otherwise:
        GS_PrepareReplayRejectedPayload
        CM_SetReplayRejectedHeaders
        JMS Receiver REJECTED_REPLAY_SO_INBOUND

Rejected:
  -> GS_PrepareReplayRejectedPayload
  -> CM_SetReplayRejectedHeaders
  -> JMS Receiver REJECTED_REPLAY_SO_INBOUND

-> End
```

## Operational Mode

| Area | Decision |
| --- | --- |
| Default state | Deployed but stopped |
| Execution mode | Manual start only |
| Trigger | Operations review of DLQ message and replay approval |
| Runtime window | Start for controlled replay window only |
| Shutdown rule | Stop immediately after the approved replay window |
| Replay source | `DLQ_SO_INBOUND` |
| Replay target | `JMS_SO_INBOUND` |
| Rejection target | `REJECTED_REPLAY_SO_INBOUND` |

The iFlow remains JMS Sender based for now, but it must not be left running continuously. Starting the iFlow can consume all available messages from `DLQ_SO_INBOUND`, so operations must run it only during a controlled replay window.

## Responsibilities

`IFL_SO_REPROCESS_DLQ` is responsible for:

- Consuming reviewed DLQ envelopes from `DLQ_SO_INBOUND` only during approved manual replay windows.
- Validating replay eligibility from explicit DLQ envelope control fields.
- Routing eligible messages to `JMS_SO_INBOUND`.
- Routing ineligible messages to `REJECTED_REPLAY_SO_INBOUND`.
- Extracting `originalPayload` from eligible DLQ envelopes.
- Routing extraction failures to `REJECTED_REPLAY_SO_INBOUND`.
- Republishing only the original Sales Order JSON payload to `JMS_SO_INBOUND`.
- Preserving replay traceability fields:
  - `correlationId`
  - `consumerId`
  - `idempotencyKey`
- Adding replay metadata as message headers and exchange properties.

## Non-Responsibilities

`IFL_SO_REPROCESS_DLQ` is not responsible for:

- Calling SAP S/4HANA directly.
- Calling `API_SALES_ORDER_SRV` directly.
- Modifying the Sales Order payload.
- Rebuilding or enriching the Sales Order JSON.
- Performing SAP business validation.
- Performing customer, material, pricing, partner, or sales area validation.
- Enforcing idempotency through custom persistence.
- Persisting payloads outside JMS.
- Logging payload content to MPL.

## Replay Eligibility

A DLQ message is eligible for replay only when all required checks pass.

| Check | Rule | Ineligible Behavior |
| --- | --- | --- |
| Valid DLQ JSON envelope | Body must be a valid JSON object | Route to `REJECTED_REPLAY_SO_INBOUND` |
| `originalPayload` | Must exist and be non-empty | Route to `REJECTED_REPLAY_SO_INBOUND` |
| `correlationId` | Must exist and be non-empty | Route to `REJECTED_REPLAY_SO_INBOUND` |
| `replayApproved` | Must be `true` | Route to `REJECTED_REPLAY_SO_INBOUND` |
| `replayCount` | Must be less than `maxReplayCount` | Route to `REJECTED_REPLAY_SO_INBOUND` |
| `maxReplayCount` | Default is `1` when missing | Apply default |
| `errorCategory = SAP_AUTH_CONFIG_ERROR` | Never replay by default | Route to `REJECTED_REPLAY_SO_INBOUND` |
| `errorCategory = VALIDATION_ERROR` | Reject unless explicitly approved with `validationReplayApproved = true` | Route to `REJECTED_REPLAY_SO_INBOUND` |
| `errorCategory = SAP_BUSINESS_ERROR` | Replay only when `replayApproved = true` and `businessCorrectionConfirmed = true` | Route to `REJECTED_REPLAY_SO_INBOUND` |
| `consumerId` | Preserved when present; defaults to `UNKNOWN_CONSUMER` when empty | Continue |
| `idempotencyKey` | Preserved when present; may be empty | Continue |

Default replay policy:

| Field | Default |
| --- | --- |
| `maxReplayCount` | `1` |
| Missing `replayCount` | `0` |
| Missing `replayApproved` | `false` |

Messages without `replayApproved = true` must not be silently reprocessed. They are routed to `REJECTED_REPLAY_SO_INBOUND` with a replay rejection reason.

## Router Conditions

| Route | Condition |
| --- | --- |
| Eligible | `${property.replayEligible} = 'true'` |
| Rejected | `${property.replayEligible} != 'true'` |

`GS_ValidateDlqReplayEligibility` must not throw exceptions for replay ineligibility. It sets `replayEligible` and `replayRejectionReason`; the Router controls the next step.

`GS_ExtractOriginalPayloadFromDlq` must also avoid exceptions for replay extraction problems. If `originalPayload` is missing during extraction, it sets `replayEligible = false`, `replayRejectionCode = MISSING_ORIGINAL_PAYLOAD`, `replayTarget = REJECTED_REPLAY_SO_INBOUND`, and returns the message normally. A safety Router after extraction sends the message to the rejected replay path.

## Replay Metadata

For eligible replay, the flow adds the following metadata.

| Field | Value |
| --- | --- |
| `replayed` | `true` |
| `replayedAt` | Current UTC timestamp |
| `replaySource` | `DLQ_SO_INBOUND` |
| `replayTarget` | `JMS_SO_INBOUND` |
| `replayFlow` | `IFL_SO_REPROCESS_DLQ` |
| `replayCount` | Previous `replayCount + 1` |

## Replay Rejection Metadata

For rejected replay, the flow routes the original DLQ envelope to `REJECTED_REPLAY_SO_INBOUND` and adds:

| Field | Value |
| --- | --- |
| `replayRejected` | `true` |
| `replayRejectedAt` | Current UTC timestamp |
| `replayRejectionReason` | Reason set by validation or extraction script |
| `replayFlow` | `IFL_SO_REPROCESS_DLQ` |

## Message Handling

The inbound DLQ envelope is parsed only to validate replay eligibility and extract `originalPayload`.

Eligible route: the outbound message body to `JMS_SO_INBOUND` must be exactly the extracted `originalPayload` value. No canonical model is introduced, and no Sales Order field is modified.

Rejected route: the outbound message body to `REJECTED_REPLAY_SO_INBOUND` is a JSON replay rejection envelope that preserves the original DLQ envelope and adds replay rejection metadata.

Replay validation and extraction failures must never rely on the Exception Subprocess. They must be controlled through `replayEligible`, Router conditions, and the rejected replay route.

## JMS Configuration

| Adapter | Queue | Purpose |
| --- | --- | --- |
| JMS Sender | `DLQ_SO_INBOUND` | Consume reviewed DLQ envelopes during manual replay window |
| JMS Receiver | `JMS_SO_INBOUND` | Requeue original payload for normal orchestration processing |
| JMS Receiver | `REJECTED_REPLAY_SO_INBOUND` | Preserve messages pulled from DLQ but not eligible for replay |

## Logging And Monitoring

Success path logging uses operational metadata only:

- `correlationId`
- `consumerId`
- `idempotencyKey`
- `replayed`
- `replayedAt`
- `replaySource`
- `replayTarget`
- `replayFlow`
- `replayCount`

Rejected path logging uses operational metadata only:

- `correlationId`
- `consumerId`
- `idempotencyKey`
- `replayRejected`
- `replayRejectedAt`
- `replayRejectionReason`
- `replayFlow`

Payload logging is not performed. Trace mode may be used temporarily for troubleshooting under controlled operations procedures.

## Security And Architecture Guardrails

This replay iFlow remains aligned with the approved architecture:

```text
APIM
-> IFL_SO_INBOUND
-> JMS_SO_INBOUND
-> IFL_SO_ORCHESTRATION
-> SAP Standard Sales Order API
-> Callback
-> DLQ
```

The replay utility does not introduce:

- CAP
- PostgreSQL
- Event Mesh
- RFC
- BAPI
- Custom Z APIs
- Custom persistence

## Operational Runbook

1. Review the DLQ message in JMS monitoring.
2. Confirm the root cause has been resolved.
3. Confirm replay approval by setting `replayApproved = true` in the DLQ envelope copy prepared for replay.
4. Confirm `replayCount < maxReplayCount`; default `maxReplayCount` is `1`.
5. For `SAP_BUSINESS_ERROR`, confirm business or master data correction and set `businessCorrectionConfirmed = true`.
6. For `VALIDATION_ERROR`, explicitly approve only when replay is operationally safe by setting `validationReplayApproved = true`.
7. Review idempotency and duplicate-processing risk.
8. Start `IFL_SO_REPROCESS_DLQ` manually.
9. Monitor messages routed to `JMS_SO_INBOUND` and `REJECTED_REPLAY_SO_INBOUND`.
10. Confirm downstream processing in `IFL_SO_ORCHESTRATION` for eligible replayed messages.
11. Stop `IFL_SO_REPROCESS_DLQ` immediately after the controlled replay window.

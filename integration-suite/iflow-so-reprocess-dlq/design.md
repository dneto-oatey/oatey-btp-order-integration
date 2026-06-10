# IFL_SO_REPROCESS_DLQ Design

## Purpose

`IFL_SO_REPROCESS_DLQ` supports manual and ad hoc replay of reviewed Sales Order DLQ messages from `DLQ_SO_INBOUND` back to `JMS_SO_INBOUND`.

This iFlow is an operations-controlled replay utility only. It does not call SAP, does not perform SAP business validation, and does not modify the Sales Order payload.

## Operational Model

`IFL_SO_REPROCESS_DLQ` must remain Not Deployed by default.

Deploy it only during an approved replay window, then undeploy it immediately after replay completion.

Reason: if left active, it may create an operational loop:

```text
DLQ_SO_INBOUND
-> IFL_SO_REPROCESS_DLQ
-> JMS_SO_INBOUND
-> IFL_SO_ORCHESTRATION
-> DLQ_SO_INBOUND
```

This is a manual/ad hoc operational replay utility, not a continuously running runtime flow.

## Approved Runtime Flow

```text
JMS Sender DLQ_SO_INBOUND
-> GS_SetMplCustomHeaders or GS_LogBeforeJms when present
-> GS_ValidateDlqReplayEligibility
-> Router eligible vs rejected

Eligible:
  -> GS_ExtractOriginalPayloadFromDlq
  -> optional safety Router if replayEligible changed during extraction
     -> replayEligible = true:
        CM_PrepareRequeueToInbound
        GS_SetMplCustomHeaders or GS_LogBeforeJms when present
        JMS Receiver JMS_SO_INBOUND
     -> otherwise:
        GS_PrepareReplayRejectedPayload
        CM_SetReplayRejectedHeaders
        GS_SetMplCustomHeaders or GS_LogBeforeJms when present
        JMS Receiver REJECTED_REPLAY_SO_INBOUND

Rejected:
  -> GS_PrepareReplayRejectedPayload
  -> CM_SetReplayRejectedHeaders
  -> GS_SetMplCustomHeaders or GS_LogBeforeJms when present
  -> JMS Receiver REJECTED_REPLAY_SO_INBOUND

-> End
```

## Router Conditions

Router condition must use Simple Expression, not XPath.

| Route | CPI Router Setting |
| --- | --- |
| Eligible | Simple Expression `${property.replayEligible} == 'true'` |
| Rejected | Default / Otherwise |

`GS_ValidateDlqReplayEligibility` must not throw exceptions for replay ineligibility. It sets `replayEligible` and `replayRejectionReason`; the Router controls the next step.

`GS_ExtractOriginalPayloadFromDlq` must also avoid exceptions for replay extraction problems. If `originalPayload` is missing during extraction, it sets `replayEligible = false`, `replayRejectionCode = MISSING_ORIGINAL_PAYLOAD`, `replayTarget = REJECTED_REPLAY_SO_INBOUND`, and returns the message normally. A safety Router after extraction sends the message to the rejected replay path.

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

## Replay Governance

`replayCount` and `maxReplayCount` are DLQ envelope metadata. Do not rely on JMS technical redelivery count for business replay governance.

`IFL_SO_REPROCESS_DLQ` increments `replayCount` when publishing back to `JMS_SO_INBOUND`. If the message fails again, `IFL_SO_ORCHESTRATION` preserves that `replayCount` in the new DLQ envelope.

## Rejected Replay Payload

Messages not eligible for replay must go to `REJECTED_REPLAY_SO_INBOUND`, not be lost and not be sent back to `JMS_SO_INBOUND`.

Rejected payload includes:

- `replayRejected`
- `replayRejectedAt`
- `replayRejectionCode`
- `replayRejectionReason`
- `replayFlow`
- `replaySource`
- `replayTarget`
- `correlationId`
- `consumerId`
- `idempotencyKey`
- `replayCount`
- `maxReplayCount`
- `errorCategory`
- `originalDlqEnvelope`

## MPL Custom Headers

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

`GS_LogBeforeJms` or `GS_SetMplCustomHeaders`, when present, must only add custom header properties when values exist. Empty values must not be written because MPL appends values and does not overwrite prior custom header values.

## JMS Configuration

| Adapter | Queue | Purpose |
| --- | --- | --- |
| JMS Sender | `DLQ_SO_INBOUND` | Consume reviewed DLQ envelopes during manual replay deployment window |
| JMS Receiver | `JMS_SO_INBOUND` | Requeue original payload for normal orchestration processing |
| JMS Receiver | `REJECTED_REPLAY_SO_INBOUND` | Preserve messages pulled from DLQ but not eligible for replay |

## Logging And Monitoring

Payload logging is not performed. Trace mode may be used temporarily for troubleshooting under controlled operations procedures.

## Security And Architecture Guardrails

The replay utility does not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, custom persistence, or direct SAP calls. It does not modify the business payload during replay.

## Operational Runbook

1. Review the DLQ message in JMS monitoring.
2. Confirm the root cause has been resolved.
3. Confirm replay approval by setting `replayApproved = true` in the DLQ envelope copy prepared for replay.
4. Confirm `replayCount < maxReplayCount`; default `maxReplayCount` is `1`.
5. For `SAP_BUSINESS_ERROR`, confirm business or master data correction and set `businessCorrectionConfirmed = true`.
6. For `VALIDATION_ERROR`, explicitly approve only when replay is operationally safe by setting `validationReplayApproved = true`.
7. Review idempotency and duplicate-processing risk.
8. Deploy `IFL_SO_REPROCESS_DLQ` for the approved replay window.
9. Monitor messages routed to `JMS_SO_INBOUND` and `REJECTED_REPLAY_SO_INBOUND`.
10. Confirm downstream processing in `IFL_SO_ORCHESTRATION` for eligible replayed messages.
11. Undeploy `IFL_SO_REPROCESS_DLQ` immediately after replay completion.

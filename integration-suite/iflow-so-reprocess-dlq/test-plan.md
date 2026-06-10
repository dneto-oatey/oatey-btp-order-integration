# IFL_SO_REPROCESS_DLQ Test Plan

## Scope

This test plan validates operations-controlled replay of reviewed DLQ Sales Order messages from `DLQ_SO_INBOUND` to either `JMS_SO_INBOUND` or `REJECTED_REPLAY_SO_INBOUND`.

The iFlow must not call SAP directly and must not modify the Sales Order payload.

## Preconditions

- `IFL_SO_REPROCESS_DLQ` is Not Deployed by default.
- Deploy only during an approved replay window.
- Undeploy immediately after replay completion.
- A reviewed DLQ envelope exists in `DLQ_SO_INBOUND`.
- Operations has confirmed root cause resolution where applicable.
- Operations has completed idempotency and duplicate-processing review.
- Payload logging is disabled.
- Replay approval is represented through DLQ envelope control fields.

## Runtime Flow Under Test

```text
JMS Sender DLQ_SO_INBOUND
-> GS_SetMplCustomHeaders or GS_LogBeforeJms when present
-> GS_ValidateDlqReplayEligibility
-> Router eligible vs rejected
Eligible:
  -> GS_ExtractOriginalPayloadFromDlq
  -> optional safety Router if replayEligible changed during extraction
  -> CM_PrepareRequeueToInbound
  -> GS_SetMplCustomHeaders or GS_LogBeforeJms when present
  -> JMS Receiver JMS_SO_INBOUND
Rejected:
  -> GS_PrepareReplayRejectedPayload
  -> CM_SetReplayRejectedHeaders
  -> GS_SetMplCustomHeaders or GS_LogBeforeJms when present
  -> JMS Receiver REJECTED_REPLAY_SO_INBOUND
```

Router condition must use Simple Expression `${property.replayEligible} == 'true'`. Rejected route is Default / Otherwise.

## Validated Results

| Scenario | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- |
| Reprocess consumes `DLQ_SO_INBOUND` | DLQ message consumed during approved replay window | Working | PASS |
| Missing or false `replayApproved` | Message routed to `REJECTED_REPLAY_SO_INBOUND` | Working | PASS |
| Missing `originalPayload` | Message routed to `REJECTED_REPLAY_SO_INBOUND` | Working | PASS |
| Replay governance headers | `replayCount` and `maxReplayCount` visible in MPL custom headers when populated | Working | PASS |
| Payload logging | No payload logged to MPL custom headers | Working | PASS |

## Test Scenarios

| # | Scenario | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- | --- |
| 1 | iFlow default state | iFlow is Not Deployed by default | TBD | TBD |
| 2 | Deploy for replay window | iFlow consumes DLQ messages only during approved deployment window | TBD | TBD |
| 3 | Undeploy after replay | iFlow is undeployed immediately after replay completion | TBD | TBD |
| 4 | `replayApproved = true` valid message | `originalPayload` is published to `JMS_SO_INBOUND`; `replayCount` increments by 1 | TBD | TBD |
| 5 | `replayApproved` missing | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | PASS | PASS |
| 6 | `replayApproved = false` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | PASS | PASS |
| 7 | `replayCount >= maxReplayCount` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 8 | Missing `originalPayload` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | PASS | PASS |
| 9 | Missing `correlationId` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 10 | Invalid DLQ JSON envelope | Message is routed to `REJECTED_REPLAY_SO_INBOUND` as replay rejection JSON | TBD | TBD |
| 11 | `SAP_AUTH_CONFIG_ERROR` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 12 | `VALIDATION_ERROR` without `validationReplayApproved = true` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 13 | `SAP_BUSINESS_ERROR` with `businessCorrectionConfirmed = false` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 14 | `SAP_BUSINESS_ERROR` with `businessCorrectionConfirmed = true` and `replayApproved = true` | Message is published to `JMS_SO_INBOUND` | TBD | TBD |
| 15 | Missing `consumerId` on otherwise eligible replay | Message is published to `JMS_SO_INBOUND` with `UNKNOWN_CONSUMER` | TBD | TBD |
| 16 | Missing `idempotencyKey` on otherwise eligible replay | Message is published to `JMS_SO_INBOUND` with empty `idempotencyKey` | TBD | TBD |
| 17 | Payload mutation check | Requeued body matches `originalPayload` exactly | TBD | TBD |
| 18 | Rejected payload preservation check | Rejection body preserves original DLQ envelope and adds rejection metadata | TBD | TBD |
| 19 | No SAP call check | No SAP HTTP/OData/RFC/BAPI call is performed by this iFlow | TBD | TBD |
| 20 | No payload MPL logging | Replay and rejection paths log metadata only, not payload | PASS | PASS |

## Successful Replay Validation Points

For successful replay, confirm:

- Message appears on `JMS_SO_INBOUND`.
- Outbound body equals `originalPayload` exactly.
- `correlationId` is preserved.
- `consumerId` is preserved or set to `UNKNOWN_CONSUMER`.
- `idempotencyKey` is preserved or empty.
- `replayCount` is previous value plus 1.
- `maxReplayCount` is preserved.
- Replay metadata is present.

## Rejected Replay Validation Points

For rejected replay, confirm:

- Message is not published to `JMS_SO_INBOUND`.
- Message is published to `REJECTED_REPLAY_SO_INBOUND`.
- Rejection body is JSON.
- Rejection body preserves the original DLQ envelope.
- Rejection metadata is present:
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
- Payload content is not logged to MPL.

## Completion Criteria

Testing is complete when:

- Eligible reviewed DLQ envelope replays successfully.
- Ineligible DLQ envelopes are routed to `REJECTED_REPLAY_SO_INBOUND`.
- No Sales Order payload mutation occurs.
- No SAP call occurs.
- The iFlow remains undeployed outside approved replay windows.

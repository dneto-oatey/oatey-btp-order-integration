# IFL_SO_REPROCESS_DLQ Test Plan

## Scope

This test plan validates operations-controlled replay of reviewed DLQ Sales Order messages from `DLQ_SO_INBOUND` to either `JMS_SO_INBOUND` or `REJECTED_REPLAY_SO_INBOUND`.

The iFlow must not call SAP directly and must not modify the Sales Order payload.

## Preconditions

- `IFL_SO_REPROCESS_DLQ` is deployed and stopped by default.
- A reviewed DLQ envelope exists in `DLQ_SO_INBOUND`.
- Operations has confirmed root cause resolution where applicable.
- Operations has completed idempotency and duplicate-processing review.
- Payload logging is disabled.
- Replay approval is represented through DLQ envelope control fields.

## Test Scenarios

| # | Scenario | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- | --- |
| 1 | iFlow deployed after build | iFlow remains stopped by default | TBD | TBD |
| 2 | Manual start for replay window | iFlow consumes DLQ messages only during controlled manual run | TBD | TBD |
| 3 | `replayApproved = true` valid message | `originalPayload` is published to `JMS_SO_INBOUND`; `replayCount` increments by 1 | TBD | TBD |
| 4 | `replayApproved` missing | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 5 | `replayApproved = false` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 6 | `replayCount >= maxReplayCount` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 7 | Missing `originalPayload` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 8 | Missing `correlationId` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 9 | Invalid DLQ JSON envelope | Message is routed to `REJECTED_REPLAY_SO_INBOUND` as replay rejection JSON | TBD | TBD |
| 10 | `SAP_AUTH_CONFIG_ERROR` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 11 | `VALIDATION_ERROR` without `validationReplayApproved = true` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 12 | `SAP_BUSINESS_ERROR` with `businessCorrectionConfirmed = false` | Message is routed to `REJECTED_REPLAY_SO_INBOUND` | TBD | TBD |
| 13 | `SAP_BUSINESS_ERROR` with `businessCorrectionConfirmed = true` and `replayApproved = true` | Message is published to `JMS_SO_INBOUND` | TBD | TBD |
| 14 | Missing `consumerId` on otherwise eligible replay | Message is published to `JMS_SO_INBOUND` with `UNKNOWN_CONSUMER` | TBD | TBD |
| 15 | Missing `idempotencyKey` on otherwise eligible replay | Message is published to `JMS_SO_INBOUND` with empty `idempotencyKey` | TBD | TBD |
| 16 | Payload mutation check | Requeued body matches `originalPayload` exactly | TBD | TBD |
| 17 | Rejected payload preservation check | Rejection body preserves original DLQ envelope and adds rejection metadata | TBD | TBD |
| 18 | No SAP call check | No SAP HTTP/OData/RFC/BAPI call is performed by this iFlow | TBD | TBD |
| 19 | No payload MPL logging | Replay and rejection paths log metadata only, not payload | TBD | TBD |
| 20 | Stop after replay window | iFlow is stopped immediately after approved replay processing | TBD | TBD |

## Successful Replay Validation Points

For successful replay, confirm:

- Message is removed from `DLQ_SO_INBOUND` by the manual replay run.
- Message appears on `JMS_SO_INBOUND`.
- Outbound body equals `originalPayload` exactly.
- `correlationId` is preserved.
- `consumerId` is preserved or set to `UNKNOWN_CONSUMER`.
- `idempotencyKey` is preserved or empty.
- `replayCount` is previous value plus 1.
- Replay metadata is present:
  - `replayed = true`
  - `replayedAt`
  - `replaySource = DLQ_SO_INBOUND`
  - `replayTarget = JMS_SO_INBOUND`
  - `replayFlow = IFL_SO_REPROCESS_DLQ`

## Rejected Replay Validation Points

For rejected replay, confirm:

- Message is not published to `JMS_SO_INBOUND`.
- Message is published to `REJECTED_REPLAY_SO_INBOUND`.
- Rejection body is JSON.
- Rejection body preserves the original DLQ envelope.
- Rejection metadata is present:
  - `replayRejected = true`
  - `replayRejectedAt`
  - `replayRejectionReason`
  - `replayFlow = IFL_SO_REPROCESS_DLQ`
- Payload content is not logged to MPL.
- Operations can identify the rejection reason using metadata only.

## Completion Criteria

Testing is complete when:

- Eligible reviewed DLQ envelope replays successfully.
- Ineligible DLQ envelopes are routed to `REJECTED_REPLAY_SO_INBOUND`.
- No Sales Order payload mutation occurs.
- No SAP call occurs.
- The iFlow remains stopped outside approved replay windows.

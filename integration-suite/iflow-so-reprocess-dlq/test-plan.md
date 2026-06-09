# IFL_SO_REPROCESS_DLQ Test Plan

## Scope

This test plan validates manual replay of reviewed DLQ Sales Order messages from `DLQ_SO_INBOUND` to `JMS_SO_INBOUND`.

The iFlow must not call SAP directly and must not modify the Sales Order payload.

## Preconditions

- `IFL_SO_REPROCESS_DLQ` is deployed and stopped by default.
- A reviewed DLQ envelope exists in `DLQ_SO_INBOUND`.
- Operations has confirmed root cause resolution.
- Operations has completed idempotency and duplicate-processing review.
- Payload logging is disabled.

## Test Scenarios

| # | Scenario | Expected Result | Actual Result | Pass/Fail |
| --- | --- | --- | --- | --- |
| 1 | Valid reviewed DLQ envelope | `originalPayload` is published to `JMS_SO_INBOUND`; replay metadata is attached | TBD | TBD |
| 2 | iFlow deployed after build | iFlow remains stopped by default | TBD | TBD |
| 3 | Manual start for replay window | iFlow consumes DLQ message only during controlled manual run | TBD | TBD |
| 4 | Missing `originalPayload` | Replay is rejected with `REPLAY_VALIDATION_ERROR` | TBD | TBD |
| 5 | Empty `originalPayload` | Replay is rejected with `REPLAY_VALIDATION_ERROR` | TBD | TBD |
| 6 | Invalid DLQ JSON envelope | Replay is rejected with `REPLAY_VALIDATION_ERROR` | TBD | TBD |
| 7 | Missing `correlationId` | Replay is rejected because operational traceability is required | TBD | TBD |
| 8 | Missing `consumerId` | Replay continues with `UNKNOWN_CONSUMER` fallback | TBD | TBD |
| 9 | Missing `idempotencyKey` | Replay continues with empty `idempotencyKey`; operations review remains required | TBD | TBD |
| 10 | `replayRequired = false` | Replay is rejected | TBD | TBD |
| 11 | `replayed = true` | Replay is rejected to prevent accidental repeated replay | TBD | TBD |
| 12 | Valid envelope with SAP business error context | Replay republishes original payload only; error context is not sent as body | TBD | TBD |
| 13 | Payload mutation check | Requeued body matches `originalPayload` exactly | TBD | TBD |
| 14 | No SAP call check | No SAP HTTP/OData/RFC/BAPI call is performed by this iFlow | TBD | TBD |
| 15 | No payload MPL logging | Successful replay logs metadata only, not payload | TBD | TBD |

## Validation Points

For successful replay, confirm:

- Message is removed from `DLQ_SO_INBOUND` by the manual replay run.
- Message appears on `JMS_SO_INBOUND`.
- Outbound body equals `originalPayload`.
- `correlationId` is preserved.
- `consumerId` is preserved or set to `UNKNOWN_CONSUMER`.
- `idempotencyKey` is preserved or empty.
- Replay metadata is present:
  - `replayed = true`
  - `replayedAt`
  - `replaySource = DLQ_SO_INBOUND`
  - `replayTarget = JMS_SO_INBOUND`
  - `replayFlow = IFL_SO_REPROCESS_DLQ`

## Negative Test Validation

For rejected replay, confirm:

- Message is not published to `JMS_SO_INBOUND`.
- Controlled error properties are set.
- Payload content is not logged to MPL.
- Operations can identify the rejection reason using metadata only.

## Completion Criteria

Testing is complete when:

- Valid reviewed DLQ envelope replays successfully.
- Invalid or ineligible DLQ envelopes are rejected.
- No Sales Order payload mutation occurs.
- No SAP call occurs.
- The iFlow remains stopped outside approved replay windows.

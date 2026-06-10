# REJECTED_REPLAY_SO_INBOUND

Purpose: operational parking queue for DLQ messages consumed by `IFL_SO_REPROCESS_DLQ` but rejected by replay eligibility rules.

This queue does not replace `DLQ_SO_INBOUND`.

## Queue Responsibility

`REJECTED_REPLAY_SO_INBOUND` stores messages that were pulled from `DLQ_SO_INBOUND` during an approved manual replay window but were not eligible to be replayed back to `JMS_SO_INBOUND`.

Only the rejected route of `IFL_SO_REPROCESS_DLQ` publishes to this queue.

## Producers And Consumers

| Role | Component |
| --- | --- |
| Producer | `IFL_SO_REPROCESS_DLQ` rejected route only |
| Consumer | Operations review / manual investigation |
| Not a producer | `IFL_SO_INBOUND` |
| Not a producer | `IFL_SO_ORCHESTRATION` normal failure path |

Normal orchestration failures must continue to route to `DLQ_SO_INBOUND`, not directly to `REJECTED_REPLAY_SO_INBOUND`.

## Rejection Conditions

Messages are routed to `REJECTED_REPLAY_SO_INBOUND` when replay eligibility fails, including:

- `replayApproved` missing or not `true`
- `originalPayload` missing or empty
- `correlationId` missing or empty
- `replayCount >= maxReplayCount`
- `SAP_AUTH_CONFIG_ERROR`
- `VALIDATION_ERROR` without `validationReplayApproved = true`
- `SAP_BUSINESS_ERROR` without `businessCorrectionConfirmed = true`
- Extraction failure in `GS_ExtractOriginalPayloadFromDlq`

## Message Body

The message body is a replay rejection JSON envelope created by `GS_PrepareReplayRejectedPayload`.

Required fields:

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

## Operational Handling

Operations should review this queue separately from `DLQ_SO_INBOUND`.

A message in `REJECTED_REPLAY_SO_INBOUND` means replay was intentionally blocked by governance rules. It should not be blindly moved back to `JMS_SO_INBOUND`.

Before any future replay attempt, operations must validate root cause, idempotency risk, replay approval, business/master data correction where applicable, and replay count limits.

## Logging

Payload logging to MPL is not allowed. Use queue monitoring, correlationId, replayRejectionCode, and replayRejectionReason for operational triage.

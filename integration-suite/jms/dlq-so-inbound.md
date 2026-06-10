# DLQ_SO_INBOUND

Purpose: hold messages that cannot be processed by `IFL_SO_ORCHESTRATION` after retry exhaustion or non-recoverable failure classification.

`DLQ_SO_INBOUND` remains the primary dead-letter queue for orchestration failures. It is not replaced by `REJECTED_REPLAY_SO_INBOUND`.

## Producer And Consumer

| Role | Component |
| --- | --- |
| Producer | `IFL_SO_ORCHESTRATION` exception path |
| Consumer | Operations review; optional manual replay through `IFL_SO_REPROCESS_DLQ` |

Normal processing failures must route to `DLQ_SO_INBOUND`, not directly to `REJECTED_REPLAY_SO_INBOUND`.

## Required DLQ Data

DLQ envelope should include:

- `sourceIFlow`
- `sourceQueue`
- `targetQueue`
- `correlationId`
- `consumerId`
- `idempotencyKey`
- `processingStatus`
- `failureTimestamp`
- `errorCategory`
- `errorCode`
- `errorMessage`
- `sapResponseStatusCode`
- `sapErrorCode`
- `sapErrorMessage`
- `retryAttempt`
- `maxRetryCount`
- `replayRequired`
- `replayInstruction`
- `replayCount`
- `maxReplayCount`
- `originalPayload`

## Relationship To REJECTED_REPLAY_SO_INBOUND

`REJECTED_REPLAY_SO_INBOUND` is only for messages already consumed from `DLQ_SO_INBOUND` by `IFL_SO_REPROCESS_DLQ` and rejected by replay eligibility rules.

A message should move from `DLQ_SO_INBOUND` to `REJECTED_REPLAY_SO_INBOUND` only through the rejected route of `IFL_SO_REPROCESS_DLQ`.

## Operations

DLQ depth greater than zero triggers alerting. Replay requires root cause review, idempotency validation, replay approval, and replay governance checks.

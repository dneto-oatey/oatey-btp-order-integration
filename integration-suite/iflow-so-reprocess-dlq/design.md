# IFL_SO_REPROCESS_DLQ Design

## Purpose

`IFL_SO_REPROCESS_DLQ` supports manual and ad hoc replay of reviewed Sales Order DLQ messages from `DLQ_SO_INBOUND` back to `JMS_SO_INBOUND`.

This iFlow is an operational replay utility only. It does not call SAP, does not perform SAP business validation, and does not modify the Sales Order payload.

## Approved Runtime Flow

```text
JMS Sender DLQ_SO_INBOUND
-> GS_ValidateDlqReplayEligibility
-> GS_ExtractOriginalPayloadFromDlq
-> CM_PrepareRequeueToInbound
-> JMS Receiver JMS_SO_INBOUND
-> End
```

## Operational Mode

| Area | Decision |
| --- | --- |
| Default state | Stopped |
| Execution mode | Manual start only |
| Trigger | Operations review of DLQ message and replay approval |
| Runtime window | Start for controlled replay window, then stop again |
| Replay source | `DLQ_SO_INBOUND` |
| Replay target | `JMS_SO_INBOUND` |

## Responsibilities

`IFL_SO_REPROCESS_DLQ` is responsible for:

- Consuming reviewed DLQ envelopes from `DLQ_SO_INBOUND`.
- Validating that the DLQ message is eligible for replay.
- Extracting `originalPayload` from the DLQ envelope.
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

A DLQ message is eligible for replay when all required checks pass.

| Check | Rule | Failure Behavior |
| --- | --- | --- |
| Valid DLQ JSON envelope | Body must be valid JSON object | Reject replay |
| `originalPayload` | Must exist and be non-empty | Reject replay |
| `correlationId` | Must exist and be non-empty | Reject replay |
| `consumerId` | Preserved when present; defaults to `UNKNOWN_CONSUMER` when empty | Continue |
| `idempotencyKey` | Preserved when present; may be empty | Continue |
| `replayRequired` | If present and `false`, reject replay | Reject replay |
| Already replayed | If envelope has `replayed = true`, reject replay | Reject replay |

Idempotency review is an operational prerequisite before manually starting this iFlow. Missing `idempotencyKey` alone is not a replay rejection reason, but operations must review duplicate risk before replay.

## Replay Metadata

The replay flow adds the following metadata.

| Field | Value |
| --- | --- |
| `replayed` | `true` |
| `replayedAt` | Current UTC timestamp |
| `replaySource` | `DLQ_SO_INBOUND` |
| `replayTarget` | `JMS_SO_INBOUND` |
| `replayFlow` | `IFL_SO_REPROCESS_DLQ` |

## Message Handling

The inbound DLQ envelope is parsed only to validate replay eligibility and extract `originalPayload`.

The outbound message body to `JMS_SO_INBOUND` must be exactly the extracted `originalPayload` value. No canonical model is introduced, and no Sales Order field is modified.

## JMS Configuration

| Adapter | Queue | Purpose |
| --- | --- | --- |
| JMS Sender | `DLQ_SO_INBOUND` | Consume reviewed DLQ envelopes |
| JMS Receiver | `JMS_SO_INBOUND` | Requeue original payload for normal orchestration processing |

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
3. Review idempotency and duplicate-processing risk.
4. Confirm replay is approved by operations.
5. Start `IFL_SO_REPROCESS_DLQ` manually.
6. Monitor messages leaving `DLQ_SO_INBOUND` and arriving on `JMS_SO_INBOUND`.
7. Confirm downstream processing in `IFL_SO_ORCHESTRATION`.
8. Stop `IFL_SO_REPROCESS_DLQ` after the controlled replay window.

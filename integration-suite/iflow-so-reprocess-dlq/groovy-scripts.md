# IFL_SO_REPROCESS_DLQ Groovy Scripts

## Scope

This document describes the Groovy scripts used by `IFL_SO_REPROCESS_DLQ`.

Generated script files:

| Script | File |
| --- | --- |
| `GS_ValidateDlqReplayEligibility` | `integration-suite/iflow-so-reprocess-dlq/scripts/GS_ValidateDlqReplayEligibility.groovy` |
| `GS_ExtractOriginalPayloadFromDlq` | `integration-suite/iflow-so-reprocess-dlq/scripts/GS_ExtractOriginalPayloadFromDlq.groovy` |
| `GS_PrepareReplayRejectedPayload` | `integration-suite/iflow-so-reprocess-dlq/scripts/GS_PrepareReplayRejectedPayload.groovy` |

All scripts must be SAP Integration Suite compatible and use:

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message)
```

Where JSON parsing is required, scripts must use streaming-compatible parsing from a `Reader`. Payload content must not be logged to MPL.

## GS_ValidateDlqReplayEligibility

### Purpose

Parse the DLQ envelope, evaluate replay control fields, and set `replayEligible = true` or `false` for the Router.

This script must not throw exceptions for replay ineligibility. Ineligible replay must be routed to `REJECTED_REPLAY_SO_INBOUND`.

### Input

Message body from `DLQ_SO_INBOUND`, expected to be a DLQ JSON envelope.

Required for eligible replay:

- `correlationId`
- `originalPayload`
- `replayApproved = true`
- `replayCount < maxReplayCount`

Optional fields:

- `consumerId`
- `idempotencyKey`
- `maxReplayCount`
- `errorCategory`
- `businessCorrectionConfirmed`
- `validationReplayApproved`

### Output

Sets Exchange Properties:

| Property | Description |
| --- | --- |
| `replayEligible` | `true` or `false` |
| `replayRejectionReason` | Required when ineligible |
| `correlationId` | Preserved correlation identifier when available |
| `consumerId` | Preserved consumer ID or `UNKNOWN_CONSUMER` fallback |
| `idempotencyKey` | Preserved idempotency key or empty string |
| `originalPayload` | Original Sales Order JSON payload when available |
| `originalDlqEnvelope` | Original consumed DLQ envelope text |
| `replayApproved` | `true` or `false`; missing is `false` |
| `replayCount` | Existing count; missing is `0` |
| `maxReplayCount` | Existing limit; missing is `1` |
| `errorCategory` | Preserved error category when available |
| `replaySource` | `DLQ_SO_INBOUND` |
| `replayTarget` | `JMS_SO_INBOUND` when eligible; `REJECTED_REPLAY_SO_INBOUND` when rejected |
| `replayFlow` | `IFL_SO_REPROCESS_DLQ` |

### Validation Rules

| Rule | Behavior |
| --- | --- |
| DLQ body is not valid JSON | `replayEligible = false` |
| DLQ body is not a JSON object | `replayEligible = false` |
| `originalPayload` missing or empty | `replayEligible = false` |
| `correlationId` missing or empty | `replayEligible = false` |
| `replayApproved` missing | Treat as `false`; `replayEligible = false` |
| `replayApproved != true` | `replayEligible = false` |
| `replayCount` missing | Treat as `0` |
| `maxReplayCount` missing | Treat as `1` |
| `replayCount >= maxReplayCount` | `replayEligible = false` |
| `SAP_AUTH_CONFIG_ERROR` | `replayEligible = false` |
| `VALIDATION_ERROR` | `replayEligible = false` unless `validationReplayApproved = true` |
| `SAP_BUSINESS_ERROR` | Eligible only when `businessCorrectionConfirmed = true` and `replayApproved = true` |
| `consumerId` missing or empty | Set `UNKNOWN_CONSUMER` |
| `idempotencyKey` missing or empty | Preserve as empty string |

## GS_ExtractOriginalPayloadFromDlq

### Purpose

Extract `originalPayload` and make it the outbound JMS message body for `JMS_SO_INBOUND`.

### Input

Exchange Properties produced by `GS_ValidateDlqReplayEligibility`.

Primary input:

- `originalPayload`

Traceability input:

- `correlationId`
- `consumerId`
- `idempotencyKey`
- `replayCount`

### Output

- Message body is set to `originalPayload` only.
- Message headers are set for replay metadata and traceability.
- `replayCount` is incremented by 1.
- Exchange Property `processingStatus` is set to `ORIGINAL_PAYLOAD_EXTRACTED`.

### Failure Behavior

This script is expected to run only on the eligible route. If `originalPayload` is unavailable, the script sets controlled error properties and throws a replay extraction exception.

## GS_PrepareReplayRejectedPayload

### Purpose

Build a replay rejection JSON envelope for messages pulled from `DLQ_SO_INBOUND` but not eligible for replay.

### Input

Exchange Properties produced by `GS_ValidateDlqReplayEligibility`.

Primary input:

- `originalDlqEnvelope`
- `replayRejectionReason`
- `correlationId`
- `consumerId`
- `idempotencyKey`
- `replayCount`
- `maxReplayCount`
- `errorCategory`

### Output Body

JSON body containing:

- `replayRejected = true`
- `replayRejectedAt`
- `replayRejectionReason`
- `replayFlow = IFL_SO_REPROCESS_DLQ`
- `replaySource = DLQ_SO_INBOUND`
- `replayTarget = REJECTED_REPLAY_SO_INBOUND`
- `correlationId`
- `consumerId`
- `idempotencyKey`
- `replayCount`
- `maxReplayCount`
- `errorCategory`
- `originalDlqEnvelope`

### Logging Rule

The script must not write the payload or the original DLQ envelope to MPL custom properties or logs.

## Upgrade Readiness

The scripts avoid deprecated full-body parsing patterns such as:

```groovy
new JsonSlurper().parseText(message.getBody(String))
```

JSON parsing uses `java.io.Reader` and `StringReader` where a preserved copy of the consumed envelope is required.

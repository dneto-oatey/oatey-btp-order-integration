# IFL_SO_REPROCESS_DLQ Groovy Scripts

## Scope

This document describes the Groovy scripts used by `IFL_SO_REPROCESS_DLQ`.

Generated script files:

| Script | File |
| --- | --- |
| `GS_ValidateDlqReplayEligibility` | `integration-suite/iflow-so-reprocess-dlq/scripts/GS_ValidateDlqReplayEligibility.groovy` |
| `GS_ExtractOriginalPayloadFromDlq` | `integration-suite/iflow-so-reprocess-dlq/scripts/GS_ExtractOriginalPayloadFromDlq.groovy` |

All scripts must be SAP Integration Suite compatible and use:

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message)
```

Where JSON parsing is required, scripts must use streaming-compatible parsing:

```groovy
Reader reader = message.getBody(java.io.Reader)
def json = new JsonSlurper().parse(reader)
```

Payload content must not be logged to MPL.

## GS_ValidateDlqReplayEligibility

### Purpose

Validate that the consumed DLQ envelope is eligible for manual replay.

### Input

Message body from `DLQ_SO_INBOUND`, expected to be a DLQ JSON envelope containing at minimum:

- `correlationId`
- `originalPayload`

Optional fields:

- `consumerId`
- `idempotencyKey`
- `replayRequired`
- `replayed`
- `errorCategory`
- `errorCode`
- `errorMessage`

### Output

Sets Exchange Properties:

| Property | Description |
| --- | --- |
| `correlationId` | Preserved correlation identifier |
| `consumerId` | Preserved consumer ID or `UNKNOWN_CONSUMER` fallback |
| `idempotencyKey` | Preserved idempotency key or empty string |
| `originalPayload` | Original Sales Order JSON payload extracted from DLQ envelope |
| `replayed` | `true` |
| `replayedAt` | Current UTC timestamp |
| `replaySource` | `DLQ_SO_INBOUND` |
| `replayTarget` | `JMS_SO_INBOUND` |
| `replayFlow` | `IFL_SO_REPROCESS_DLQ` |
| `replayValidationStatus` | `SUCCESS` |

### Validation Rules

| Rule | Behavior |
| --- | --- |
| DLQ body is not valid JSON | Throw controlled replay validation exception |
| DLQ body is not a JSON object | Throw controlled replay validation exception |
| `originalPayload` missing or empty | Throw controlled replay validation exception |
| `correlationId` missing or empty | Throw controlled replay validation exception |
| `consumerId` missing or empty | Set `UNKNOWN_CONSUMER` |
| `idempotencyKey` missing or empty | Preserve as empty string |
| `replayRequired = false` | Throw controlled replay validation exception |
| `replayed = true` | Throw controlled replay validation exception |

### Failure Behavior

Sets controlled error properties before throwing:

| Property | Value |
| --- | --- |
| `errorCategory` | `REPLAY_VALIDATION_ERROR` |
| `errorCode` | Script-specific validation code |
| `errorMessage` | Safe validation message without payload content |
| `processingStatus` | `REPLAY_REJECTED` |

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
- `replayed`
- `replayedAt`
- `replaySource`
- `replayTarget`
- `replayFlow`

### Output

- Message body is set to `originalPayload` only.
- Message headers are set for replay metadata and traceability.
- Exchange Property `processingStatus` is set to `ORIGINAL_PAYLOAD_EXTRACTED`.

### Failure Behavior

If `originalPayload` is unavailable, the script sets controlled error properties and throws a replay extraction exception.

| Property | Value |
| --- | --- |
| `errorCategory` | `REPLAY_EXTRACTION_ERROR` |
| `errorCode` | `MISSING_ORIGINAL_PAYLOAD` |
| `processingStatus` | `REPLAY_REJECTED` |

## Upgrade Readiness

The scripts avoid deprecated full-body parsing patterns such as:

```groovy
new JsonSlurper().parseText(message.getBody(String))
```

JSON parsing uses `java.io.Reader` when needed.

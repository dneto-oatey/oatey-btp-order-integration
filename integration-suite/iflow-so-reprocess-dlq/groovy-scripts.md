# IFL_SO_REPROCESS_DLQ Groovy Scripts

## Scope

This document describes the Groovy scripts used by `IFL_SO_REPROCESS_DLQ`.

Generated script files:

| Script | File |
| --- | --- |
| `GS_ValidateDlqReplayEligibility` | `integration-suite/iflow-so-reprocess-dlq/scripts/GS_ValidateDlqReplayEligibility.groovy` |
| `GS_ExtractOriginalPayloadFromDlq` | `integration-suite/iflow-so-reprocess-dlq/scripts/GS_ExtractOriginalPayloadFromDlq.groovy` |
| `GS_PrepareReplayRejectedPayload` | `integration-suite/iflow-so-reprocess-dlq/scripts/GS_PrepareReplayRejectedPayload.groovy` |

Where JSON parsing is required, scripts must use streaming-compatible parsing from a `Reader`. Payload content must not be logged to MPL.

## Router And Error Handling Principle

Replay validation and replay extraction problems must not rely on the Exception Subprocess.

Scripts return the message normally and set routing properties:

| Property | Value |
| --- | --- |
| `replayEligible` | `true` or `false` |
| `replayRejectionCode` | Controlled reason code when rejected |
| `replayRejectionReason` | Meaningful operational reason when rejected |
| `replayTarget` | `JMS_SO_INBOUND` or `REJECTED_REPLAY_SO_INBOUND` |
| `processingStatus` | Current replay status |

Router condition must use Simple Expression, not XPath:

- Eligible: `${property.replayEligible} == 'true'`
- Rejected: Default / Otherwise

## GS_ValidateDlqReplayEligibility

### Purpose

Parse the DLQ envelope, evaluate replay control fields, and set `replayEligible = true` or `false` for the Router.

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

When extraction succeeds:

- Message body is set to `originalPayload` only.
- Message headers are set for replay metadata and traceability.
- `replayCount` is incremented by 1.
- `processingStatus` is set to `ORIGINAL_PAYLOAD_EXTRACTED`.

When `originalPayload` is missing:

| Property | Value |
| --- | --- |
| `replayEligible` | `false` |
| `replayRejectionCode` | `MISSING_ORIGINAL_PAYLOAD` |
| `replayRejectionReason` | `Unable to extract originalPayload from DLQ envelope` |
| `replayTarget` | `REJECTED_REPLAY_SO_INBOUND` |
| `originalPayloadExtractionStatus` | `FAILED` |
| `processingStatus` | `REPLAY_REJECTED` |

The script returns the message normally. It must not throw `RuntimeException` for extraction failures.

## GS_PrepareReplayRejectedPayload

### Purpose

Build a replay rejection JSON envelope for messages pulled from `DLQ_SO_INBOUND` but not eligible for replay.

Output body contains:

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

`GS_LogBeforeJms` or `GS_SetMplCustomHeaders`, when present, must only add custom header properties when values exist. Empty values must not be written.

## Logging Rule

Scripts must not write payload, original DLQ envelope, CSRF token, SAP cookie, Authorization header, bearer token, password, or secrets to MPL custom properties or logs.

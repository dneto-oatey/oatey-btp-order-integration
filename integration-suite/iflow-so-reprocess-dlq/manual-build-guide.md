# IFL_SO_REPROCESS_DLQ Manual Build Guide

## Purpose

Manual SAP Integration Suite build guide for `IFL_SO_REPROCESS_DLQ`.

The iFlow is used only for operations-controlled replay of reviewed DLQ Sales Order messages from `DLQ_SO_INBOUND` back to `JMS_SO_INBOUND`.

## Operational Deployment Rule

`IFL_SO_REPROCESS_DLQ` must remain Not Deployed by default.

Deploy only during an approved replay window. Undeploy immediately after replay completion.

Reason: if left active, it may create an operational loop:

```text
DLQ_SO_INBOUND -> IFL_SO_REPROCESS_DLQ -> JMS_SO_INBOUND -> IFL_SO_ORCHESTRATION -> DLQ_SO_INBOUND
```

## Build Guardrails

- Route ineligible messages to `REJECTED_REPLAY_SO_INBOUND`.
- Do not call SAP directly.
- Do not modify the Sales Order payload.
- Do not log payload content to MPL.
- Do not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, or custom persistence.

## Executable Flow

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

-> End
```

## Router Configuration

Router condition must use Simple Expression, not XPath.

| Route | CPI Router Setting |
| --- | --- |
| Eligible | Simple Expression `${property.replayEligible} == 'true'` |
| Rejected | Default / Otherwise |

## Step 1: Create Package And iFlow

| Field | Value |
| --- | --- |
| Package | Oatey Sales Order Integration |
| iFlow Name | `IFL_SO_REPROCESS_DLQ` |
| Runtime State | Not Deployed by default |
| Deployment Model | Deploy only during approved replay window; undeploy immediately after completion |
| Purpose | Manual replay from `DLQ_SO_INBOUND` to `JMS_SO_INBOUND` or `REJECTED_REPLAY_SO_INBOUND` |

## Step 2: Configure JMS Sender

| Setting | Value |
| --- | --- |
| Component | JMS Sender |
| Adapter Type | JMS |
| Queue Name | `DLQ_SO_INBOUND` |
| Access Type | Non-Exclusive |
| Processing Mode | Manual replay utility |
| Deployment Condition | Operations-approved replay window only |

## Step 3: Add `GS_ValidateDlqReplayEligibility`

The script must not throw exceptions for replay ineligibility. It must set `replayEligible` and `replayRejectionReason`.

| Condition | Expected Behavior |
| --- | --- |
| Invalid JSON envelope | `replayEligible = false` |
| Missing `originalPayload` | `replayEligible = false` |
| Missing `correlationId` | `replayEligible = false` |
| Missing `replayApproved` | Treat as `false`; `replayEligible = false` |
| `replayApproved != true` | `replayEligible = false` |
| Missing `replayCount` | Treat as `0` |
| Missing `maxReplayCount` | Treat as `1` |
| `replayCount >= maxReplayCount` | `replayEligible = false` |
| `SAP_AUTH_CONFIG_ERROR` | `replayEligible = false` |
| `VALIDATION_ERROR` | `replayEligible = false` unless `validationReplayApproved = true` |
| `SAP_BUSINESS_ERROR` | `replayEligible = true` only when `businessCorrectionConfirmed = true` and `replayApproved = true` |

## Step 4: Configure Eligible Route

- `GS_ExtractOriginalPayloadFromDlq`
- Optional safety Router if `replayEligible` changed during extraction
- `CM_PrepareRequeueToInbound`
- Optional `GS_SetMplCustomHeaders` or `GS_LogBeforeJms`
- JMS Receiver `JMS_SO_INBOUND`

`GS_ExtractOriginalPayloadFromDlq` increments `replayCount` by 1 when publishing back to `JMS_SO_INBOUND`.

## Step 5: Configure Rejected Route

- `GS_PrepareReplayRejectedPayload`
- `CM_SetReplayRejectedHeaders`
- Optional `GS_SetMplCustomHeaders` or `GS_LogBeforeJms`
- JMS Receiver `REJECTED_REPLAY_SO_INBOUND`

Messages without `replayApproved = true`, messages with missing `originalPayload`, and messages that exceed `maxReplayCount` must go to `REJECTED_REPLAY_SO_INBOUND`.

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

`GS_LogBeforeJms` or `GS_SetMplCustomHeaders` must only add custom header properties when values exist. Do not write empty custom header values.

## Manual Replay Procedure

1. Review the DLQ envelope in JMS monitoring.
2. Confirm root cause is resolved.
3. Set `replayApproved = true` only for approved messages.
4. Confirm `replayCount < maxReplayCount`; default `maxReplayCount` is `1`.
5. For `SAP_BUSINESS_ERROR`, set `businessCorrectionConfirmed = true` only after business/master data correction.
6. For `VALIDATION_ERROR`, set `validationReplayApproved = true` only when replay is explicitly approved.
7. Confirm idempotency and duplicate-processing review is complete.
8. Deploy `IFL_SO_REPROCESS_DLQ` manually.
9. Monitor eligible messages on `JMS_SO_INBOUND`.
10. Monitor rejected messages on `REJECTED_REPLAY_SO_INBOUND`.
11. Undeploy `IFL_SO_REPROCESS_DLQ` immediately after replay completion.

## Expected Result

Eligible messages are requeued to `JMS_SO_INBOUND` with the original Sales Order JSON body unchanged. Ineligible messages are routed to `REJECTED_REPLAY_SO_INBOUND` with the original DLQ envelope preserved and rejection metadata added. No SAP call occurs in this replay flow.

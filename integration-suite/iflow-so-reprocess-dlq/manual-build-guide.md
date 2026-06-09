# IFL_SO_REPROCESS_DLQ Manual Build Guide

## Purpose

This guide describes how to manually build `IFL_SO_REPROCESS_DLQ` in SAP Integration Suite.

The iFlow is used only for operations-controlled replay of reviewed DLQ Sales Order messages from `DLQ_SO_INBOUND` back to `JMS_SO_INBOUND`.

## Build Guardrails

- Keep the iFlow deployed but stopped by default.
- Start manually only after operations review.
- Stop immediately after the approved replay window.
- Route ineligible messages to `REJECTED_REPLAY_SO_INBOUND`.
- Do not call SAP directly.
- Do not modify the Sales Order payload.
- Do not log payload content to MPL.
- Do not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, or custom persistence.

## Executable Flow

```text
JMS Sender DLQ_SO_INBOUND
-> GS_ValidateDlqReplayEligibility
-> Router eligible vs rejected

Eligible:
  -> GS_ExtractOriginalPayloadFromDlq
  -> CM_PrepareRequeueToInbound
  -> JMS Receiver JMS_SO_INBOUND

Rejected:
  -> GS_PrepareReplayRejectedPayload
  -> CM_SetReplayRejectedHeaders
  -> JMS Receiver REJECTED_REPLAY_SO_INBOUND

-> End
```

## Step 1: Create Package And iFlow

| Field | Value |
| --- | --- |
| Package | Oatey Sales Order Integration |
| iFlow Name | `IFL_SO_REPROCESS_DLQ` |
| Runtime State | Deployed but stopped by default |
| Purpose | Manual replay from `DLQ_SO_INBOUND` to `JMS_SO_INBOUND` |

## Step 2: Configure JMS Sender

| Setting | Value |
| --- | --- |
| Component | JMS Sender |
| Adapter Type | JMS |
| Queue Name | `DLQ_SO_INBOUND` |
| Access Type | Non-Exclusive |
| Processing Mode | Manual replay utility |
| Start Condition | Operations-approved replay window only |

## Step 3: Add `GS_ValidateDlqReplayEligibility`

| Setting | Value |
| --- | --- |
| Component | Groovy Script |
| Script File | `scripts/GS_ValidateDlqReplayEligibility.groovy` |
| Purpose | Validate DLQ envelope and set `replayEligible` |
| Payload Logging | Not allowed |

Required validation outcomes:

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
| Missing `consumerId` | Set `UNKNOWN_CONSUMER` |
| Missing `idempotencyKey` | Continue with empty value |

The script must not throw exceptions for replay ineligibility. It must set:

| Property | Value |
| --- | --- |
| `replayEligible` | `true` or `false` |
| `replayRejectionReason` | Required when `replayEligible = false` |

## Step 4: Add Router

| Route | Condition |
| --- | --- |
| Eligible | `${property.replayEligible} = 'true'` |
| Rejected | `${property.replayEligible} != 'true'` |

## Step 5: Configure Eligible Route

### Add `GS_ExtractOriginalPayloadFromDlq`

| Setting | Value |
| --- | --- |
| Component | Groovy Script |
| Script File | `scripts/GS_ExtractOriginalPayloadFromDlq.groovy` |
| Purpose | Replace body with `originalPayload` only |
| Payload Mutation | No Sales Order field modification allowed |

### Add `CM_PrepareRequeueToInbound`

| Setting | Value |
| --- | --- |
| Component | Content Modifier |
| Name | `CM_PrepareRequeueToInbound` |
| Purpose | Set JMS headers and replay metadata before requeue |

### Configure JMS Receiver

| Setting | Value |
| --- | --- |
| Component | JMS Receiver |
| Adapter Type | JMS |
| Queue Name | `JMS_SO_INBOUND` |
| Access Type | Non-Exclusive |
| Body | Original Sales Order JSON payload |
| Transfer Exchange Properties | Enabled when available |

## Step 6: Configure Rejected Route

### Add `GS_PrepareReplayRejectedPayload`

| Setting | Value |
| --- | --- |
| Component | Groovy Script |
| Script File | `scripts/GS_PrepareReplayRejectedPayload.groovy` |
| Purpose | Preserve original DLQ envelope and add rejection metadata |
| Payload Logging | Not allowed |

### Add `CM_SetReplayRejectedHeaders`

| Setting | Value |
| --- | --- |
| Component | Content Modifier |
| Name | `CM_SetReplayRejectedHeaders` |
| Purpose | Set headers/properties before routing to replay rejection queue |

### Configure JMS Receiver

| Setting | Value |
| --- | --- |
| Component | JMS Receiver |
| Adapter Type | JMS |
| Queue Name | `REJECTED_REPLAY_SO_INBOUND` |
| Access Type | Non-Exclusive |
| Body | Replay rejection JSON envelope |
| Transfer Exchange Properties | Enabled when available |

## Step 7: Configure Monitoring

| Field | Source |
| --- | --- |
| `correlationId` | Exchange Property |
| `consumerId` | Exchange Property |
| `idempotencyKey` | Exchange Property |
| `replayEligible` | Exchange Property |
| `replayRejectionReason` | Exchange Property |
| `replayed` | Exchange Property/Header |
| `replayedAt` | Exchange Property/Header |
| `replayRejected` | Exchange Property/Header |
| `replayRejectedAt` | Exchange Property/Header |
| `replaySource` | Exchange Property/Header |
| `replayTarget` | Exchange Property/Header |
| `replayFlow` | Exchange Property/Header |
| `replayCount` | Exchange Property/Header |
| `processingStatus` | Exchange Property |

Do not add payload body to MPL custom properties.

## Step 8: Deployment Checklist

- [ ] iFlow deployed successfully.
- [ ] iFlow is stopped after deployment.
- [ ] JMS Sender queue is `DLQ_SO_INBOUND`.
- [ ] Router uses `replayEligible` property.
- [ ] Eligible route publishes to `JMS_SO_INBOUND`.
- [ ] Rejected route publishes to `REJECTED_REPLAY_SO_INBOUND`.
- [ ] Scripts are assigned in the correct sequence.
- [ ] Content Modifiers use `Create` actions only.
- [ ] Payload logging is disabled.
- [ ] Operations replay procedure is documented.

## Step 9: Manual Replay Procedure

1. Review the DLQ envelope in JMS monitoring.
2. Confirm root cause is resolved.
3. Set `replayApproved = true` only for approved messages.
4. Confirm `replayCount < maxReplayCount`; default `maxReplayCount` is `1`.
5. For `SAP_BUSINESS_ERROR`, set `businessCorrectionConfirmed = true` only after business/master data correction.
6. For `VALIDATION_ERROR`, set `validationReplayApproved = true` only when replay is explicitly approved.
7. Confirm idempotency and duplicate-processing review is complete.
8. Start `IFL_SO_REPROCESS_DLQ` manually.
9. Monitor eligible messages on `JMS_SO_INBOUND`.
10. Monitor rejected messages on `REJECTED_REPLAY_SO_INBOUND`.
11. Stop `IFL_SO_REPROCESS_DLQ` immediately after replay window.

## Expected Result

Eligible messages are requeued to `JMS_SO_INBOUND` with the original Sales Order JSON body unchanged. Ineligible messages are routed to `REJECTED_REPLAY_SO_INBOUND` with the original DLQ envelope preserved and rejection metadata added. No SAP call occurs in this replay flow.

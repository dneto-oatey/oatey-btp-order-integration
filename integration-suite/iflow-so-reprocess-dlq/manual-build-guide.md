# IFL_SO_REPROCESS_DLQ Manual Build Guide

## Purpose

This guide describes how to manually build `IFL_SO_REPROCESS_DLQ` in SAP Integration Suite.

The iFlow is used only for manual replay of reviewed DLQ Sales Order messages from `DLQ_SO_INBOUND` back to `JMS_SO_INBOUND`.

## Build Guardrails

- Keep the iFlow stopped by default.
- Start manually only after operations review.
- Do not call SAP directly.
- Do not modify the Sales Order payload.
- Do not log payload content to MPL.
- Do not introduce CAP, PostgreSQL, Event Mesh, RFC, BAPI, custom Z APIs, or custom persistence.

## Executable Flow

```text
JMS Sender DLQ_SO_INBOUND
-> GS_ValidateDlqReplayEligibility
-> GS_ExtractOriginalPayloadFromDlq
-> CM_PrepareRequeueToInbound
-> JMS Receiver JMS_SO_INBOUND
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
| Purpose | Validate DLQ envelope and replay eligibility |
| Payload Logging | Not allowed |

Required validation outcomes:

| Condition | Expected Behavior |
| --- | --- |
| Invalid JSON envelope | Reject replay |
| Missing `originalPayload` | Reject replay |
| Missing `correlationId` | Reject replay |
| Missing `consumerId` | Set `UNKNOWN_CONSUMER` |
| Missing `idempotencyKey` | Continue with empty value |
| `replayRequired = false` | Reject replay |
| `replayed = true` | Reject replay |

## Step 4: Add `GS_ExtractOriginalPayloadFromDlq`

| Setting | Value |
| --- | --- |
| Component | Groovy Script |
| Script File | `scripts/GS_ExtractOriginalPayloadFromDlq.groovy` |
| Purpose | Replace body with `originalPayload` only |
| Payload Mutation | No Sales Order field modification allowed |

The outbound body after this step must be the original Sales Order JSON payload only.

## Step 5: Add `CM_PrepareRequeueToInbound`

| Setting | Value |
| --- | --- |
| Component | Content Modifier |
| Name | `CM_PrepareRequeueToInbound` |
| Purpose | Set JMS headers and replay metadata before requeue |

Use the matrix in `content-modifiers.md`.

## Step 6: Configure JMS Receiver

| Setting | Value |
| --- | --- |
| Component | JMS Receiver |
| Adapter Type | JMS |
| Queue Name | `JMS_SO_INBOUND` |
| Access Type | Non-Exclusive |
| Body | Original Sales Order JSON payload |
| Transfer Exchange Properties | Enabled when available |

## Step 7: Configure Monitoring

| Field | Source |
| --- | --- |
| `correlationId` | Exchange Property |
| `consumerId` | Exchange Property |
| `idempotencyKey` | Exchange Property |
| `replayed` | Exchange Property/Header |
| `replayedAt` | Exchange Property/Header |
| `replaySource` | Exchange Property/Header |
| `replayTarget` | Exchange Property/Header |
| `replayFlow` | Exchange Property/Header |
| `processingStatus` | Exchange Property |

Do not add payload body to MPL custom properties.

## Step 8: Deployment Checklist

- [ ] iFlow deployed successfully.
- [ ] iFlow is stopped after deployment.
- [ ] JMS Sender queue is `DLQ_SO_INBOUND`.
- [ ] JMS Receiver queue is `JMS_SO_INBOUND`.
- [ ] Scripts are assigned in the correct sequence.
- [ ] `CM_PrepareRequeueToInbound` uses `Create` actions only.
- [ ] Payload logging is disabled.
- [ ] Operations replay procedure is documented.

## Step 9: Manual Replay Procedure

1. Review the DLQ envelope in JMS monitoring.
2. Confirm root cause is resolved.
3. Confirm idempotency and duplicate-processing review is complete.
4. Start `IFL_SO_REPROCESS_DLQ` manually.
5. Monitor replayed message publication to `JMS_SO_INBOUND`.
6. Confirm `IFL_SO_ORCHESTRATION` consumes the replayed message.
7. Stop `IFL_SO_REPROCESS_DLQ` immediately after replay window.

## Expected Result

The original Sales Order JSON payload is requeued to `JMS_SO_INBOUND` with replay metadata and preserved traceability fields. No SAP call occurs in this replay flow.

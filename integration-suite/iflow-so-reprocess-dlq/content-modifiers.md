# IFL_SO_REPROCESS_DLQ Content Modifier Matrix

## Scope

This document defines Content Modifier settings for `IFL_SO_REPROCESS_DLQ`.

SAP Integration Suite Content Modifier actions must use `Create` or `Delete`. This replay iFlow uses `Create` only.

## CM_PrepareRequeueToInbound

Purpose: prepare the extracted original Sales Order payload for requeue to `JMS_SO_INBOUND` and attach replay metadata.

| Action | Type | Name | Source Type | Source Value | Data Type |
| --- | --- | --- | --- | --- | --- |
| Create | Message Body | Body | Property | `originalPayload` | `java.lang.String` |
| Create | Message Header | `correlationId` | Property | `correlationId` | `java.lang.String` |
| Create | Message Header | `consumerId` | Property | `consumerId` | `java.lang.String` |
| Create | Message Header | `idempotencyKey` | Property | `idempotencyKey` | `java.lang.String` |
| Create | Message Header | `replayed` | Constant | `true` | `java.lang.String` |
| Create | Message Header | `replayedAt` | Property | `replayedAt` | `java.lang.String` |
| Create | Message Header | `replaySource` | Constant | `DLQ_SO_INBOUND` | `java.lang.String` |
| Create | Message Header | `replayTarget` | Constant | `JMS_SO_INBOUND` | `java.lang.String` |
| Create | Message Header | `replayFlow` | Constant | `IFL_SO_REPROCESS_DLQ` | `java.lang.String` |
| Create | Exchange Property | `processingStatus` | Constant | `REQUEUED_TO_INBOUND` | `java.lang.String` |
| Create | Exchange Property | `targetQueueName` | Constant | `JMS_SO_INBOUND` | `java.lang.String` |
| Create | Exchange Property | `replayed` | Constant | `true` | `java.lang.String` |
| Create | Exchange Property | `replaySource` | Constant | `DLQ_SO_INBOUND` | `java.lang.String` |
| Create | Exchange Property | `replayTarget` | Constant | `JMS_SO_INBOUND` | `java.lang.String` |
| Create | Exchange Property | `replayFlow` | Constant | `IFL_SO_REPROCESS_DLQ` | `java.lang.String` |

## Header Preservation

The following values are preserved from the DLQ envelope and should be available as Exchange Properties before the Content Modifier executes:

| Field | Source |
| --- | --- |
| `correlationId` | DLQ envelope |
| `consumerId` | DLQ envelope or `UNKNOWN_CONSUMER` fallback |
| `idempotencyKey` | DLQ envelope, may be empty |
| `originalPayload` | DLQ envelope |
| `replayedAt` | Generated UTC timestamp |

## Payload Rule

`CM_PrepareRequeueToInbound` must set the outbound body to the `originalPayload` property only.

The Content Modifier must not:

- Add wrapper fields around the Sales Order payload.
- Modify Sales Order header fields.
- Modify Sales Order item fields.
- Add replay metadata inside the Sales Order JSON body.
- Log payload content to MPL.

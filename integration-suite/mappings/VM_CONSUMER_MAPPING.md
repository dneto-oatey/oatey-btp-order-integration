# VM_CONSUMER_MAPPING

Consumer-specific mappings belong here so new consumers can be onboarded without changing existing flows.

## Initial Consumers

| Consumer | API Product | Pattern | Notes |
| --- | --- | --- | --- |
| Sales Rep Portal | REP_PORTAL_API | Synchronous | Direct pass-through via API Management. |
| Epicor HQ / EDI Translator | EDL_SALES_ORDER_INBOUND_API | Asynchronous | Validate, normalize, queue, orchestrate, callback. |
| Future Partner | CONSUMER_A_API | Asynchronous | Template for future onboarding. |

## Mapping Rules

| Consumer Field | Canonical Field | SAP Target | Required | Rule |
| --- | --- | --- | --- | --- |
| externalOrderId | externalOrderId | Customer reference / extension field | Yes | Also used for idempotency. |
| soldToParty | soldToParty | Sold-to party | Yes | Must exist in S/4HANA. |
| shipToParty | shipToParty | Ship-to party | No | Defaulting requires business approval. |
| requestedDeliveryDate | requestedDeliveryDate | Requested delivery date | No | Preserve consumer date when present. |
| items[].material | items[].material | Material | Yes | Validate in S/4HANA. |
| items[].quantity | items[].quantity | Requested quantity | Yes | Must be greater than zero. |
| items[].unitOfMeasure | items[].unitOfMeasure | Sales unit | Yes | Convert per consumer where required. |

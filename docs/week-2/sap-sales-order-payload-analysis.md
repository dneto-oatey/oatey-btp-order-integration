# Week 2 SAP Sales Order Payload Analysis

## Scope

This document analyzes the two real SAP Sales Order JSON payloads provided for the inbound POC baseline:

- K-Cimarron Lumber_476272_7e3c15ad-57a3-481e-b2c9-3d9228df04b5.JSON
- Affiliated Dist._P1297405_c9a3d002-2c89-47b8-8.JSON

The Week 2 POC scope remains limited to posting a JSON file payload to the standard SAP Sales Order API through APIM and SAP Integration Suite. No CAP, PostgreSQL, Event Mesh, UI, or additional runtime services are introduced.

## Header-Level Required Fields

The following header fields are present in both examples and are treated as required for inbound validation:

| Field | Purpose | Observed values |
| --- | --- | --- |
| SalesOrderType | SAP sales order type | ZOR |
| SalesOrganization | Sales organization | 1201 |
| DistributionChannel | Sales channel | 10, 20 |
| OrganizationDivision | Division | 00 |
| SoldToParty | Customer sold-to account | 0001011417, 0001024883 |
| PurchaseOrderByCustomer | Customer PO number | P1297405, 476272 |
| CustomerPurchaseOrderType | Customer PO source/type | EDI |
| CustomerPurchaseOrderDate | Customer PO date | 2025-04-15, 2025-04-16 |
| SalesOrderDate | Sales order date | 2025-04-15, 2025-04-16 |
| PricingDate | Header pricing date | 2025-04-15, 2025-04-16 |
| RequestedDeliveryDate | Requested delivery date | 2025-04-18, 2025-04-21 |
| to_Item.results | One or more order items | 31 items, 100 items |

## Header-Level Optional Fields

- IncotermsClassification is present in Affiliated Dist. with value PPD and absent in K-Cimarron Lumber. It should remain optional for the inbound POC.
- to_Text.results is present in both examples with delivery or PO note text. It is supported by the contract but should not block the POC if absent unless the business later makes header text mandatory.

## Item-Level Required Fields

The following item fields are present across both examples and are treated as required:

| Field | Purpose |
| --- | --- |
| UnderlyingPurchaseOrderItem | Customer PO line reference |
| Material | SAP material number |
| PricingDate | Item pricing date |
| RequestedQuantity | Requested order quantity |
| RequestedQuantityUnit | Quantity unit, observed as EA |
| to_PricingElement.results | One or more pricing condition entries |

The following item fields are present in both examples but should remain optional or customer-specific unless SAP validation requires them:

- MaterialByCustomer
- ZZ1_OriginalOrderQuant_SDI
- ZZ1_OriginalOrderQuant_SDIU

## Pricing Element Structure

Each item contains to_PricingElement.results as an array. Each observed item has one pricing element with this structure:

| Field | Observed pattern |
| --- | --- |
| ConditionType | EDI1 |
| ConditionQuantity | String quantity, observed as 10 |
| ConditionRateValue | String numeric price/rate value |
| ConditionCurrency | USD |

The schema requires at least one pricing element per item for this POC because both real files include pricing and the business request includes a negative test for missing pricing element.

## Text Structure

Both examples contain header text in to_Text.results:

| Field | Observed pattern |
| --- | --- |
| LongTextID | Z015 |
| LongText | Free-form customer delivery or PO note |

K-Cimarron has a shorter receiving-hours note. Affiliated Dist. has a longer purchase order note with delivery appointment and invoicing instructions.

## Differences Between Examples

| Area | K-Cimarron Lumber | Affiliated Dist. |
| --- | --- | --- |
| PurchaseOrderByCustomer | 476272 | P1297405 |
| SoldToParty | 0001024883 | 0001011417 |
| DistributionChannel | 20 | 10 |
| Item count | 100 | 31 |
| IncotermsClassification | Not present | PPD |
| RequestedDeliveryDate | 2025-04-21T00:00:00 | 2025-04-18T00:00:00 |
| Header text | Receiving hours note | Purchase order / receiving / invoice note |
| Materials | Includes values such as K820-72BN | Includes values such as 31135 |

## Contract Impact

- sales-order-inbound-api.yaml now describes the inbound body as SAP Sales Order API JSON rather than a generic normalized order object.
- schemas/sales-order-request.json now validates SAP field names and nested SAP structures.
- Idempotency-Key remains an HTTP header and is not modeled as an authoritative body field.
- APIM responsibilities remain authentication, authorization, rate limiting, analytics, and consumer separation.
- IFL_SO_INBOUND remains responsible for validation, enrichment, correlation ID, JMS publish, and ACK.

## Readiness for Week 2 POC Build

Ready with focused validation on the real SAP Sales Order payload structure. The next build step should implement schema/header validation in IFL_SO_INBOUND and payload handoff to JMS without changing the approved runtime architecture.

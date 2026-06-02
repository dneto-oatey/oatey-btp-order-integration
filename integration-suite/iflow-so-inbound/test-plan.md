# IFL_SO_INBOUND Test Plan

## Scope

Week 4 build specification test plan for IFL_SO_INBOUND. The iFlow validates headers and SAP Sales Order JSON, publishes valid messages to JMS_SO_INBOUND, and returns HTTP 202 only after successful JMS publish.

## Test Data

| Test data | Location |
| --- | --- |
| Valid K-Cimarron payload | test-payloads/real-sap-sales-order/K-Cimarron Lumber_476272_7e3c15ad-57a3-481e-b2c9-3d9228df04b5.JSON |
| Valid Affiliated Dist payload | test-payloads/real-sap-sales-order/Affiliated Dist._P1297405_c9a3d002-2c89-47b8-8.JSON |
| Missing SoldToParty | test-payloads/real-sap-sales-order/negative/missing-sold-to-party.json |
| Missing Material | test-payloads/real-sap-sales-order/negative/missing-material.json |
| Missing RequestedQuantity | test-payloads/real-sap-sales-order/negative/missing-requested-quantity.json |
| Missing pricing element | test-payloads/real-sap-sales-order/negative/missing-pricing-element.json |

## Required Headers For Positive Tests

| Header | Example value |
| --- | --- |
| Content-Type | application/json |
| Idempotency-Key | test-idempotency-001 |
| X-Consumer-ID | WEEK4_TEST_CONSUMER |
| X-Correlation-ID | Optional UUID value |

## Positive Test Matrix

| ID | Scenario | Payload | Header condition | Expected HTTP | Expected JMS |
| --- | --- | --- | --- | --- | --- |
| INB-P01 | Valid K-Cimarron with supplied correlation | K-Cimarron real payload | X-Correlation-ID supplied | 202 | Message published with same correlationId |
| INB-P02 | Valid K-Cimarron without correlation | K-Cimarron real payload | X-Correlation-ID omitted | 202 | Message published with generated UUID |
| INB-P03 | Valid Affiliated with Incoterms | Affiliated real payload | All required headers | 202 | Message published with incotermsClassification property |
| INB-P04 | Header text preserved | Valid payload with to_Text.results | All required headers | 202 | Original body preserved in JMS |
| INB-P05 | Idempotency from header | Valid payload | Idempotency-Key supplied in header | 202 | idempotencyKey JMS property equals header value |

## Negative Header Test Matrix

| ID | Scenario | Expected HTTP | Expected errorCode | Expected JMS |
| --- | --- | --- | --- | --- |
| INB-H01 | Missing Idempotency-Key | 400 | MISSING_IDEMPOTENCY_KEY | No publish |
| INB-H02 | Blank Idempotency-Key | 400 | MISSING_IDEMPOTENCY_KEY | No publish |
| INB-H03 | Missing X-Consumer-ID | 400 | MISSING_CONSUMER_ID | No publish |
| INB-H04 | Blank X-Consumer-ID | 400 | MISSING_CONSUMER_ID | No publish |
| INB-H05 | Content-Type text/plain | 400 | INVALID_CONTENT_TYPE | No publish |
| INB-H06 | Blank supplied X-Correlation-ID | 400 | INVALID_CORRELATION_ID | No publish |

## Negative Payload Test Matrix

| ID | Scenario | Payload | Expected HTTP | Expected errorCode | Expected JMS |
| --- | --- | --- | --- | --- | --- |
| INB-V01 | Missing SoldToParty | missing-sold-to-party.json | 422 | PAYLOAD_VALIDATION_FAILED | No publish |
| INB-V02 | Missing Material | missing-material.json | 422 | PAYLOAD_VALIDATION_FAILED | No publish |
| INB-V03 | Missing RequestedQuantity | missing-requested-quantity.json | 422 | PAYLOAD_VALIDATION_FAILED | No publish |
| INB-V04 | Missing pricing element | missing-pricing-element.json | 422 | PAYLOAD_VALIDATION_FAILED | No publish |
| INB-V05 | Malformed JSON body | Manually broken JSON | 400 | INVALID_JSON | No publish |

## JMS Failure Test Matrix

| ID | Scenario | Setup | Expected HTTP | Expected errorCode |
| --- | --- | --- | --- | --- |
| INB-J01 | JMS_SO_INBOUND unavailable | Disable or misconfigure JMS receiver in test tenant | 500 | JMS_PUBLISH_FAILED |
| INB-J02 | JMS authorization failure | Use invalid JMS credential or queue config | 500 | JMS_PUBLISH_FAILED |
| INB-J03 | JMS timeout | Simulate adapter timeout if available | 500 | JMS_PUBLISH_FAILED |

## ACK Verification

| Assertion | Expected result |
| --- | --- |
| ACK returned only after JMS publish | HTTP 202 appears only when queue message exists |
| ACK status field | ACCEPTED |
| ACK correlationId | Matches supplied header or generated UUID |
| ACK idempotencyKey | Matches Idempotency-Key header |
| ACK message | Sales order accepted for asynchronous processing |

## Payload Preservation Verification

| Assertion | Expected result |
| --- | --- |
| JMS body equals inbound body | No custom canonical mapping or mutation |
| SAP Sales Order fields preserved | SalesOrderType, SoldToParty, to_Item.results, to_PricingElement.results remain unchanged |
| Optional fields preserved | IncotermsClassification and to_Text.results remain when present |
| No body idempotency authority | Any idempotencyKey in body is ignored; header remains authoritative |

## Out Of Scope Verification

| Assertion | Expected result |
| --- | --- |
| No S/4HANA receiver adapter | IFL_SO_INBOUND has none |
| No CAP service | None referenced |
| No PostgreSQL persistence | None referenced |
| No Event Mesh | None referenced |
| No UI artifact | None referenced |
| No custom canonical model | Original SAP JSON is used |
| No payload persistence outside JMS | Only JMS_SO_INBOUND receives valid payloads |

# IFL_SO_INBOUND Test Plan

## Scope

Test plan for the IFL_SO_INBOUND executable POC flow. The iFlow validates Content-Type and SAP Sales Order payload with Groovy, sends valid messages to JMS_SO_INBOUND, and returns HTTP 202 only after successful JMS send.

There is no JSON Schema Validation step and no JMS Request Reply.

## Test Data

| Test data | Location |
| --- | --- |
| Valid K-Cimarron payload | test-payloads/real-sap-sales-order/K-Cimarron Lumber_476272_7e3c15ad-57a3-481e-b2c9-3d9228df04b5.JSON |
| Valid Affiliated Dist payload | test-payloads/real-sap-sales-order/Affiliated Dist._P1297405_c9a3d002-2c89-47b8-8.JSON |
| Missing SoldToParty | test-payloads/real-sap-sales-order/negative/missing-sold-to-party.json |
| Missing Material | test-payloads/real-sap-sales-order/negative/missing-material.json |
| Missing RequestedQuantity | test-payloads/real-sap-sales-order/negative/missing-requested-quantity.json |
| Missing pricing element | test-payloads/real-sap-sales-order/negative/missing-pricing-element.json |

## Header Rules

| Header | POC rule |
| --- | --- |
| Content-Type | Required; must contain application/json |
| X-Correlation-ID | Optional; generated when missing |
| Idempotency-Key | Optional; empty when missing |
| X-Consumer-ID | Optional; UNKNOWN_CONSUMER when missing |

## Postman Test Matrix

| ID | Scenario | Expected HTTP | Expected result | Expected JMS |
| --- | --- | --- | --- | --- |
| INB-P01 | Happy Path | 202 | status ACCEPTED, supplied correlationId returned | One message in JMS_SO_INBOUND |
| INB-V01 | Missing SoldToParty | 422 | errorCode PAYLOAD_VALIDATION_FAILED | No message |
| INB-H01 | Missing Content-Type | 400 | errorCode INVALID_CONTENT_TYPE | No message |
| INB-P02 | Missing X-Consumer-ID | 202 | consumerId UNKNOWN_CONSUMER | One message |
| INB-P03 | Missing X-Correlation-ID | 202 | generated correlationId returned | One message |

## Additional Negative Payload Matrix

| ID | Scenario | Expected HTTP | Expected errorCode | Expected JMS |
| --- | --- | --- | --- | --- |
| INB-V02 | Missing Material | 422 | PAYLOAD_VALIDATION_FAILED | No message |
| INB-V03 | Missing RequestedQuantity | 422 | PAYLOAD_VALIDATION_FAILED | No message |
| INB-V04 | Missing pricing element | 422 | PAYLOAD_VALIDATION_FAILED | No message |
| INB-V05 | Malformed JSON body | 400 | INVALID_JSON | No message |

## JMS Failure Matrix

| ID | Scenario | Setup | Expected HTTP | Expected errorCode |
| --- | --- | --- | --- | --- |
| INB-J01 | JMS_SO_INBOUND unavailable | Disable or misconfigure JMS receiver in test tenant | 500 | JMS_PUBLISH_FAILED |
| INB-J02 | JMS authorization failure | Use invalid JMS credential or queue config | 500 | JMS_PUBLISH_FAILED |
| INB-J03 | JMS timeout | Simulate adapter timeout if available | 500 | JMS_PUBLISH_FAILED |

## JMS Verification

| Assertion | Expected result |
| --- | --- |
| Queue | JMS_SO_INBOUND |
| Access Type | Non-Exclusive |
| Retention | 7 days |
| Transfer Exchange Properties | Enabled |
| Body preservation | JMS body equals inbound SAP Sales Order JSON |
| ACK timing | HTTP 202 appears only after JMS send succeeds |

## Out Of Scope Verification

IFL_SO_INBOUND has no S/4HANA receiver adapter, JSON Schema Validation step, JMS Request Reply, CAP service, PostgreSQL persistence, Event Mesh, UI, RFC, BAPI, custom Z service, custom canonical model, or payload persistence outside JMS.

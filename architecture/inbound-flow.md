# SAP BTP Inbound Sales Order Integration - Detailed Flow

## Diagram Overview

This document describes the **Inbound Processing Architecture** as shown in the approved BTP Solution Diagram (Level 1.1).

The diagram illustrates the complete flow of sales orders from multiple consumers through SAP BTP to SAP S/4HANA.

---

## Architecture Layers

### 1. Consumer Layer (Top - Yellow Background)

#### Asynchronous Consumers (Channel 1 - Upstream Senders)

**Epicor HQ / EDI Translator**
- Translates X12 → JSON format
- Entry point: `POST /GET`
- Channel: Asynchronous

**Other Consumers**
- Partner apps / custom channels
- Entry point: `POST /GET`
- Channel: Asynchronous

#### Synchronous Consumer (Channel 1 - Right Side)

**Sales Rep Portal**
- Future direct API consumer
- Entry point: `POST /GET`
- Channel: Synchronous (direct to S/4HANA)
- POC scope note: "Future design keeps the API open for additional consumers"

---

### 2. SAP BTP Platform - API Management Layer (Blue Background)

#### API Subscriptions

**EDL_SALES_ORDER_INBOUND API Subscription**
- Handles Epicor HQ and EDI Translator traffic
- OAuth / key policies
- Rate limiting / consumer separation

**CONSUMER_A API Subscription**
- Handles other partner/custom consumers
- OAuth / key policies
- Rate limiting / consumer separation

**REP_PORTAL API Subscription**
- Handles Sales Rep Portal (synchronous)
- OAuth / key policies
- Rate limiting / consumer separation

**API Management Features**:
- Authentication enforcement
- Rate limiting per consumer
- Consumer separation and analytics
- All traffic routed through API Management

---

### 3. Integration Suite Layer (Light Blue Background)

#### 2a. Inbound iFlow - Integration and Orchestration

**Inbound Processing iFlow** (`IFL_SO_INBOUND`)

```
Consumer API Request
        ↓
[Validate + Normalize]
  ├─ Schema checks
  ├─ Mandatory fields
  └─ Partner-specific mapping rules
        ↓
[JMS Queue - Decoupling + Retry Buffer]
  ├─ Dead Letter Queue
  ├─ Retain retries exhausted
  └─ Message created / Queued / Pending
        ↓
[Send Response / Acknowledgment]
  └─ API response to sender
```

**Responsibilities**:
- Validate payload against schema
- Normalize data formats
- Apply partner/consumer-specific mapping rules
- Generate Correlation ID
- Publish to JMS Queue
- Send ACK back to consumer

**Output**: Message published to JMS queue with:
- Order data (normalized JSON)
- Correlation ID
- Consumer metadata
- Idempotency key

---

#### Exception Subflow

**Exception Subflow** (`SP_EXCEPTION_HANDLER`)

```
Error Detection
        ↓
[Exception Subflow]
├─ Trap all technical exceptions
├─ Retry or abort actions
├─ Alarming and error status update
        ↓
[Log + Alert]
└─ Correlation ID tracking
```

**Triggers**:
- Validation failures
- Mapping errors
- System errors
- Communication failures

**Actions**:
- Log with Correlation ID
- Alert operations team
- Update status in monitoring
- Store error details for DLQ

---

#### Orchestration iFlow

**Orchestration iFlow** (`IFL_SO_ORCHESTRATION`)

```
[Receive Inbound Sales Order]
├─ Consume from JMS Queue
├─ Canonical JSON payload
└─ Correlation ID / Idempotency key
        ↓
[Map to SAP Sales Order API]
├─ Convert to SAP format
├─ Keep S/4 logic standard
        ↓
[Call SAP Sales Order Service]
├─ Synchronous API call
├─ Success / business error response
        ↓
[Monitor + Log Results]
```

**Responsibilities**:
- Consume message from JMS queue
- Transform canonical format → SAP Sales Order API format
- Call `C_SALESORDERAPI_ORDERS` (standard SAP API)
- Process response (success/error)
- Update order status
- Trigger callback (optional)
- Log all operations with Correlation ID

**SAP API Calls**:
- `POST Sales Order API` - Create sales order
- Standard inbound service
- No RFC calls
- No BAPI calls
- No custom Z services

---

### 4. SAP S/4HANA Layer (Green Background)

#### GET Sales Order API

**Purpose**: Retrieve existing sales order information

**Usage**:
- Query for duplicate detection (idempotency)
- Verify order status
- Retrieve order details for callbacks

**API**: Standard SAP Sales Order Read Service

---

#### Sales Order Confirm Processing (A2A) - Callback

**Purpose**: Asynchronous notification of order creation status

**Flow**:
```
Sales Order Created in S/4HANA
        ↓
[A2A Callback iFlow]
├─ Extract consumer callback URL
├─ Prepare confirmation payload
└─ POST to consumer endpoint
        ↓
[Retry Logic]
├─ Max 5 retries
├─ Exponential backoff
└─ Alert on final failure
```

**Callback Includes**:
- Sales Order Number (created)
- Confirmation status
- Correlation ID (for tracing)
- Any errors/warnings

---

#### POST Sales Order API

**Purpose**: Create sales orders in S/4HANA

**Standard Inbound Service**:
- Creation via standard API
- Sales Order creation
- Sales Order (ZA2)

**Input Transformation** (via IFL_SO_ORCHESTRATION):
```
Consumer Order Format → SAP Sales Order Format
├─ Customer mapping (bill-to/ship-to)
├─ Item mapping (material, quantity, price)
├─ Header mapping (order type, plant, sales org)
└─ Custom field mapping (correlation ID, consumer reference)
```

---

## Message Flow Scenarios

### Scenario 1: Epicor EDI Order (Asynchronous)

```
1. Epicor HQ sends X12 EDI message
        ↓
2. EDI Translator converts X12 → JSON
        ↓
3. POST to EDL_SALES_ORDER_INBOUND API (API Management)
        ↓
4. API Management: Authenticate (OAuth/Key) + Rate Limit
        ↓
5. IFL_SO_INBOUND: Validate + Normalize
        ↓
6. Publish to JMS_SO_INBOUND queue
        ↓
7. Return ACK to Epicor
        ↓
8. [ASYNC] IFL_SO_ORCHESTRATION: Consume from queue
        ↓
9. Map to SAP Sales Order format
        ↓
10. Call POST Sales Order API → S/4HANA
        ↓
11. S/4HANA creates order (status "Created" in monitoring)
        ↓
12. A2A Callback: POST confirmation back to Epicor
        ↓
13. Epicor receives confirmation (order number, status)
```

---

### Scenario 2: Sales Rep Portal (Synchronous - Future)

```
1. Sales Rep Portal sends order request
        ↓
2. POST to REP_PORTAL API (API Management)
        ↓
3. API Management: Authenticate + Rate Limit
        ↓
4. BYPASS Integration Suite (low-latency direct path)
        ↓
5. Call POST Sales Order API → S/4HANA
        ↓
6. S/4HANA creates order immediately
        ↓
7. Return response to Portal (synchronous)
        ↓
8. Portal displays order number to user (immediate feedback)
```

**Note**: Sales Rep Portal bypasses IFL_SO_INBOUND for real-time UX.

---

### Scenario 3: Consumer A (Future Partner - Asynchronous)

```
1. Partner API sends order JSON
        ↓
2. POST to CONSUMER_A API (API Management)
        ↓
3. API Management: Authenticate (OAuth) + Rate Limit
        ↓
4. IFL_SO_INBOUND: Validate + Normalize (consumer-specific rules)
        ↓
5. Publish to JMS_SO_INBOUND queue
        ↓
6. Return ACK to partner
        ↓
7. [ASYNC] IFL_SO_ORCHESTRATION: Consume from queue
        ↓
8. Map to SAP Sales Order format
        ↓
9. Call POST Sales Order API → S/4HANA
        ↓
10. If successful: A2A Callback → Partner notification
        ↓
11. If failed: DLQ (manual intervention required)
```

---

## Design Principles (From Diagram)

The diagram footer documents key design principles:

✅ **API-first entry via API Management**
- Loose coupling through semantic model and persistence
- Reprocessing ready (idempotency + correlation ID)
- Standard SAP Sales Order API preferred over custom core logic

### Data Flow Through Integration Suite

```
Consumer
    ↓
[API Management Layer]
    ├─ Authentication
    ├─ Rate Limiting
    └─ Consumer Identification
    ↓
[IFL_SO_INBOUND]
    ├─ Validation
    ├─ Normalization
    ├─ Consumer-Specific Mapping
    └─ JMS Queue Publication
    ↓
[JMS Queue]
    ├─ Decoupling
    ├─ Retry Buffer
    └─ Dead Letter Queue
    ↓
[IFL_SO_ORCHESTRATION]
    ├─ Message Consumption
    ├─ SAP Format Transformation
    ├─ S/4HANA API Call
    └─ Callback Trigger
    ↓
[S/4HANA]
    ├─ Sales Order Creation
    ├─ Confirmation
    └─ Callback to Consumer
```

---

## Integration Points Summary

| Component | Technology | Function |
|-----------|-----------|----------|
| **Consumer** | EDI, JSON, Custom API | Order submission |
| **API Management** | SAP API Management | Auth, Rate Limit, Analytics |
| **IFL_SO_INBOUND** | Cloud Integration iFlow | Validation, Normalization, JMS Publish |
| **JMS Queue** | Event Mesh / Messaging | Decoupling, Buffering, Retry |
| **Exception Handler** | Cloud Integration Subflow | Error Trapping, Alerting |
| **IFL_SO_ORCHESTRATION** | Cloud Integration iFlow | Transformation, S/4HANA Call |
| **S/4HANA API** | OData / REST | Sales Order Creation |
| **A2A Callback** | Cloud Integration iFlow | Consumer Notification |

---

## Monitoring Points

**Correlation ID Tracking** across:
1. Consumer request (entry point)
2. API Management (rate limit)
3. IFL_SO_INBOUND (validation)
4. JMS Queue (buffering)
5. IFL_SO_ORCHESTRATION (processing)
6. S/4HANA (creation)
7. A2A Callback (confirmation)

**Status Updates**:
- RECEIVED (API Management)
- VALIDATED (IFL_SO_INBOUND)
- QUEUED (JMS)
- PROCESSING (IFL_SO_ORCHESTRATION)
- SUCCESS (S/4HANA confirmed)
- FAILED (Exception Handler or DLQ)

---

## Error Handling

### Retry Strategy

**Exponential Backoff**:
- Retry 1: 5 seconds
- Retry 2: 25 seconds
- Retry 3: 125 seconds
- Max retries: 3

**Triggers**: Network failures, transient S/4HANA errors

### Dead Letter Queue

**Triggers**:
- All retries exhausted
- Validation failure
- Non-recoverable business error

**Action**: Alert operations, manual review required

---

## Consumer Onboarding Example

To add a new consumer (e.g., Shopify):

1. **API Management**: Create `SHOPIFY_API` subscription
   - Define rate limits
   - Assign OAuth credentials
   - Enable analytics

2. **IFL_SO_INBOUND**: Add consumer mapping
   - Shopify field → Canonical format rules
   - Shopify-specific defaults
   - Callback URL configuration

3. **Testing**: End-to-end flow validation
   - Send test order
   - Verify JMS queue publication
   - Confirm S/4HANA order creation
   - Validate callback delivery

4. **Monitoring**: Configure alerts
   - Error rate threshold
   - Processing latency SLA
   - DLQ depth monitoring

---

## Clean Core Compliance

This architecture adheres to **SAP Clean Core** principles:

✅ Uses standard `C_SALESORDERAPI_ORDERS` API  
✅ No RFC calls  
✅ No BAPI calls  
✅ No custom Z services  
✅ Extensibility via standard fields  
✅ Future-proof for SAP S/4HANA upgrades  

---

## Document References

- **Architecture Master**: `architecture/architecture.md`
- **Diagram**: Inbound Sales Order Integration (Level 1.1)
- **Standards**: SAP BTP Enterprise Integration, Clean Core Guidelines


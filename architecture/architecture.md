# SAP BTP Inbound Sales Order Integration Architecture (Oatey)

## Executive Summary

This document defines the enterprise architecture for the Oatey Sales Order Integration on SAP BTP. The solution receives Sales Orders from multiple external consumers and creates corresponding sales orders in SAP S/4HANA, with a design that supports current and future consumer onboarding without architectural redesign.

---

## Architectural Principles (Mandatory)

The following principles are non-negotiable and guide all implementation decisions:

- **SAP Clean Core** - Minimize customizations to core S/4HANA
- **API First** - All integrations exposed via standardized APIs
- **Reprocessing Ready** - Every message must be idempotent and replayable
- **Loose Coupling** - Decouple consumer interactions from core processing
- **Consumer Agnostic Design** - Add new consumers without modifying existing flows
- **Retry & Recovery** - Automatic retry with exponential backoff and dead-letter handling
- **Observability** - Complete traceability through Correlation IDs and structured logging

---

## Approved Architecture

### 1. Consumer Layer

#### Supported Consumers

- **Sales Rep Portal** (Initial - Synchronous)
- **EDI Translators** (Asynchronous - Future)
- **Partner Integrations** (Asynchronous - Future)

#### Consumer Onboarding

Each consumer receives:
- Dedicated API Product in API Management
- Individual Rate Limiting policy
- Consumer-specific OAuth/API Key credentials
- Isolated analytics and monitoring

---

### 2. API Management Layer

**Responsibility**: Entry point and policy enforcement

**Capabilities**:
- Authentication (OAuth 2.0, API Keys)
- Authorization (Role-based access control)
- Quotas and Rate Limiting
- Analytics and consumer tracking
- API versioning support

**Out of Scope**:
- Payload mapping
- Orchestration
- Business logic
- System integration

---

### 3. Integration Patterns

#### Pattern A: Synchronous (Sales Rep Portal)

```
Sales Rep Portal
    ↓
API Management (Authentication, Rate Limiting)
    ↓
SAP Sales Order API (S/4HANA)
    ↓
Response → Portal (Immediate UX feedback)
```

**Characteristics**:
- Direct pass-through to S/4HANA
- Bypasses Integration Suite for low-latency response
- Real-time user experience
- No queuing

**Use Case**: User-driven, synchronous operations requiring immediate feedback

---

#### Pattern B: Asynchronous (EDI, Partners, Future Consumers)

```
Consumer
    ↓
API Management (Authentication, Rate Limiting)
    ↓
IFL_SO_INBOUND (Validation & Buffering)
    ↓
JMS_SO_INBOUND (Message Queue)
    ↓
IFL_SO_ORCHESTRATION (Processing & Transformation)
    ↓
SAP Sales Order API (S/4HANA)
    ↓
Callback A2A → Sales Order Confirmation Processing
    ↓
Consumer Notification (Async Callback)
```

**Characteristics**:
- Asynchronous message-driven architecture
- Decoupled consumer from core processing
- Queue-based buffering and retry
- Dead-letter queue for failed messages
- Callbacks for consumer notification

**Use Case**: Batch processing, EDI translations, partner integrations, resilience at scale

---

## Component Architecture

### 3.1 Inbound Integration Flow (IFL_SO_INBOUND)

**Purpose**: First-stage validation and consumer-specific processing

**Responsibilities**:
- Validate JSON/XML payload structure
- Validate required fields per consumer contract
- Apply consumer-specific transformation rules
- Generate Correlation ID (if not provided)
- Enrich message with metadata
- Publish to JMS queue
- Return ACK to consumer

**Out of Scope**:
- Direct S/4HANA calls
- Core business logic
- System orchestration

**Outputs**:
- JMS message on `JMS_SO_INBOUND` queue
- Correlation ID for tracking
- Consumer metadata (Consumer ID, API Product, timestamp)

**Error Handling**:
- Validation failures → HTTP 400 with error details
- Schema violations → HTTP 422 Unprocessable Entity
- System errors → HTTP 500 with Correlation ID

---

### 3.2 JMS Queue (JMS_SO_INBOUND)

**Purpose**: Decoupling and buffering

**Configuration**:
- Queue name: `JMS_SO_INBOUND`
- Dead-letter queue: `DLQ_SO_INBOUND`
- Message persistence: Enabled
- Time to Live (TTL): 7 days
- Max retries before DLQ: 3

**Message Format**:
```json
{
  "correlationId": "UUID",
  "consumerId": "SALES_REP_PORTAL",
  "payload": {/* Original sales order */},
  "metadata": {
    "timestamp": "ISO8601",
    "apiProductName": "SalesRepPortal-API-v1",
    "consumerVersion": "1.0"
  },
  "idempotencyKey": "consumer_order_id"
}
```

---

### 3.3 Orchestration Integration Flow (IFL_SO_ORCHESTRATION)

**Purpose**: Core message processing and S/4HANA integration

**Responsibilities**:
- Consume messages from JMS queue
- Transform consumer model to SAP Sales Order format
- Call SAP Sales Order API (standard API only)
- Process API response (success/error)
- Update processing status
- Trigger callback to consumer (if configured)
- Log all operations with Correlation ID

**Out of Scope**:
- Complex business rule engine
- Custom persistence
- Direct RFC/BAPI calls

**Transformation Rules**:
- Map consumer field names to SAP standardized fields
- Apply consumer-specific defaults
- Validate against SAP Sales Order schema
- Handle currency/unit conversions per consumer

**Error Handling**:
- Non-recoverable errors → Move to DLQ with error details
- Transient errors → Automatic retry with exponential backoff
- Timeout errors → Retry after configurable delay

---

### 3.4 Callback Architecture

**Purpose**: Notify consumers of processing results

**Flow**:
```
S/4HANA Sales Order Created
    ↓
Sales Order Confirmation Processing (CPI iFlow)
    ↓
Extract Consumer Callback URL
    ↓
POST Confirmation to Consumer
    ↓
Retry on failure (max 5 times)
```

**Callback Payload**:
```json
{
  "correlationId": "UUID",
  "status": "SUCCESS|FAILED|PARTIAL",
  "salesOrderNumber": "SO-12345",
  "consumerId": "SALES_REP_PORTAL",
  "processingTimestamp": "ISO8601",
  "errors": [
    {
      "code": "INVALID_FIELD",
      "field": "customerNumber",
      "message": "Customer not found in SAP"
    }
  ]
}
```

---

## Reprocessing & Idempotency

### Correlation ID

**Generation**: 
- Generated by IFL_SO_INBOUND if not provided by consumer
- Format: UUID v4
- Included in all logs and callbacks

**Usage**:
- Track message through entire pipeline
- Identify duplicate submissions
- Root cause analysis
- End-to-end monitoring

---

### Idempotency Key

**Purpose**: Prevent duplicate Sales Orders in S/4HANA

**Strategy**:
- Consumer provides unique key per order (e.g., `PORTAL-ORDER-001`)
- Stored in custom field or header
- Checked before S/4HANA API call
- Prevents duplicate orders on retry

**Implementation**:
- Cache idempotency keys for 24 hours
- Return cached response if duplicate detected
- Log duplicate attempts

---

### Retry Strategy

**Exponential Backoff Configuration**:
- Retry 1: 5 seconds
- Retry 2: 25 seconds
- Retry 3: 125 seconds
- Max retries: 3
- Total retry window: ~2.5 minutes

**Retry Conditions**:
- Network timeouts
- Transient S/4HANA errors (HTTP 5xx)
- Service unavailability
- Rate limit errors (HTTP 429)

**Non-Retry Conditions**:
- Validation failures (HTTP 4xx)
- Authentication errors
- Business rule violations
- Customer not found

---

### Dead-Letter Queue (DLQ_SO_INBOUND)

**Purpose**: Capture messages that cannot be processed

**Configuration**:
- Queue name: `DLQ_SO_INBOUND`
- Manual intervention required
- Alert on DLQ depth > 0
- Monthly DLQ audit

**DLQ Message Content**:
```json
{
  "originalMessage": {/* JMS message */},
  "failureReason": "String",
  "failureTimestamp": "ISO8601",
  "failureCount": 3,
  "correlationId": "UUID"
}
```

---

## Monitoring & Observability

### Structured Logging

Every operation logs:
- **Correlation ID**: Trace across systems
- **Consumer ID**: Identify source
- **Sales Order Number**: Business entity
- **Timestamp**: ISO 8601 format
- **Processing Status**: RECEIVED, VALIDATED, QUEUED, PROCESSING, SUCCESS, FAILED
- **Error Category**: VALIDATION, SYSTEM, TRANSIENT, BUSINESS_RULE

### Integration Suite Monitoring

**Integration Monitoring Dashboard**:
- Message processing metrics
- Error rate by consumer
- Latency analysis
- Throughput monitoring

**Alert Triggers**:
- DLQ depth > 0
- Processing latency > 5 minutes
- Error rate > 5%
- S/4HANA API unavailability

### SAP Cloud ALM Integration

- Link CPI monitoring to Cloud ALM
- Track end-to-end processing time
- Identify bottlenecks
- Performance trending

---

## Naming Conventions

All artifacts follow standardized naming:

| Artifact Type | Naming Pattern | Example |
|---|---|---|
| Inbound iFlow | `IFL_SO_INBOUND` | `IFL_SO_INBOUND` |
| Orchestration iFlow | `IFL_SO_ORCHESTRATION` | `IFL_SO_ORCHESTRATION` |
| JMS Queue | `JMS_SO_INBOUND` | `JMS_SO_INBOUND` |
| Dead-Letter Queue | `DLQ_SO_INBOUND` | `DLQ_SO_INBOUND` |
| Exception Handler | `SP_EXCEPTION_HANDLER` | `SP_EXCEPTION_HANDLER` |
| Variable Mapping | `VM_CONSUMER_MAPPING` | `VM_CONSUMER_MAPPING` |
| Script (Correlation ID) | `SCR_CORRELATION_ID` | `SCR_CORRELATION_ID` |
| API Product | `{Consumer}-API-v{Version}` | `SalesRepPortal-API-v1` |

---

## SAP Clean Core Alignment

**Clean Core Principles Enforced**:

1. **Standard APIs Only**
   - Use SAP Sales Order API (C_SALESORDERAPI_ORDERS)
   - No BAPI calls
   - No RFC calls
   - No custom Z services

2. **Minimal Customizations**
   - Custom fields stored in standard extensibility fields
   - No modifications to standard processes
   - No custom tables
   - All extensions reversible

3. **Future-Proof**
   - SAP Cloud upgrades do not break integration
   - API versions tracked and managed
   - Deprecation strategy documented

---

## Consumer Onboarding Checklist

For each new consumer, complete:

- [ ] Create API Product in API Management
- [ ] Define consumer-specific rate limits
- [ ] Configure OAuth/API Key credentials
- [ ] Document field mapping rules (VM_CONSUMER_MAPPING)
- [ ] Define business-specific defaults
- [ ] Configure callback URL (if asynchronous)
- [ ] Test end-to-end flow
- [ ] Configure monitoring and alerts
- [ ] Document consumer-specific error handling
- [ ] Train operations team

---

## Deployment Architecture

### SAP Cloud Integration (CPI) Deployment

- **Region**: [Specify customer region]
- **Tenant**: [Specify customer tenant]
- **Availability**: High Availability enabled
- **Backup**: Daily automated backups

### API Management Deployment

- **Plan**: API Management on SAP Cloud Platform
- **API Portal**: Enabled for consumer documentation
- [ **Developer Community**: Enabled for API feedback**

### Event Mesh (Future)

- Reserved for future event-driven patterns
- Configured for Sales Order lifecycle events
- Pub/Sub for consumer notifications

---

## Document References

**Baseline Documentation**:
- Oatey API Management Patterns & Best Practices
- SAP BTP Enterprise Integration Architecture

**Standards**:
- SAP Clean Core Guidelines
- API Management Best Practices
- Cloud Integration Security Guidelines

---

## Approval & Sign-Off

| Role | Name | Date | Signature |
|---|---|---|---|
| Enterprise Architect | | | |
| SAP BTP Practice Lead | | | |
| Project Lead | | | |
| Client Stakeholder | | | |

---

## Document History

| Version | Date | Author | Changes |
|---|---|---|---|
| 1.0 | 2026-06-01 | Architecture Team | Initial architecture definition |


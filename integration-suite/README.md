# Integration Suite

This directory contains all SAP Cloud Integration Platform (CPI) artifacts.

## Contents

- **Integration Flows (iFlows)** - Core message processing logic
- **Scripts** - Custom groovy/JavaScript processing
- **Message Mappings** - Consumer-to-SAP format transformations
- **Deployment** - Deployment configurations and parameters

## Core Integration Flows

### IFL_SO_INBOUND
**Purpose**: Inbound validation, normalization, and JMS queue publication

- Validates incoming payload
- Applies consumer-specific mapping rules
- Generates Correlation ID
- Publishes to JMS_SO_INBOUND queue
- Returns ACK to consumer

**Location**: `integration-suite/iflows/IFL_SO_INBOUND.iflow`

### IFL_SO_ORCHESTRATION
**Purpose**: Message consumption, transformation, and S/4HANA integration

- Consumes messages from JMS queue
- Transforms to SAP Sales Order API format
- Calls `C_SALESORDERAPI_ORDERS` API
- Processes responses
- Triggers A2A callback

**Location**: `integration-suite/iflows/IFL_SO_ORCHESTRATION.iflow`

### SP_EXCEPTION_HANDLER
**Purpose**: Centralized exception handling and alerting

- Traps all technical exceptions
- Logs errors with Correlation ID
- Triggers alerts to operations
- Routes non-recoverable errors to DLQ

**Location**: `integration-suite/scripts/SP_EXCEPTION_HANDLER.js`

## Message Mappings

- `VM_CONSUMER_MAPPING` - Consumer field mapping rules
- `VM_SAP_SALES_ORDER_MAPPING` - Canonical to SAP format transformation

**Location**: `integration-suite/mappings/`

## Scripts

### SCR_CORRELATION_ID
Generate unique Correlation ID for message tracking

**Location**: `integration-suite/scripts/SCR_CORRELATION_ID.js`

### SCR_RETRY_HANDLER
Exponential backoff retry logic for failed messages

**Location**: `integration-suite/scripts/SCR_RETRY_HANDLER.js`

## Deployment

### Development
- Tenant: [Dev tenant name]
- URL: [Dev tenant URL]

### Production
- Tenant: [Prod tenant name]
- URL: [Prod tenant URL]
- Backup: Daily automated

**Deployment Checklist**:
- [ ] iFlows exported and tested
- [ ] Message mappings validated
- [ ] Security policies configured
- [ ] Monitoring alerts configured
- [ ] Operations team trained

## Configuration Parameters

JMS Queue Configuration:
- Queue name: `JMS_SO_INBOUND`
- Dead-letter queue: `DLQ_SO_INBOUND`
- Message TTL: 7 days
- Max retries: 3

Retry Strategy:
- Retry 1: 5 seconds
- Retry 2: 25 seconds
- Retry 3: 125 seconds

## Monitoring

All iFlows include:
- Correlation ID logging
- Consumer tracking
- Processing status updates
- Error classification and alerting

**Dashboard**: Integration Suite Monitoring Portal

See `architecture/architecture.md` for detailed specifications.

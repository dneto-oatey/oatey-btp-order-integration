# OpenAPI Specifications

This directory contains OpenAPI (Swagger) specifications for all consumer-facing APIs.

## API Specifications

### 1. Sales Order Inbound API (Asynchronous Consumers)

**File**: `sales-order-inbound-api.yaml`

**Purpose**: Receive sales orders from external consumers

**Consumers**:
- Epicor HQ / EDI Translator
- Partner integrations
- Future consumers

**Endpoint**: `/api/v1/sales-orders`

**Operations**:
- `POST /sales-orders` - Submit new sales order
- Response: ACK with Correlation ID

**Authentication**: OAuth 2.0 / API Key

**Rate Limiting**: Per-consumer quotas

---

### 2. Sales Rep Portal API (Synchronous)

**File**: `sales-rep-portal-api.yaml`

**Purpose**: Real-time sales order creation for internal portal

**Consumers**: Sales Rep Portal (internal)

**Endpoint**: `/api/v1/portal/sales-orders`

**Operations**:
- `POST /portal/sales-orders` - Create order (synchronous)
- Response: Order confirmation with order number

**Authentication**: OAuth 2.0

**Latency**: < 2 seconds (synchronous direct to S/4HANA)

---

### 3. Callback Notification API

**File**: `callback-notification-api.yaml`

**Purpose**: Notify consumers of order processing status

**Called By**: A2A Callback iFlow

**Consumer Endpoints**: Registered per consumer

**Operations**:
- `POST {consumer_callback_url}` - Deliver order confirmation
- Payload: Order number, status, correlation ID

---

## OpenAPI Versions

- **OpenAPI 3.0.0** - All specifications
- **Swagger UI**: Available in API Management Developer Portal

## Using These Specifications

### Generate Client Libraries
```bash
# Example: Generate Node.js client
npx openapi-generator-cli generate -i sales-order-inbound-api.yaml -g nodejs
```

### API Testing
- Import YAML into Postman
- Use Swagger UI for interactive testing
- Configure OAuth credentials per environment

### Consumer Integration
1. Review API specification (YAML)
2. Understand required fields and error responses
3. Implement consumer client
4. Test in sandbox environment
5. Deploy to production

## API Contract Governance

**Changes**:
- Breaking changes require new API version (v2, v3, etc.)
- Backward-compatible additions allowed in current version
- Deprecation notice required 6 months before removal

**Support**:
- Multiple versions supported simultaneously during transition
- Version sunset date documented in API specification

## Files

```
openapi/
├── README.md (this file)
├── sales-order-inbound-api.yaml
├── sales-rep-portal-api.yaml
├── callback-notification-api.yaml
└── schemas/
    ├── sales-order-request.json
    ├── sales-order-response.json
    ├── callback-payload.json
    └── error-response.json
```

## Schema Definitions

Common request/response schemas stored in `/schemas`:

- `sales-order-request.json` - Inbound order payload structure
- `sales-order-response.json` - ACK response structure
- `callback-payload.json` - Confirmation payload structure
- `error-response.json` - Standard error response

These schemas are referenced in OpenAPI specifications using `$ref`.

## Validation

All APIs validate:
- Required fields presence
- Data types and formats
- Enum values
- Minimum/maximum constraints
- Pattern matching (regex)

See error responses in specifications for validation error details.

## Example Usage

See implementation guides in `/docs` for:
- Sample requests/responses
- Error handling examples
- Integration code snippets
- Testing procedures

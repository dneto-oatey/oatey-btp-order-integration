# API Management

API Management remains the planned production entry point for the approved Oatey SAP BTP inbound Sales Order architecture.

## Current POC Runtime

The IFL_SO_INBOUND POC was validated directly against the SAP Integration Suite Runtime Endpoint using OAuth2 Client Credentials.

## Future Production Runtime

In production, Integration Suite remains behind SAP API Management. API Management will provide:

| Capability | Owner |
| --- | --- |
| OAuth Enforcement | API Management |
| Consumer Separation | API Management API Products |
| Rate Limiting | API Management |
| Spike Arrest | API Management |
| Analytics | API Management |
| Runtime orchestration | Integration Suite, not APIM |

## Header Strategy

X-Correlation-ID is optional. APIM should pass it through when provided. CPI generates correlationId when missing.

X-Consumer-ID is optional in the POC and defaults to UNKNOWN_CONSUMER. In future APIM runtime, APIM should inject X-Consumer-ID based on the authenticated application/API Product.

Idempotency-Key is optional in the POC. In future APIM runtime, consumers may provide it or APIM may generate it as a fallback. Future production may enforce mandatory idempotency.

## Out Of Scope For APIM

APIM must not perform payload mapping, orchestration, retry processing, DLQ handling, payload persistence, CAP behavior, PostgreSQL behavior, Event Mesh behavior, or UI behavior.

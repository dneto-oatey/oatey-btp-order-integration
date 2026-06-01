# Documentation

Comprehensive documentation for the Oatey SAP BTP Sales Order Integration project.

## Contents

### Architecture
- `architecture.md` - High-level architecture and design principles
- `inbound-flow.md` - Detailed inbound processing flow
- `outbound-flow.md` - Callback and confirmation processing (future)

### Implementation Guides
- `api-management-setup.md` - APIM configuration guide
- `integration-suite-setup.md` - CPI deployment and configuration
- `jms-queue-setup.md` - Message queue configuration
- `s4hana-api-integration.md` - S/4HANA API integration details

### API Specifications
- `sales-order-api-contract.md` - Inbound API contract
- `callback-api-contract.md` - Callback API contract
- `error-codes.md` - Standard error codes and handling

### Operational Guides
- `monitoring-setup.md` - Configure monitoring and alerting
- `troubleshooting.md` - Common issues and resolution
- `runbook.md` - Operational procedures
- `consumer-onboarding.md` - Adding new consumers

### Reference Documents
- `glossary.md` - Terms and abbreviations
- `contact-list.md` - Project team and escalation contacts

## Quick Links

- **Architecture Master**: [architecture/architecture.md](../architecture/architecture.md)
- **Inbound Flow Details**: [architecture/inbound-flow.md](../architecture/inbound-flow.md)
- **OpenAPI Specifications**: [../openapi/](../openapi/)

## Document Status

| Document | Status | Owner | Last Updated |
|----------|--------|-------|--------------|
| architecture.md | ✅ Complete | Architecture Team | 2026-06-01 |
| inbound-flow.md | ✅ Complete | Architecture Team | 2026-06-01 |
| Implementation Guides | ⏳ In Progress | Dev Team | - |
| API Specifications | ⏳ In Progress | Dev Team | - |
| Operational Guides | ⏳ To Do | Ops Team | - |

## How to Use This Documentation

1. **Architecture Review** → Start with `architecture.md`
2. **Understand Flows** → Read `inbound-flow.md`
3. **Implementation** → Follow implementation guides in this directory
4. **API Integration** → Reference OpenAPI specs in `/openapi`
5. **Operations** → Consult operational guides and runbooks

## Contributing

When updating documentation:
- Maintain Markdown format
- Update the status table above
- Add your name and date
- Cross-reference related documents
- Include examples where applicable

## Standards

All documentation follows:
- Clear structure with headers
- Markdown formatting
- Code examples in fenced blocks
- Links to related documents
- Version control via Git

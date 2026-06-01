# IFL_SO_ORCHESTRATION Design

Purpose: consume JMS_SO_INBOUND, transform payloads, call the standard SAP Sales Order API, classify outcomes, and trigger callbacks.

Flow:
1. Read queued message.
2. 2. Check idempotency.
   3. 3. Map to SAP Sales Order API.
      4. 4. Call standard SAP API only.
         5. 5. Retry transient/system failures.
            6. 6. Route exhausted failures to DLQ_SO_INBOUND.
              
               7. Out of scope: RFC, BAPI, custom Z services, CAP, PostgreSQL, Event Mesh, UI.
               8. 

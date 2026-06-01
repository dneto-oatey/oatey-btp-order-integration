# IFL_SO_INBOUND Design

Purpose: validate asynchronous inbound sales order requests and publish accepted messages to JMS_SO_INBOUND.

Flow:
1. Receive request from SAP API Management.
2. 2. Validate required headers and payload fields.
   3. 3. Preserve or generate correlationId.
      4. 4. Require Idempotency-Key.
         5. 5. Add consumer metadata.
            6. 6. Publish to JMS_SO_INBOUND.
               7. 7. Return HTTP 202 ACK.
                 
                  8. Out of scope: SAP calls, persistence, CAP, UI.
                  9. 

# JMS_SO_INBOUND

Purpose: durable queue for accepted asynchronous inbound sales order messages.

Configuration:
- Queue: JMS_SO_INBOUND
- - Persistence: enabled
  - - TTL: 7 days
    - - Max retries before DLQ: 3
      - - DLQ: DLQ_SO_INBOUND
       
        - Producer: IFL_SO_INBOUND.
        - Consumer: IFL_SO_ORCHESTRATION.
        - 

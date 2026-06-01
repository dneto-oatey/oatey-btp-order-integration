# JMS_SO_INBOUND

Purpose: durable queue for accepted asynchronous inbound sales order messages.

Configuration: queue name JMS_SO_INBOUND, persistence enabled, TTL seven days, maximum retries before DLQ is three, and dead-letter queue DLQ_SO_INBOUND.

Producer: IFL_SO_INBOUND.

Consumer: IFL_SO_ORCHESTRATION.

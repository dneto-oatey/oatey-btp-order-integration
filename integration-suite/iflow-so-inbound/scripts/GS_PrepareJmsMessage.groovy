import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
      Map props = message.getProperties()

      requireProperty(message, props, 'correlationId')

      setHeaderFromProperty(message, props, 'X-Correlation-ID', 'correlationId')
      setHeaderFromProperty(message, props, 'Idempotency-Key', 'idempotencyKey')
      setHeaderFromProperty(message, props, 'X-Consumer-ID', 'consumerId')
      setHeaderFromProperty(message, props, 'correlationId', 'correlationId')
      setHeaderFromProperty(message, props, 'idempotencyKey', 'idempotencyKey')
      setHeaderFromProperty(message, props, 'consumerId', 'consumerId')
      setHeaderFromProperty(message, props, 'purchaseOrderByCustomer', 'purchaseOrderByCustomer')
      setHeaderFromProperty(message, props, 'soldToParty', 'soldToParty')
      setHeaderFromProperty(message, props, 'itemCount', 'itemCount')
      setHeaderFromProperty(message, props, 'inboundReceivedAt', 'inboundReceivedAt')
      setHeaderFromProperty(message, props, 'salesOrderType', 'salesOrderType')
      setHeaderFromProperty(message, props, 'salesOrganization', 'salesOrganization')
      setHeaderFromProperty(message, props, 'distributionChannel', 'distributionChannel')
      setHeaderFromProperty(message, props, 'incotermsClassification', 'incotermsClassification')

      return message
}

void requireProperty(Message message, Map props, String name) {
      if (!props[name]?.toString()?.trim()) {
                controlledError(message, 'TECHNICAL_ERROR', 'TECHNICAL', 'Required property missing before JMS publish: ' + name, '500')
      }
}

void setHeaderFromProperty(Message message, Map props, String headerName, String propertyName) {
      def value = props[propertyName]
      message.setHeader(headerName, value == null ? '' : value.toString())
}

void controlledError(Message message, String code, String category, String text, String status) {
      message.setProperty('errorCode', code)
      message.setProperty('errorCategory', category)
      message.setProperty('errorMessage', text)
      message.setProperty('httpStatus', status)
      throw new RuntimeException(text)
}

import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
      Map headers = message.getHeaders()

      String contentType = firstHeader(headers, 'Content-Type')
      String idempotencyKey = firstHeader(headers, 'Idempotency-Key')
      String consumerId = firstHeader(headers, 'X-Consumer-ID')
      String correlationHeader = firstHeader(headers, 'X-Correlation-ID')

      if (!contentType || !contentType.toLowerCase().contains('application/json')) {
                controlledError(message, 'INVALID_CONTENT_TYPE', 'VALIDATION', 'Content-Type must contain application/json', '400')
      }

      message.setProperty('rawContentType', contentType)
      message.setProperty('idempotencyKey', idempotencyKey ?: '')
      message.setProperty('consumerId', consumerId ?: 'UNKNOWN_CONSUMER')
      message.setProperty('receivedCorrelationHeader', correlationHeader ?: '')
      message.setProperty('validationStatus', 'HEADER_VALIDATED')

      return message
}

String firstHeader(Map headers, String name) {
      def value = headers[name]
      if (value == null) {
                value = headers.find { k, v -> k?.toString()?.equalsIgnoreCase(name) }?.value
      }
      return value == null ? null : value.toString().trim()
}

void controlledError(Message message, String code, String category, String text, String status) {
      message.setProperty('errorCode', code)
      message.setProperty('errorCategory', category)
      message.setProperty('errorMessage', text)
      message.setProperty('httpStatus', status)
      throw new RuntimeException(text)
}

import com.sap.gateway.ip.core.customdev.util.Message
import java.util.UUID

def Message processData(Message message) {
      Map headers = message.getHeaders()
      String correlationId = firstHeader(headers, 'X-Correlation-ID')

      if (!correlationId) {
                correlationId = UUID.randomUUID().toString()
      }

      message.setProperty('correlationId', correlationId)
      message.setHeader('X-Correlation-ID', correlationId)

      return message
}

String firstHeader(Map headers, String name) {
      def value = headers[name]
      if (value == null) {
                value = headers.find { k, v -> k?.toString()?.equalsIgnoreCase(name) }?.value
      }
      return value == null ? null : value.toString().trim()
}

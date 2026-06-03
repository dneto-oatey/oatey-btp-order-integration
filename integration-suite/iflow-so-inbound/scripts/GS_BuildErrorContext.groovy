import com.sap.gateway.ip.core.customdev.util.Message
import java.util.UUID

def Message processData(Message message) {
      Map props = message.getProperties()
      Exception caught = props['CamelExceptionCaught'] as Exception

      String correlationId = stringProp(props, 'correlationId')
      if (!correlationId) {
                correlationId = UUID.randomUUID().toString()
                message.setProperty('correlationId', correlationId)
                message.setHeader('X-Correlation-ID', correlationId)
      }

      String errorCode = stringProp(props, 'errorCode') ?: classifyCode(caught)
      String errorCategory = stringProp(props, 'errorCategory') ?: classifyCategory(errorCode)
      String httpStatus = stringProp(props, 'httpStatus') ?: classifyStatus(errorCode)
      String errorMessage = stringProp(props, 'errorMessage') ?: safeMessage(caught)

      message.setProperty('errorCode', errorCode)
      message.setProperty('errorCategory', errorCategory)
      message.setProperty('errorMessage', errorMessage)
      message.setProperty('httpStatus', httpStatus)
      message.setProperty('validationStatus', errorCategory == 'VALIDATION' ? 'REJECTED' : 'FAILED')
      message.setHeader('CamelHttpResponseCode', httpStatus)
      message.setHeader('Content-Type', 'application/json')
      message.setHeader('SAP_MessageProcessingLogCustomStatus', errorCategory == 'VALIDATION' ? 'REJECTED' : 'FAILED')

      return message
}

String stringProp(Map props, String name) {
      def value = props[name]
      return value == null ? '' : value.toString().trim()
}

String classifyCode(Exception e) {
      String text = safeMessage(e).toLowerCase()
      if (text.contains('json')) return 'INVALID_JSON'
      if (text.contains('schema') || text.contains('validation')) return 'PAYLOAD_VALIDATION_FAILED'
      if (text.contains('jms')) return 'JMS_PUBLISH_FAILED'
      return 'TECHNICAL_ERROR'
}

String classifyCategory(String code) {
      return ['INVALID_CONTENT_TYPE', 'INVALID_JSON', 'PAYLOAD_VALIDATION_FAILED'].contains(code) ? 'VALIDATION' : 'TECHNICAL'
}

String classifyStatus(String code) {
      if (code == 'PAYLOAD_VALIDATION_FAILED') return '422'
      if (['INVALID_CONTENT_TYPE', 'INVALID_JSON'].contains(code)) return '400'
      return '500'
}

String safeMessage(Exception e) {
      return e?.message ?: 'Unhandled integration error'
}

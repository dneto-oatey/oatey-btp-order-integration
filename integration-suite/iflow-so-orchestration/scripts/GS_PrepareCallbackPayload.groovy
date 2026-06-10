import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

def Message processData(Message message) {
    def props = message.getProperties()
    def correlationId = value(props.get('correlationId'))
    def consumerId = value(props.get('consumerId')) ?: 'UNKNOWN_CONSUMER'
    def processingStatus = value(props.get('processingStatus')) ?: 'FAILED'
    def sapSalesOrderNumber = value(props.get('sapSalesOrderNumber'))

    if (!correlationId) {
        fail(message, 'CALLBACK_ERROR', 'CALLBACK_MISSING_CORRELATION_ID', 'Cannot build callback payload without correlationId.', false)
    }

    def callbackStatus = processingStatus == 'SUCCESS' ? 'SUCCESS' : 'FAILED'
    def payload = [
        correlationId       : correlationId,
        consumerId          : consumerId,
        status              : callbackStatus,
        processingTimestamp : new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
    ]

    if (callbackStatus == 'SUCCESS') {
        payload.salesOrderNumber = sapSalesOrderNumber
        payload.errors = []
    } else {
        payload.errors = [[
            errorCategory : value(props.get('errorCategory')),
            errorCode     : value(props.get('errorCode')),
            errorMessage  : value(props.get('errorMessage')),
            sapErrorCode  : value(props.get('sapErrorCode')),
            sapErrorMessage: value(props.get('sapErrorMessage'))
        ]]
    }

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(payload)))
    message.setProperty('callbackPayloadPrepared', 'true')
    message.setProperty('callbackStatus', 'PENDING')
    return message
}

def value(def input) {
    return input == null ? '' : input.toString().trim()
}

def fail(Message message, String category, String code, String text, boolean retryable) {
    message.setProperty('errorCategory', category)
    message.setProperty('errorCode', code)
    message.setProperty('errorMessage', text)
    message.setProperty('retryable', String.valueOf(retryable))
    message.setProperty('processingStatus', 'FAILED')
    throw new RuntimeException("${category}:${code}:${text}")
}

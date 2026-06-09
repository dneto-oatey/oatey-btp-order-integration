import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

import java.text.SimpleDateFormat

final String SOURCE_IFLOW = 'IFL_SO_ORCHESTRATION'
final String SOURCE_QUEUE = 'JMS_SO_INBOUND'
final String TARGET_QUEUE = 'DLQ_SO_INBOUND'
final String REPLAY_INSTRUCTION = 'Reprocess only through IFL_SO_ORCHESTRATION after root cause validation and idempotency review'

def Message processData(Message message) {
    def props = message.getProperties()
    def headers = message.getHeaders()
    def originalPayload = value(message.getBody(String))
    def exception = props.get('CamelExceptionCaught') ?: props.get('CamelException')
    def exceptionMessage = sanitize(exception instanceof Throwable ? exception.getMessage() : value(exception))
    def exceptionClass = exception instanceof Throwable ? value(exception.getClass().getName()) : ''

    def sapResponseStatusCode = value(props.get('sapResponseStatusCode')) ?: value(props.get('CamelHttpResponseCode')) ?: value(props.get('CamelHttpResponseStatusCode'))
    def errorCode = sanitize(value(props.get('errorCode')))
    def sapErrorCode = sanitize(value(props.get('sapErrorCode')))
    def sapErrorMessage = sanitize(value(props.get('sapErrorMessage')))
    def errorMessage = sanitize(value(props.get('errorMessage')) ?: exceptionMessage ?: sapErrorMessage ?: 'Unhandled orchestration error routed to DLQ.')
    def validationStatus = value(props.get('validationStatus'))
    def errorCategory = classifyError(value(props.get('errorCategory')), sapResponseStatusCode, exceptionMessage, validationStatus, errorCode)

    def replayCount = integerValue(firstAvailable(props, headers, 'replayCount'), 0)
    def maxReplayCount = integerValue(firstAvailable(props, headers, 'maxReplayCount'), 1)
    def replayed = value(firstAvailable(props, headers, 'replayed'))
    def replayedAt = value(firstAvailable(props, headers, 'replayedAt'))
    def replaySource = value(firstAvailable(props, headers, 'replaySource'))
    def replayTarget = value(firstAvailable(props, headers, 'replayTarget'))
    def replayFlow = value(firstAvailable(props, headers, 'replayFlow'))

    if (!errorCode) {
        errorCode = defaultErrorCode(errorCategory, sapResponseStatusCode)
    }

    def dlqPayload = [
        sourceIFlow          : SOURCE_IFLOW,
        sourceQueue          : value(props.get('sourceQueueName')) ?: SOURCE_QUEUE,
        targetQueue          : value(props.get('dlqQueueName')) ?: TARGET_QUEUE,
        correlationId        : value(props.get('correlationId')),
        consumerId           : value(props.get('consumerId')),
        idempotencyKey       : value(props.get('idempotencyKey')),
        processingStatus     : 'DLQ_ROUTED',
        failureTimestamp     : utcNow(),
        errorCategory        : errorCategory,
        errorCode            : errorCode,
        errorMessage         : errorMessage,
        sapResponseStatusCode: sapResponseStatusCode,
        sapErrorCode         : sapErrorCode,
        sapErrorMessage      : sapErrorMessage,
        retryAttempt         : value(props.get('retryAttempt')),
        maxRetryCount        : value(props.get('maxRetryCount')),
        replayRequired       : true,
        replayInstruction    : REPLAY_INSTRUCTION,
        replayCount          : replayCount,
        maxReplayCount       : maxReplayCount,
        originalPayload      : originalPayload
    ]

    if (replayed) {
        dlqPayload.replayed = replayed
    }
    if (replayedAt) {
        dlqPayload.replayedAt = replayedAt
    }
    if (replaySource) {
        dlqPayload.replaySource = replaySource
    }
    if (replayTarget) {
        dlqPayload.replayTarget = replayTarget
    }
    if (replayFlow) {
        dlqPayload.replayFlow = replayFlow
    }

    if (exceptionClass) {
        dlqPayload.exceptionClass = sanitize(exceptionClass)
    }

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(dlqPayload)))
    message.setProperty('processingStatus', 'DLQ_ROUTED')
    message.setProperty('errorCategory', errorCategory)
    message.setProperty('errorCode', errorCode)
    message.setProperty('errorMessage', errorMessage)
    message.setProperty('failureTimestamp', dlqPayload.failureTimestamp)
    message.setProperty('replayCount', String.valueOf(replayCount))
    message.setProperty('maxReplayCount', String.valueOf(maxReplayCount))
    message.setProperty('dlqPayloadPrepared', 'true')
    return message
}

def classifyError(String existingCategory, String statusCodeText, String exceptionMessage, String validationStatus, String errorCode) {
    if (existingCategory) {
        return existingCategory
    }

    int statusCode = toInteger(statusCodeText)
    if ([400, 409, 422].contains(statusCode)) {
        return 'SAP_BUSINESS_ERROR'
    }
    if ([401, 403].contains(statusCode)) {
        return 'SAP_AUTH_CONFIG_ERROR'
    }
    if ([408, 429, 500, 502, 503, 504].contains(statusCode)) {
        return 'SAP_TRANSIENT_ERROR'
    }

    def lowerException = value(exceptionMessage).toLowerCase()
    if (lowerException.contains('timeout') || lowerException.contains('timed out') || lowerException.contains('connection') || lowerException.contains('connect refused') || lowerException.contains('connection reset')) {
        return 'SAP_TRANSIENT_ERROR'
    }

    def lowerCode = value(errorCode).toLowerCase()
    if (value(validationStatus).equalsIgnoreCase('FAILED') || lowerCode.contains('invalid_json') || lowerCode.contains('missing_') || lowerCode.contains('invalid_sap_order_payload')) {
        return 'VALIDATION_ERROR'
    }

    return 'TECHNICAL_ERROR'
}

def defaultErrorCode(String errorCategory, String statusCodeText) {
    int statusCode = toInteger(statusCodeText)
    if (statusCode > 0) {
        return "${errorCategory}_${statusCode}"
    }
    return "${errorCategory}_UNSPECIFIED"
}

def firstAvailable(Map props, Map headers, String name) {
    def propertyValue = props.get(name)
    if (value(propertyValue)) {
        return propertyValue
    }
    def headerValue = headers.get(name)
    if (value(headerValue)) {
        return headerValue
    }
    return null
}

def integerValue(def input, int defaultValue) {
    try {
        def text = value(input)
        if (!text) {
            return defaultValue
        }
        return text.toInteger()
    } catch (Exception ignored) {
        return defaultValue
    }
}

def toInteger(String input) {
    try {
        return value(input).toInteger()
    } catch (Exception ignored) {
        return 0
    }
}

def utcNow() {
    def formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    formatter.setTimeZone(TimeZone.getTimeZone('UTC'))
    return formatter.format(new Date())
}

def value(def input) {
    return input == null ? '' : input.toString().trim()
}

def sanitize(String input) {
    def text = value(input)
    if (!text) {
        return ''
    }
    text = text.replaceAll(/(?i)(authorization\s*[:=]\s*)[^\s,;]+/, '$1***')
    text = text.replaceAll(/(?i)(bearer\s+)[A-Za-z0-9._\-~+\/]+=*/, '$1***')
    text = text.replaceAll(/(?i)((?:access_token|refresh_token|id_token|token|password|passwd|pwd|client_secret|secret)\s*[:=]\s*)[^\s,;]+/, '$1***')
    return text
}

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import java.io.Reader


def Message processData(Message message) {
    String originalPayload = safeString(message.getProperty('originalPayload'))

    if (originalPayload.length() == 0) {
        originalPayload = extractOriginalPayloadFromBody(message)
    }

    if (originalPayload.length() == 0) {
        failExtraction(message, 'MISSING_ORIGINAL_PAYLOAD', 'Unable to extract originalPayload from DLQ envelope')
    }

    String correlationId = safeString(message.getProperty('correlationId'))
    String consumerId = safeString(message.getProperty('consumerId'))
    String idempotencyKey = safeString(message.getProperty('idempotencyKey'))
    String replayedAt = safeString(message.getProperty('replayedAt'))

    if (consumerId.length() == 0) {
        consumerId = 'UNKNOWN_CONSUMER'
    }

    message.setBody(originalPayload)

    Map headers = message.getHeaders()
    headers.put('correlationId', correlationId)
    headers.put('consumerId', consumerId)
    headers.put('idempotencyKey', idempotencyKey)
    headers.put('replayed', 'true')
    headers.put('replayedAt', replayedAt)
    headers.put('replaySource', 'DLQ_SO_INBOUND')
    headers.put('replayTarget', 'JMS_SO_INBOUND')
    headers.put('replayFlow', 'IFL_SO_REPROCESS_DLQ')
    message.setHeaders(headers)

    message.setProperty('consumerId', consumerId)
    message.setProperty('idempotencyKey', idempotencyKey)
    message.setProperty('originalPayloadExtractionStatus', 'SUCCESS')
    message.setProperty('processingStatus', 'ORIGINAL_PAYLOAD_EXTRACTED')

    return message
}


String extractOriginalPayloadFromBody(Message message) {
    try {
        Reader reader = message.getBody(java.io.Reader)
        def dlqEnvelope = new JsonSlurper().parse(reader)
        if (dlqEnvelope instanceof Map) {
            return safeString(dlqEnvelope.originalPayload)
        }
        return ''
    } catch (Exception ignored) {
        return ''
    }
}


String safeString(def value) {
    if (value == null) {
        return ''
    }
    return String.valueOf(value).trim()
}


void failExtraction(Message message, String errorCode, String errorMessage) {
    message.setProperty('errorCategory', 'REPLAY_EXTRACTION_ERROR')
    message.setProperty('errorCode', errorCode)
    message.setProperty('errorMessage', errorMessage)
    message.setProperty('processingStatus', 'REPLAY_REJECTED')
    throw new RuntimeException(errorMessage)
}

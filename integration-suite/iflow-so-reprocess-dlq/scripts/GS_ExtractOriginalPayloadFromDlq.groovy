import com.sap.gateway.ip.core.customdev.util.Message
import java.time.Instant


def Message processData(Message message) {
    String originalPayload = safeString(message.getProperty('originalPayload'))

    if (originalPayload.length() == 0) {
        failExtraction(message, 'MISSING_ORIGINAL_PAYLOAD', 'Unable to extract originalPayload from DLQ envelope')
    }

    String correlationId = safeString(message.getProperty('correlationId'))
    String consumerId = safeString(message.getProperty('consumerId'))
    String idempotencyKey = safeString(message.getProperty('idempotencyKey'))
    int previousReplayCount = integerValue(message.getProperty('replayCount'), 0)
    int nextReplayCount = previousReplayCount + 1
    String replayedAt = Instant.now().toString()

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
    headers.put('replayCount', String.valueOf(nextReplayCount))
    message.setHeaders(headers)

    message.setProperty('consumerId', consumerId)
    message.setProperty('idempotencyKey', idempotencyKey)
    message.setProperty('replayed', 'true')
    message.setProperty('replayedAt', replayedAt)
    message.setProperty('replaySource', 'DLQ_SO_INBOUND')
    message.setProperty('replayTarget', 'JMS_SO_INBOUND')
    message.setProperty('replayFlow', 'IFL_SO_REPROCESS_DLQ')
    message.setProperty('replayCount', String.valueOf(nextReplayCount))
    message.setProperty('originalPayloadExtractionStatus', 'SUCCESS')
    message.setProperty('processingStatus', 'ORIGINAL_PAYLOAD_EXTRACTED')

    return message
}


String safeString(def value) {
    if (value == null) {
        return ''
    }
    return String.valueOf(value).trim()
}


int integerValue(def value, int defaultValue) {
    String text = safeString(value)
    if (text.length() == 0) {
        return defaultValue
    }
    try {
        return Integer.parseInt(text)
    } catch (Exception ignored) {
        return defaultValue
    }
}


void failExtraction(Message message, String errorCode, String errorMessage) {
    message.setProperty('errorCategory', 'REPLAY_EXTRACTION_ERROR')
    message.setProperty('errorCode', errorCode)
    message.setProperty('errorMessage', errorMessage)
    message.setProperty('processingStatus', 'REPLAY_REJECTED')
    throw new RuntimeException(errorMessage)
}

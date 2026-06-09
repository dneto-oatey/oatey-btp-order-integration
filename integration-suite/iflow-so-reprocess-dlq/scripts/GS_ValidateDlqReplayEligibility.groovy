import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import java.io.Reader
import java.time.Instant


def Message processData(Message message) {
    try {
        Reader reader = message.getBody(java.io.Reader)
        def dlqEnvelope = new JsonSlurper().parse(reader)

        if (!(dlqEnvelope instanceof Map)) {
            failReplay(message, 'INVALID_DLQ_ENVELOPE', 'DLQ replay message body must be a JSON object')
        }

        String originalPayload = safeString(dlqEnvelope.originalPayload)
        String correlationId = safeString(dlqEnvelope.correlationId)
        String consumerId = safeString(dlqEnvelope.consumerId)
        String idempotencyKey = safeString(dlqEnvelope.idempotencyKey)
        String replayRequired = safeString(dlqEnvelope.replayRequired)
        String replayed = safeString(dlqEnvelope.replayed)

        if (originalPayload.length() == 0) {
            failReplay(message, 'MISSING_ORIGINAL_PAYLOAD', 'DLQ envelope does not contain originalPayload for replay')
        }

        if (correlationId.length() == 0) {
            failReplay(message, 'MISSING_CORRELATION_ID', 'DLQ envelope does not contain correlationId for replay traceability')
        }

        if (consumerId.length() == 0) {
            consumerId = 'UNKNOWN_CONSUMER'
        }

        if ('false'.equalsIgnoreCase(replayRequired)) {
            failReplay(message, 'REPLAY_NOT_REQUIRED', 'DLQ envelope is marked as not requiring replay')
        }

        if ('true'.equalsIgnoreCase(replayed)) {
            failReplay(message, 'REPLAY_ALREADY_PROCESSED', 'DLQ envelope is already marked as replayed')
        }

        String replayedAt = Instant.now().toString()

        message.setProperty('correlationId', correlationId)
        message.setProperty('consumerId', consumerId)
        message.setProperty('idempotencyKey', idempotencyKey)
        message.setProperty('originalPayload', originalPayload)
        message.setProperty('replayed', 'true')
        message.setProperty('replayedAt', replayedAt)
        message.setProperty('replaySource', 'DLQ_SO_INBOUND')
        message.setProperty('replayTarget', 'JMS_SO_INBOUND')
        message.setProperty('replayFlow', 'IFL_SO_REPROCESS_DLQ')
        message.setProperty('replayValidationStatus', 'SUCCESS')
        message.setProperty('processingStatus', 'REPLAY_VALIDATED')

        return message
    } catch (Exception e) {
        if (safeString(message.getProperty('errorCategory')).length() == 0) {
            message.setProperty('errorCategory', 'REPLAY_VALIDATION_ERROR')
            message.setProperty('errorCode', 'INVALID_DLQ_JSON')
            message.setProperty('errorMessage', safeExceptionMessage(e, 'DLQ replay message body is not valid JSON'))
            message.setProperty('processingStatus', 'REPLAY_REJECTED')
        }
        throw e
    }
}


String safeString(def value) {
    if (value == null) {
        return ''
    }
    return String.valueOf(value).trim()
}


String safeExceptionMessage(Exception e, String fallbackMessage) {
    String message = e == null ? '' : safeString(e.getMessage())
    if (message.length() == 0) {
        return fallbackMessage
    }
    return message
}


void failReplay(Message message, String errorCode, String errorMessage) {
    message.setProperty('errorCategory', 'REPLAY_VALIDATION_ERROR')
    message.setProperty('errorCode', errorCode)
    message.setProperty('errorMessage', errorMessage)
    message.setProperty('processingStatus', 'REPLAY_REJECTED')
    throw new RuntimeException(errorMessage)
}

import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import java.io.Reader
import java.io.StringReader


def Message processData(Message message) {
    String envelopeText = readBody(message)
    message.setProperty('originalDlqEnvelope', envelopeText)
    message.setProperty('replayFlow', 'IFL_SO_REPROCESS_DLQ')
    message.setProperty('replaySource', 'DLQ_SO_INBOUND')

    try {
        def dlqEnvelope = new JsonSlurper().parse(new StringReader(envelopeText))

        if (!(dlqEnvelope instanceof Map)) {
            markRejected(message, 'INVALID_DLQ_ENVELOPE', 'DLQ replay message body must be a JSON object')
            return message
        }

        String originalPayload = safeString(dlqEnvelope.originalPayload)
        String correlationId = safeString(dlqEnvelope.correlationId)
        String consumerId = safeString(dlqEnvelope.consumerId)
        String idempotencyKey = safeString(dlqEnvelope.idempotencyKey)
        String errorCategory = safeString(dlqEnvelope.errorCategory)
        boolean replayApproved = booleanValue(dlqEnvelope.replayApproved, false)
        boolean businessCorrectionConfirmed = booleanValue(dlqEnvelope.businessCorrectionConfirmed, false)
        boolean validationReplayApproved = booleanValue(dlqEnvelope.validationReplayApproved, false)
        int replayCount = integerValue(dlqEnvelope.replayCount, 0)
        int maxReplayCount = integerValue(dlqEnvelope.maxReplayCount, 1)

        if (consumerId.length() == 0) {
            consumerId = 'UNKNOWN_CONSUMER'
        }

        message.setProperty('correlationId', correlationId)
        message.setProperty('consumerId', consumerId)
        message.setProperty('idempotencyKey', idempotencyKey)
        message.setProperty('originalPayload', originalPayload)
        message.setProperty('errorCategory', errorCategory)
        message.setProperty('replayApproved', String.valueOf(replayApproved))
        message.setProperty('businessCorrectionConfirmed', String.valueOf(businessCorrectionConfirmed))
        message.setProperty('validationReplayApproved', String.valueOf(validationReplayApproved))
        message.setProperty('replayCount', String.valueOf(replayCount))
        message.setProperty('maxReplayCount', String.valueOf(maxReplayCount))

        if (originalPayload.length() == 0) {
            markRejected(message, 'MISSING_ORIGINAL_PAYLOAD', 'DLQ envelope does not contain originalPayload for replay')
            return message
        }

        if (correlationId.length() == 0) {
            markRejected(message, 'MISSING_CORRELATION_ID', 'DLQ envelope does not contain correlationId for replay traceability')
            return message
        }

        if (!replayApproved) {
            markRejected(message, 'REPLAY_NOT_APPROVED', 'DLQ envelope replayApproved must be true before replay')
            return message
        }

        if (replayCount >= maxReplayCount) {
            markRejected(message, 'MAX_REPLAY_COUNT_REACHED', 'DLQ envelope replayCount is greater than or equal to maxReplayCount')
            return message
        }

        if ('SAP_AUTH_CONFIG_ERROR'.equalsIgnoreCase(errorCategory)) {
            markRejected(message, 'SAP_AUTH_CONFIG_ERROR_NOT_REPLAYABLE', 'SAP authentication or configuration errors must not be replayed by default')
            return message
        }

        if ('VALIDATION_ERROR'.equalsIgnoreCase(errorCategory) && !validationReplayApproved) {
            markRejected(message, 'VALIDATION_ERROR_NOT_APPROVED', 'Validation errors require explicit validationReplayApproved=true before replay')
            return message
        }

        if ('SAP_BUSINESS_ERROR'.equalsIgnoreCase(errorCategory) && !businessCorrectionConfirmed) {
            markRejected(message, 'BUSINESS_CORRECTION_NOT_CONFIRMED', 'SAP business errors require businessCorrectionConfirmed=true before replay')
            return message
        }

        markEligible(message)
        return message
    } catch (Exception e) {
        message.setProperty('errorCategory', 'REPLAY_VALIDATION_ERROR')
        markRejected(message, 'INVALID_DLQ_JSON', 'DLQ replay message body is not valid JSON')
        return message
    }
}


String readBody(Message message) {
    Reader reader = message.getBody(java.io.Reader)
    StringBuilder builder = new StringBuilder()
    char[] buffer = new char[4096]
    int length = reader.read(buffer)
    while (length != -1) {
        builder.append(buffer, 0, length)
        length = reader.read(buffer)
    }
    return builder.toString()
}


String safeString(def value) {
    if (value == null) {
        return ''
    }
    return String.valueOf(value).trim()
}


boolean booleanValue(def value, boolean defaultValue) {
    String text = safeString(value)
    if (text.length() == 0) {
        return defaultValue
    }
    return 'true'.equalsIgnoreCase(text)
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


void markEligible(Message message) {
    message.setProperty('replayEligible', 'true')
    message.setProperty('replayRejectionReason', '')
    message.setProperty('replayTarget', 'JMS_SO_INBOUND')
    message.setProperty('replayValidationStatus', 'SUCCESS')
    message.setProperty('processingStatus', 'REPLAY_ELIGIBLE')
}


void markRejected(Message message, String code, String reason) {
    message.setProperty('replayEligible', 'false')
    message.setProperty('replayRejectionCode', code)
    message.setProperty('replayRejectionReason', reason)
    message.setProperty('replayTarget', 'REJECTED_REPLAY_SO_INBOUND')
    message.setProperty('replayValidationStatus', 'FAILED')
    message.setProperty('processingStatus', 'REPLAY_REJECTED')
}

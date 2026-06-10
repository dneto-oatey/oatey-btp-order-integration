import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant


def Message processData(Message message) {
    String replayRejectedAt = Instant.now().toString()
    String correlationId = safeString(message.getProperty('correlationId'))
    String consumerId = safeString(message.getProperty('consumerId'))
    String idempotencyKey = safeString(message.getProperty('idempotencyKey'))
    String replayRejectionReason = safeString(message.getProperty('replayRejectionReason'))
    String replayRejectionCode = safeString(message.getProperty('replayRejectionCode'))
    String replayCount = safeString(message.getProperty('replayCount'))
    String maxReplayCount = safeString(message.getProperty('maxReplayCount'))
    String errorCategory = safeString(message.getProperty('errorCategory'))
    String originalDlqEnvelope = safeString(message.getProperty('originalDlqEnvelope'))

    if (consumerId.length() == 0) {
        consumerId = 'UNKNOWN_CONSUMER'
    }

    Map rejectedEnvelope = [
        replayRejected       : true,
        replayRejectedAt     : replayRejectedAt,
        replayRejectionCode  : replayRejectionCode,
        replayRejectionReason: replayRejectionReason,
        replayFlow           : 'IFL_SO_REPROCESS_DLQ',
        replaySource         : 'DLQ_SO_INBOUND',
        replayTarget         : 'REJECTED_REPLAY_SO_INBOUND',
        correlationId        : correlationId,
        consumerId           : consumerId,
        idempotencyKey       : idempotencyKey,
        replayCount          : replayCount,
        maxReplayCount       : maxReplayCount,
        errorCategory        : errorCategory,
        originalDlqEnvelope  : originalDlqEnvelope
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(rejectedEnvelope)))

    Map headers = message.getHeaders()
    headers.put('correlationId', correlationId)
    headers.put('consumerId', consumerId)
    headers.put('idempotencyKey', idempotencyKey)
    headers.put('replayEligible', 'false')
    headers.put('replayRejected', 'true')
    headers.put('replayRejectedAt', replayRejectedAt)
    headers.put('replayRejectionReason', replayRejectionReason)
    headers.put('replaySource', 'DLQ_SO_INBOUND')
    headers.put('replayTarget', 'REJECTED_REPLAY_SO_INBOUND')
    headers.put('replayFlow', 'IFL_SO_REPROCESS_DLQ')
    headers.put('replayCount', replayCount)
    message.setHeaders(headers)

    message.setProperty('consumerId', consumerId)
    message.setProperty('idempotencyKey', idempotencyKey)
    message.setProperty('replayRejected', 'true')
    message.setProperty('replayRejectedAt', replayRejectedAt)
    message.setProperty('replayTarget', 'REJECTED_REPLAY_SO_INBOUND')
    message.setProperty('replayFlow', 'IFL_SO_REPROCESS_DLQ')
    message.setProperty('processingStatus', 'REPLAY_REJECTED_PAYLOAD_PREPARED')

    return message
}


String safeString(def value) {
    if (value == null) {
        return ''
    }
    return String.valueOf(value).trim()
}

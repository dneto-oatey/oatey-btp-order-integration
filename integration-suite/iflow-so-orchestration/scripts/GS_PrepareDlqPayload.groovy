import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

def Message processData(Message message) {
    def props = message.getProperties()
    def originalPayload = message.getBody(String)

    def dlqPayload = [
        correlationId   : value(props.get('correlationId')),
        idempotencyKey  : value(props.get('idempotencyKey')),
        consumerId      : value(props.get('consumerId')) ?: 'UNKNOWN_CONSUMER',
        processingStatus: 'DLQ_ROUTED',
        failureTimestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC')),
        retryAttempt    : value(props.get('retryAttempt')),
        errorContext    : [
            errorCategory       : value(props.get('errorCategory')),
            errorCode           : value(props.get('errorCode')),
            errorMessage        : value(props.get('errorMessage')),
            sapResponseStatusCode: value(props.get('sapResponseStatusCode')),
            sapErrorCode        : value(props.get('sapErrorCode')),
            sapErrorMessage     : value(props.get('sapErrorMessage')),
            retryable           : value(props.get('retryable'))
        ],
        replayGuidance  : 'Review idempotency and SAP document status before replaying this message.',
        originalPayload : originalPayload
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(dlqPayload)))
    message.setProperty('processingStatus', 'DLQ_ROUTED')
    message.setProperty('dlqPayloadPrepared', 'true')
    return message
}

def value(def input) {
    return input == null ? '' : input.toString().trim()
}

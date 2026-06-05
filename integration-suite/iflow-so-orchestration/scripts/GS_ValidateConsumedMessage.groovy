import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def body = message.getBody(String)
    def props = message.getProperties()
    def headers = message.getHeaders()

    def correlationId = value(props.get('correlationId')) ?: value(headers.get('correlationId')) ?: value(headers.get('X-Correlation-ID'))
    def consumerId = value(props.get('consumerId')) ?: value(headers.get('consumerId')) ?: value(headers.get('X-Consumer-ID'))
    def idempotencyKey = value(props.get('idempotencyKey')) ?: value(headers.get('idempotencyKey')) ?: value(headers.get('Idempotency-Key'))

    if (!body?.trim()) {
        fail(message, 'VALIDATION_ERROR', 'MISSING_BODY', 'JMS message body is required for orchestration validation.', false)
    }

    def payload
    try {
        payload = new JsonSlurper().parseText(body)
    } catch (Exception e) {
        fail(message, 'VALIDATION_ERROR', 'INVALID_JSON', 'JMS message body is not valid JSON.', false)
    }

    if (!correlationId) {
        fail(message, 'VALIDATION_ERROR', 'MISSING_CORRELATION_ID', 'correlationId is required by IFL_SO_ORCHESTRATION.', false)
    }

    if (!consumerId) {
        fail(message, 'VALIDATION_ERROR', 'MISSING_CONSUMER_ID', 'consumerId is required. Use UNKNOWN_CONSUMER fallback from IFL_SO_INBOUND when no consumer is provided.', false)
    }

    if (!(payload instanceof Map)) {
        fail(message, 'VALIDATION_ERROR', 'INVALID_SAP_ORDER_PAYLOAD', 'SAP Sales Order payload must be a JSON object.', false)
    }

    def items = extractResults(payload.to_Item)
    if (items == null || items.isEmpty()) {
        fail(message, 'VALIDATION_ERROR', 'MISSING_ITEMS', 'SAP Sales Order payload must contain at least one item in to_Item.results.', false)
    }

    message.setProperty('correlationId', correlationId)
    message.setProperty('consumerId', consumerId ?: 'UNKNOWN_CONSUMER')
    message.setProperty('idempotencyKey', idempotencyKey ?: '')
    message.setProperty('idempotencyWarning', idempotencyKey ? 'false' : 'true')
    message.setProperty('validationStatus', 'SUCCESS')
    message.setProperty('processingStatus', 'VALIDATED')
    message.setProperty('itemCount', String.valueOf(items.size()))
    message.setProperty('payloadPreservationMode', 'ORIGINAL_SAP_JSON')

    return message
}

def value(def input) {
    return input == null ? '' : input.toString().trim()
}

def extractResults(def node) {
    if (node instanceof Map && node.results instanceof Collection) {
        return node.results
    }
    if (node instanceof Collection) {
        return node
    }
    return null
}

def fail(Message message, String category, String code, String text, boolean retryable) {
    message.setProperty('errorCategory', category)
    message.setProperty('errorCode', code)
    message.setProperty('errorMessage', text)
    message.setProperty('retryable', String.valueOf(retryable))
    message.setProperty('processingStatus', 'FAILED')
    throw new RuntimeException("${category}:${code}:${text}")
}

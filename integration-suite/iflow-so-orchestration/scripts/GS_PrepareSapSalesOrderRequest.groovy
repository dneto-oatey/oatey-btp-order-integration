import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def body = message.getBody(String)

    try {
        def payload = new JsonSlurper().parseText(body)
        if (!(payload instanceof Map)) {
            fail(message, 'TECHNICAL_ERROR', 'SAP_REQUEST_PREPARATION_FAILED', 'SAP Sales Order request payload is not a JSON object.', true)
        }

        message.setProperty('sapApiOperation', 'CREATE_SALES_ORDER')
        message.setProperty('sapRequestPrepared', 'true')
        message.setProperty('payloadPreservationMode', 'ORIGINAL_SAP_JSON')
        message.setProperty('purchaseOrderByCustomer', value(payload.PurchaseOrderByCustomer))
        message.setProperty('soldToParty', value(payload.SoldToParty))
        message.setProperty('salesOrderType', value(payload.SalesOrderType))
        message.setProperty('salesOrganization', value(payload.SalesOrganization))
    } catch (Exception e) {
        if (value(message.getProperty('errorCategory'))) {
            throw e
        }
        fail(message, 'TECHNICAL_ERROR', 'SAP_REQUEST_PREPARATION_FAILED', 'Unable to prepare SAP Sales Order API request context.', true)
    }

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

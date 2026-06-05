import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def statusCode = resolveStatusCode(message)
    def body = message.getBody(String)
    message.setProperty('sapResponseStatusCode', String.valueOf(statusCode))

    if (statusCode == 200 || statusCode == 201) {
        def salesOrderNumber = extractSalesOrderNumber(body)
        if (salesOrderNumber) {
            message.setProperty('processingStatus', 'SUCCESS')
            message.setProperty('sapSalesOrderNumber', salesOrderNumber)
            message.setProperty('retryable', 'false')
            return message
        }
        fail(message, 'TECHNICAL_ERROR', 'SAP_SUCCESS_WITHOUT_ORDER_NUMBER', 'SAP API returned success but no Sales Order number could be extracted.', true)
    }

    def sapError = extractSapError(body)

    if ([400, 409, 422].contains(statusCode)) {
        fail(message, 'SAP_BUSINESS_ERROR', sapError.code ?: "SAP_HTTP_${statusCode}", sapError.message ?: 'SAP rejected the Sales Order request with a business error.', false, statusCode)
    }

    if ([401, 403].contains(statusCode)) {
        fail(message, 'SAP_AUTH_CONFIG_ERROR', sapError.code ?: "SAP_HTTP_${statusCode}", sapError.message ?: 'SAP authentication or authorization failed.', false, statusCode)
    }

    if ([408, 429, 500, 502, 503, 504].contains(statusCode)) {
        fail(message, 'SAP_TRANSIENT_ERROR', sapError.code ?: "SAP_HTTP_${statusCode}", sapError.message ?: 'SAP API returned a retryable transient error.', true, statusCode)
    }

    fail(message, 'TECHNICAL_ERROR', sapError.code ?: "SAP_HTTP_${statusCode}", sapError.message ?: 'SAP API returned an unclassified response.', true, statusCode)
}

def resolveStatusCode(Message message) {
    def headers = message.getHeaders()
    def props = message.getProperties()
    def raw = headers.get('CamelHttpResponseCode') ?: headers.get('CamelHttpResponseStatusCode') ?: props.get('CamelHttpResponseCode') ?: props.get('sapResponseStatusCode')
    try {
        return raw == null ? 0 : raw.toString().trim().toInteger()
    } catch (Exception ignored) {
        return 0
    }
}

def extractSalesOrderNumber(String body) {
    if (!body?.trim()) {
        return ''
    }
    try {
        def json = new JsonSlurper().parseText(body)
        def root = json?.d instanceof Map ? json.d : json
        return value(root?.SalesOrder) ?: value(root?.SalesOrderNumber) ?: value(root?.SalesDocument) ?: value(root?.SalesOrderID)
    } catch (Exception ignored) {
        def matcher = body =~ /"SalesOrder"\s*:\s*"([^"]+)"/
        return matcher.find() ? matcher.group(1) : ''
    }
}

def extractSapError(String body) {
    def result = [code: '', message: '']
    if (!body?.trim()) {
        return result
    }
    try {
        def json = new JsonSlurper().parseText(body)
        def err = json?.error ?: json?.d?.error
        result.code = value(err?.code)
        def msg = err?.message
        result.message = value(msg instanceof Map ? msg.value : msg)
    } catch (Exception ignored) {
        result.message = body.take(500)
    }
    return result
}

def value(def input) {
    return input == null ? '' : input.toString().trim()
}

def fail(Message message, String category, String code, String text, boolean retryable, int statusCode = 0) {
    message.setProperty('errorCategory', category)
    message.setProperty('errorCode', code)
    message.setProperty('errorMessage', text)
    message.setProperty('sapErrorCode', code)
    message.setProperty('sapErrorMessage', text)
    message.setProperty('retryable', String.valueOf(retryable))
    message.setProperty('processingStatus', retryable ? 'RETRYING' : 'FAILED')
    if (statusCode > 0) {
        message.setProperty('sapResponseStatusCode', String.valueOf(statusCode))
    }
    throw new RuntimeException("${category}:${code}:${text}")
}

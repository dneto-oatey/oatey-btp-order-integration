import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    def headers = message.getHeaders()

    def csrfToken = firstHeaderValue(headers, 'x-csrf-token') ?: firstHeaderValue(headers, 'X-CSRF-Token')
    def setCookieValues = headerValues(headers, 'set-cookie') + headerValues(headers, 'Set-Cookie')
    def sapCookie = buildCookieHeader(setCookieValues)

    if (!csrfToken) {
        fail(message, 'TECHNICAL_ERROR', 'MISSING_CSRF_TOKEN', 'SAP CSRF fetch response did not include x-csrf-token.', true)
    }

    if (!sapCookie) {
        fail(message, 'TECHNICAL_ERROR', 'MISSING_SAP_COOKIE', 'SAP CSRF fetch response did not include session cookie.', true)
    }

    message.setProperty('csrfToken', csrfToken)
    message.setProperty('sapCookie', sapCookie)

    headers.put('x-csrf-token', csrfToken)
    headers.put('X-CSRF-Token', csrfToken)
    headers.put('Cookie', sapCookie)
    message.setHeaders(headers)

    return message
}

def firstHeaderValue(Map headers, String name) {
    return headerValues(headers, name).find { it } ?: ''
}

def headerValues(Map headers, String name) {
    def values = []
    headers.each { key, raw ->
        if (key?.toString()?.equalsIgnoreCase(name)) {
            if (raw instanceof Collection) {
                raw.each { item -> values.add(value(item)) }
            } else {
                values.add(value(raw))
            }
        }
    }
    return values.findAll { it }
}

def buildCookieHeader(Collection setCookieValues) {
    if (!setCookieValues || setCookieValues.isEmpty()) {
        return ''
    }

    def cookies = []
    setCookieValues.each { headerValue ->
        def normalized = value(headerValue).replaceAll(/\r?\n/, ',')
        def parts = normalized.split(/,(?=\s*[^;,\s]+=)/)
        parts.each { part ->
            def cookiePair = value(part).split(';')[0]
            if (cookiePair) {
                cookies.add(cookiePair)
            }
        }
    }
    return cookies.unique().join('; ')
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

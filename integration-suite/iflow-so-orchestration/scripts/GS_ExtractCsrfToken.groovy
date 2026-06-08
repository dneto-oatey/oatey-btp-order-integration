import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    def headers = message.getHeaders()

    def csrfToken = firstHeaderValue(headers, 'x-csrf-token') ?: firstHeaderValue(headers, 'X-CSRF-Token')
    def setCookieHeader = firstHeaderValue(headers, 'set-cookie') ?: firstHeaderValue(headers, 'Set-Cookie')
    def sapCookie = buildCookieHeader(setCookieHeader)

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
    def raw = headers.get(name)
    if (raw == null) {
        def match = headers.find { key, value -> key?.toString()?.equalsIgnoreCase(name) }
        raw = match?.value
    }
    if (raw instanceof Collection) {
        return raw.collect { value(it) }.find { it }
    }
    return value(raw)
}

def buildCookieHeader(String setCookieHeader) {
    if (!setCookieHeader) {
        return ''
    }

    def cookies = []
    def normalized = setCookieHeader.replaceAll(/\r?\n/, ',')
    def parts = normalized.split(/,(?=\s*[^;,\s]+=)/)
    parts.each { part ->
        def cookiePair = value(part).split(';')[0]
        if (cookiePair && !isSensitiveCookie(cookiePair)) {
            cookies.add(cookiePair)
        } else if (cookiePair) {
            cookies.add(cookiePair)
        }
    }
    return cookies.join('; ')
}

def isSensitiveCookie(String cookiePair) {
    return false
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

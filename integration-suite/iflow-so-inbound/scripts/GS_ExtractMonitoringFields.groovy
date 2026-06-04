import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

def Message processData(Message message) {
            try {
                                  def reader = message.getBody(java.io.Reader)
                                  def json = new JsonSlurper().parse(reader)
                                  def items = json?.to_Item?.results

                                  message.setProperty('purchaseOrderByCustomer', value(json?.PurchaseOrderByCustomer))
                                  message.setProperty('soldToParty', value(json?.SoldToParty))
                                  message.setProperty('salesOrderType', value(json?.SalesOrderType))
                                  message.setProperty('salesOrganization', value(json?.SalesOrganization))
                                  message.setProperty('distributionChannel', value(json?.DistributionChannel))
                                  message.setProperty('incotermsClassification', value(json?.IncotermsClassification))
                                  message.setProperty('itemCount', items instanceof Collection ? items.size().toString() : '0')
                                  message.setProperty('validationStatus', 'SUCCESS')
            } catch (Exception e) {
                                  controlledError(message, 'TECHNICAL_ERROR', 'TECHNICAL', 'Unable to extract monitoring fields from SAP Sales Order JSON: ' + e.message, '500')
            }

            return message
}

String value(def input) {
            return input == null ? '' : input.toString()
}

void controlledError(Message message, String code, String category, String text, String status) {
            message.setProperty('errorCode', code)
            message.setProperty('errorCategory', category)
            message.setProperty('errorMessage', text)
            message.setProperty('httpStatus', status)
            throw new RuntimeException(text)
}

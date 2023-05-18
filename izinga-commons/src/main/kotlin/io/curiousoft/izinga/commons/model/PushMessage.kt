package io.curiousoft.izinga.commons.model

class PushMessage(var pushMessageType: PushMessageType, var pushHeading: PushHeading, var pushContent: Any) {

    override fun equals(obj: Any?): Boolean {
        return obj is PushMessage && pushMessageType == obj.pushMessageType && pushHeading == obj.pushHeading && pushContent == obj.pushContent
    }
}
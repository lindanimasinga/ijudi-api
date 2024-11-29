package io.curiousoft.izinga.commons.model

class PushHeading {
    var link: String? = null
    var body: String? = null
    var title: String? = null
    var icon: String? = null

    constructor()
    constructor(body: String?, title: String?, icon: String?, link: String?) {
        this.body = body
        this.title = title
        this.icon = icon
        this.link = link
    }

    override fun equals(o: Any?): Boolean {
        return o is PushHeading && body == o.body && title == o.title && icon == o.icon
    }
}
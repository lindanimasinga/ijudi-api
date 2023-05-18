package io.curiousoft.izinga.commons.model

import javax.validation.constraints.NotEmpty

class SelectionOption {
    var name: @NotEmpty(message = "selectionOption name not valid") String? = null
    var values: @NotEmpty(message = "selectionOption values not valid") MutableList<String>? = null
    var selected: String? = null
    var price = 0.0
}
package io.curiousoft.izinga.commons.model

import javax.validation.constraints.NotBlank

class Bank {
    var branchCode: @NotBlank(message = "Bank Branch code not valid") String? = null
    var name: @NotBlank(message = "Bank name not valid") String? = null
    var phone: @NotBlank(message = "Bank phone not valid") String? = null
    var accountId: @NotBlank(message = "Bank account id not valid") String? = null
    var type: BankAccType? = null
}

enum class BankAccType(val code: String) {
    CHEQUE("1"), EWALLET("D"), SAVINGS("2"), TRANSMISSION("3"), wallet(""), string("")
}
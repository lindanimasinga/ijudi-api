package io.curiousoft.izinga.yocopay.api

import java.util.*

data class TransactionsResponse(val status: Int, val data: Data)
data class Data(val transactions: Set<Transaction>)
data class Transaction(val total: Double, val totalTipAmount: Double, val subItems: Set<SubItem>)
data class SubItem(val latitude: String, val longitude: String, val user: User, val amounts: Amounts, val created: Date, val details: Details)
data class User(val email: String, val firstname: String, val lastname: String)
data class Amounts(val totalAmount: Double, val tipAmount: Double)
data class Details(val receiptNumber: String)
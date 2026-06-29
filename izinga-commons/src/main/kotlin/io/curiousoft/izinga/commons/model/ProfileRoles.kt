package io.curiousoft.izinga.commons.model

enum class ProfileRoles(private val s: String) {
    CUSTOMER("Customer"), STORE_ADMIN("Store Admin"), STORE("Store"), MESSENGER("Messenger"), MESSENGER_ADMIN("Messenger Admin"), ADMIN("Admin");

    override fun toString(): String {
        return s;
    }}
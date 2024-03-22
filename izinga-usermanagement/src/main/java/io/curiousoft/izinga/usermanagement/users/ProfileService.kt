package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Profile

interface ProfileService<U : Profile> {
    @Throws(Exception::class)
    fun create(profile: U): U

    @Throws(Exception::class)
    fun update(profileId: String, profile: U): U
    fun delete(id: String)
    fun find(profileId: String): U
    fun findAll(): List<U>
}
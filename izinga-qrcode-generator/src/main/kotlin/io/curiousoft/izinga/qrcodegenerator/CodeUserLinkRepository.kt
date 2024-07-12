package io.curiousoft.izinga.qrcodegenerator

import org.springframework.data.mongodb.repository.MongoRepository

interface LinkCodeUserRepository : MongoRepository<LinkCodeUser, String> {
    fun findByUserId(userId: String): List<LinkCodeUser>
    fun findByLinkCode(codeLink: String): LinkCodeUser?
}
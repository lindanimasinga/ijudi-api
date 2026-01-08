package io.curiousoft.izinga.usermanagement.userconfig

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class UserConfigService(private val userConfigRepo: UserConfigRepo) {

    fun create(config: UserConfig): UserConfig {
        return userConfigRepo.save(config)
    }

    fun update(userType: String, config: UserConfig): UserConfig {
        val persisted = userConfigRepo.findById(userType).orElseThrow { Exception("UserConfig not found") }
        // copy fields (preserve the id/userType)
        persisted.mandatoryFields = config.mandatoryFields
        persisted.optionalFields = config.optionalFields
        return userConfigRepo.save(persisted)
    }

    fun find(userType: String): UserConfig? = userConfigRepo.findByIdOrNull(userType)

    fun findAll(): List<UserConfig> = userConfigRepo.findAll()

    fun delete(userType: String) = userConfigRepo.deleteById(userType)
}

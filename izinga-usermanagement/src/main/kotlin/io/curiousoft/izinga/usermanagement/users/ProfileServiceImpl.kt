package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Profile
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.repo.ProfileRepo
import org.springframework.beans.BeanUtils
import java.util.*
import javax.validation.Validation
import javax.validation.Validator

abstract class ProfileServiceImpl<E : ProfileRepo<U>, U : Profile>(protected val profileRepo: E) : ProfileService<U> {

    private val validator: Validator

    init {
        val factory = Validation.buildDefaultValidatorFactory()
        validator = factory.validator
    }

    @Throws(Exception::class)
    override fun create(profile: U): U {
        validate(profile)
        profile.id = UUID.randomUUID().toString()
        return profileRepo.save<U>(profile)
    }

    @Throws(Exception::class)
    override fun update(profileId: String, profile: U): U {
        val persistedProfile = profileRepo.findById(profileId)
            .orElseThrow { Exception("Profile not found") }
        BeanUtils.copyProperties(profile, persistedProfile)
        return profileRepo.save(persistedProfile)
    }

    override fun delete(id: String) {
        profileRepo.deleteById(id)
    }

    override fun find(profileId: String): U? {
        return profileRepo.findById(profileId).orElse(null)
    }

    override fun findAll(): List<U> {
        return profileRepo.findAll()
    }

    @Throws(Exception::class)
    protected fun validate(profile: Any) {
        val violations = validator.validate(profile)
        if (violations.size > 0) {
            throw Exception(violations.iterator().next().message)
        }
    }

    fun findByRole(role: ProfileRoles?): List<U> {
        return profileRepo.findByRole(role!!)!!
    }
}
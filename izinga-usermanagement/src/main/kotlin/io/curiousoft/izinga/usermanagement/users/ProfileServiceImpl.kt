package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Profile
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.profile.events.ProfileCreatedEvent
import io.curiousoft.izinga.commons.profile.events.ProfileDeletedEvent
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent
import io.curiousoft.izinga.commons.repo.ProfileRepo
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.springframework.beans.BeanUtils
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

abstract class ProfileServiceImpl<E : ProfileRepo<U>, U : Profile>(protected val profileRepo: E,
                                                                   private val eventPublisher: ApplicationEventPublisher) : ProfileService<U> {

    private val validator: Validator

    init {
        val factory = Validation.buildDefaultValidatorFactory()
        validator = factory.validator
    }

    @Throws(Exception::class)
    override fun create(profile: U): U {
        validate(profile)
        profile.id = UUID.randomUUID().toString()
        val saved = profileRepo.save<U>(profile)
        eventPublisher.publishEvent(ProfileCreatedEvent(this, saved))
        return saved
    }

    @Throws(Exception::class)
    override fun update(profileId: String, profile: U): U {
        val persistedProfile = profileRepo.findById(profileId)
            .orElseThrow { Exception("Profile not found") }
        BeanUtils.copyProperties(profile, persistedProfile)
        val saved = profileRepo.save(persistedProfile)
        eventPublisher.publishEvent(ProfileUpdatedEvent(this, saved))
        return saved
    }

    override fun delete(id: String) {
        val p = profileRepo.findById(id).orElse(null)
        profileRepo.deleteById(id)
        p?.let { eventPublisher.publishEvent(ProfileDeletedEvent(this, it)) }
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

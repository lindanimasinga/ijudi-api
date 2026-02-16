package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Profile
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.utils.isSAMobileNumber
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

@RestController
@RequestMapping("/user")
class UserController(private val profileService: UserProfileService) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @RequestMapping(method = [RequestMethod.POST], consumes = ["application/json"], produces = ["application/json"])
    @Throws(Exception::class)
    fun create(@RequestBody profile: @Valid UserProfile): ResponseEntity<Profile> {
        logger.info("Create user request for mobileNumber={}", profile.mobileNumber)
        val created = profileService.create(profile)
        logger.info("User created with id={}", created.id)
        return ResponseEntity.ok(created)
    }

    @PatchMapping(value = ["/{id}"], consumes = ["application/json"], produces = ["application/json"])
    @Throws(Exception::class)
    fun update(@PathVariable id: String?, @RequestBody profile: @Valid UserProfile): ResponseEntity<Profile> {
        logger.info("Update user request for id={}", id)
        val updated = profileService.update(id!!, profile)
        logger.info("User updated id={}", updated.id)
        return ResponseEntity.ok(updated)
    }

    @GetMapping(value = ["/{id}"], produces = ["application/json"])
    fun findUser(@PathVariable id: String?): ResponseEntity<Profile> {
        logger.info("Find user request for idOrPhone={}", id)
        val user: Profile? = if (isSAMobileNumber(id!!)) profileService.findUserByPhone(id) else profileService.find(id)
        return if (user != null) {
            logger.info("Found user id={}", user.id)
            ResponseEntity.ok(user)
        } else {
            logger.info("User not found for idOrPhone={}", id)
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping(produces = ["application/json"])
    fun findUsers(
        @RequestParam(required = false) role: ProfileRoles?,
        @RequestParam(required = false) latitude: Double,
        @RequestParam(required = false) longitude: Double,
        @RequestParam(required = false) range: Double
    ): ResponseEntity<List<UserProfile?>?> {
        logger.info("Find users request role={} latitude={} longitude={} range={}", role, latitude, longitude, range)
        val users = if (role != null) profileService.findByLocation(
            role,
            latitude,
            longitude,
            range) else profileService.findAll()
        logger.info("Returning {} users", users?.size)
        return ResponseEntity.ok(users)
    }

    @GetMapping(value = ["/pending-approvals"], produces = ["application/json"])
    fun pendingApprovals(): ResponseEntity<List<UserProfile>> {
        logger.info("Pending approvals request received")
        val pending = profileService.pendingAproval()
        logger.info("Found {} pending approvals", pending.size)
        return ResponseEntity.ok(pending)
    }

    @DeleteMapping(value = ["/{id}"], produces = ["application/json"])
    fun deleteUser(@PathVariable id: String?): ResponseEntity<*> {
        logger.info("Delete user request for id={}", id)
        profileService.delete(id!!)
        logger.info("Deleted user id={}", id)
        return ResponseEntity.ok().build<Any>()
    }
}
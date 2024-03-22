package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Profile
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.utils.isSAMobileNumber
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

@RestController
@RequestMapping("/user")
class UserController(private val profileService: UserProfileService) {

    @RequestMapping(method = [RequestMethod.POST], consumes = ["application/json"], produces = ["application/json"])
    @Throws(Exception::class)
    fun create(@RequestBody profile: @Valid UserProfile): ResponseEntity<Profile> {
        return ResponseEntity.ok(profileService.create(profile))
    }

    @PatchMapping(value = ["/{id}"], consumes = ["application/json"], produces = ["application/json"])
    @Throws(Exception::class)
    fun update(@PathVariable id: String?, @RequestBody profile: @Valid UserProfile): ResponseEntity<Profile> {
        return ResponseEntity.ok(profileService.update(id!!, profile))
    }

    @GetMapping(value = ["/{id}"], produces = ["application/json"])
    fun findUser(@PathVariable id: String?): ResponseEntity<Profile> {
        val user: Profile? = if (isSAMobileNumber(id!!)) profileService.findUserByPhone(id) else profileService.find(id)
        return if (user != null) ResponseEntity.ok(user) else ResponseEntity.notFound().build()
    }

    @GetMapping(produces = ["application/json"])
    fun findUsers(
        @RequestParam(required = false) role: ProfileRoles?,
        @RequestParam(required = false) latitude: Double,
        @RequestParam(required = false) longitude: Double,
        @RequestParam(required = false) range: Double
    ): ResponseEntity<List<UserProfile?>?> {
        val users = if (role != null) profileService.findByLocation(
            role,
            latitude,
            longitude,
            range) else profileService.findAll()
        return ResponseEntity.ok(users)
    }

    @DeleteMapping(value = ["/{id}"], produces = ["application/json"])
    fun deleteUser(@PathVariable id: String?): ResponseEntity<*> {
        profileService.delete(id!!)
        return ResponseEntity.ok().build<Any>()
    }
}
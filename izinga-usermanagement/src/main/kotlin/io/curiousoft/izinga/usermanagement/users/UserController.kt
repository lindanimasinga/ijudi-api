package io.curiousoft.izinga.usermanagement.users

import io.curiousoft.izinga.commons.model.Profile
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.StoreType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.qrcodegenerator.tips.QRCodeService
import io.curiousoft.izinga.recon.payout.AmbassadorPayout
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import io.curiousoft.izinga.usermanagement.utils.IjudiUtils.isSAMobileNumber
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

@RestController
@RequestMapping("/user")
class UserController(
    private val profileService: UserProfileService,
    private val userProfileRepo: UserProfileRepo,
    private val qrCodeService: QRCodeService,
    private val ambassadorPayoutRepo: AmbassadorPayoutRepository
) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    @RequestMapping(method = [RequestMethod.POST], consumes = ["application/json"], produces = ["application/json"])
    @Throws(Exception::class)
    fun create(
        @RequestBody profile: @Valid UserProfile,
        @RequestParam(required = false) ref: String?
    ): ResponseEntity<Profile> {
        logger.info("Create user request for mobileNumber={} ref={}", profile.mobileNumber, ref)
        val created = profileService.create(profile, ref)
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
        @RequestParam(required = false, defaultValue = "false") includePendingUsers: Boolean,
        @RequestParam(required = false) role: ProfileRoles?,
        @RequestParam(required = false) messengerAdminId: String?,
        @RequestParam(required = false) latitude: Double?,
        @RequestParam(required = false) longitude: Double?,
        @RequestParam(required = false) range: Double?,
        @RequestParam(required = false, defaultValue = "FOOD") storeType: StoreType = StoreType.FOOD) : ResponseEntity<List<UserProfile?>?> {
        logger.info("Find users request role={} messengerAdminId={} latitude={} longitude={} range={}", role, messengerAdminId, latitude, longitude, range)
        val users = when {
            role == ProfileRoles.MESSENGER && !messengerAdminId.isNullOrBlank() -> profileService.findMessengersByAdminId(messengerAdminId)
            role == ProfileRoles.MESSENGER && latitude != null && longitude != null && range != null -> profileService.findMessengersByLocation(latitude, longitude, range)
            role != null -> profileService.findByLocation(
                role,
                latitude!!,
                longitude!!,
                range!!,
                storeType
            )
            else -> profileService.findAll()
        }
        logger.info("Returning {} users", users?.size)
        return ResponseEntity.ok(users?.filter { user -> includePendingUsers || (user.profileApproved && user.termsAccepted == true) })
    }

    @GetMapping(value = ["/pending-approvals"], produces = ["application/json"])
    fun pendingApprovals(): ResponseEntity<List<UserProfile>> {
        logger.info("Pending approvals request received")
        val pending = profileService.pendingAproval()
        logger.info("Found {} pending approvals", pending.size)
        return ResponseEntity.ok(pending)
    }

    @GetMapping(value = ["/{userId}/ambassador-qr"], produces = [MediaType.IMAGE_PNG_VALUE])
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")
    fun getAmbassadorQrCode(@PathVariable userId: String): ResponseEntity<ByteArray> {
        logger.info("Ambassador QR code request for userId={}", userId)

        val profile = userProfileRepo.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build<ByteArray>().also {
                logger.warn("Ambassador QR request for unknown userId={}", userId)
            }

        if (profile.role != ProfileRoles.AMBASSADOR || profile.profileApproved != true) {
            logger.warn("Ambassador QR denied: userId={} role={} approved={}", userId, profile.role, profile.profileApproved)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return try {
            val qrContent = "https://onboarding.izinga.co.za/indivisuals?ref=$userId"
            val label = profile.name ?: userId
            val qrImage = qrCodeService.generateQRCodeImage("REFER A FRIEND", qrContent, label, 450, 450)
            logger.info("Ambassador QR generated for userId={}", userId)
            ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(qrImage)
        } catch (e: Exception) {
            logger.error("Failed to generate ambassador QR for userId={}", userId, e)
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping(value = ["/{userId}/ambassador-drivers"], produces = ["application/json"])
    fun getAmbassadorDrivers(@PathVariable userId: String): ResponseEntity<List<UserProfile>> {
        logger.info("Ambassador drivers request for userId={}", userId)
        val profile = userProfileRepo.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (profile.role != ProfileRoles.AMBASSADOR || profile.profileApproved != true) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val drivers = userProfileRepo.findByAmbassadorId(userId)
        logger.info("Found {} drivers for ambassadorId={}", drivers.size, userId)
        return ResponseEntity.ok(drivers)
    }

    @GetMapping(value = ["/{userId}/ambassador-payouts"], produces = ["application/json"])
    fun getAmbassadorPayouts(@PathVariable userId: String): ResponseEntity<List<AmbassadorPayout>> {
        logger.info("Ambassador payouts request for userId={}", userId)
        val profile = userProfileRepo.findById(userId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (profile.role != ProfileRoles.AMBASSADOR || profile.profileApproved != true) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val payouts = ambassadorPayoutRepo.findByToId(userId)
        logger.info("Found {} payouts for ambassadorId={}", payouts.size, userId)
        return ResponseEntity.ok(payouts)
    }

    @DeleteMapping(value = ["/{id}"], produces = ["application/json"])
    fun deleteUser(@PathVariable id: String?): ResponseEntity<*> {
        logger.info("Delete user request for id={}", id)
        profileService.delete(id!!)
        logger.info("Deleted user id={}", id)
        return ResponseEntity.ok().build<Any>()
    }
}

@RestController
@RequestMapping("/ambassador")
class AmbassadorAdminController(
    private val profileService: UserProfileService,
    private val userProfileRepo: UserProfileRepo
) {
    private val logger = LoggerFactory.getLogger(AmbassadorAdminController::class.java)

    data class CreateAmbassadorRequest(
        val name: String,
        val mobileNumber: String,
        val emailAddress: String? = null
    )

    data class CreateAmbassadorResponse(
        val userId: String,
        val referralUrl: String
    )

    @PostMapping(consumes = ["application/json"], produces = ["application/json"])
    @PreAuthorize("hasRole('ADMIN')")
    fun createAmbassador(@RequestBody request: CreateAmbassadorRequest): ResponseEntity<*> {
        logger.info("Admin creating ambassador for mobileNumber={}", request.mobileNumber)

        if (userProfileRepo.findByMobileNumber(request.mobileNumber) != null) {
            logger.warn("Ambassador creation failed — mobileNumber already exists: {}", request.mobileNumber)
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to "A user with this phone number already exists"))
        }

        val profile = UserProfile(
            request.name,
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "",
            "",
            request.mobileNumber,
            ProfileRoles.AMBASSADOR
        ).apply {
            profileApproved = true
            emailAddress = request.emailAddress
        }

        val created = profileService.create(profile)
        val referralUrl = "https://onboarding.izinga.co.za/indivisuals?ref=${created.id}"
        logger.info("Ambassador created id={} referralUrl={}", created.id, referralUrl)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CreateAmbassadorResponse(userId = created.id!!, referralUrl = referralUrl))
    }
}
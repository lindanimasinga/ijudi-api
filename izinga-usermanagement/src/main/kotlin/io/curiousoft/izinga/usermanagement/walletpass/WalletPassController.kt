package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.commons.model.DeviceType
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.usermanagement.users.UserProfileService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/walletpass")
class WalletPassController(private val passGenerator: WalletPassService, private val userProfileService: UserProfileService) {

    private val passKitContentType = "application/vnd.apple.pkpass"

    @GetMapping("/{userId}/{deviceType}")
    @Throws(Exception::class)
    fun create(@PathVariable userId: String, @PathVariable deviceType: DeviceType): ResponseEntity<Any> {
        return userProfileService.find(userId)?.let { user ->
            passGenerator.generatePass(user, deviceType)?.let {
                when (deviceType) {
                    DeviceType.APPLE -> resolveAppleResponse(it as ByteArray, user)
                    DeviceType.ANDROID -> resolveAndroidResponse(it as String)
                }
            }
        } ?: ResponseEntity.notFound().build()
    }

    private fun resolveAndroidResponse(passUrl: String): ResponseEntity<Any> {
        return ResponseEntity.status(302).location(URI.create(passUrl)).build()
    }

    private fun resolveAppleResponse(passData: ByteArray, user: UserProfile): ResponseEntity<Any> {
        return passData
            .let { ByteArrayResource(it) }
            .let {
            ResponseEntity.status(201)
                .header("Content-Type", passKitContentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=%s(%s).pkpass", user.name, user.mobileNumber))
                .body(it)
        }
    }
}

data class PassCreateRequest(val userId: String, val deviceType: DeviceType)

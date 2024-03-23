package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.usermanagement.users.UserProfileService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.validation.Valid

@RestController
@RequestMapping("/walletpass")
class WalletPassController(private val passGenerator: WalletPassService, private val userProfileService: UserProfileService) {

    private val passKitContentType = "application/vnd.apple.pkpass"

    @GetMapping("/{userId}/{deviceType}")
    @Throws(Exception::class)
    fun create(@PathVariable userId: String, @PathVariable deviceType: DeviceType): ResponseEntity<ByteArrayResource> {
        return userProfileService.find(userId)?.let { user ->
            val passWalletBytes = passGenerator.generatePass(user, deviceType)?.let { ByteArrayResource(it) }
            ResponseEntity.status(201)
                .header("Content-Type", passKitContentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=%s(%s).pkpass", user.name, user.mobileNumber))
                .body(passWalletBytes)
        } ?: ResponseEntity.notFound().build()
    }
}

data class PassCreateRequest(val userId: String, val deviceType: DeviceType)

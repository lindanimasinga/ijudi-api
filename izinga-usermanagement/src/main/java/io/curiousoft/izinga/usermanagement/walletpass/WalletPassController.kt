package io.curiousoft.izinga.usermanagement.walletpass

import io.curiousoft.izinga.usermanagement.users.UserProfileService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
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

    @PostMapping(consumes = ["application/json"], produces = ["application/json"])
    @Throws(Exception::class)
    fun create(@RequestBody passCreateRequest: @Valid PassCreateRequest): ResponseEntity<ByteArrayResource> {
        return userProfileService.find(passCreateRequest.userId)?.let { user ->
            val passWalletBytes = passGenerator.generatePass(user, passCreateRequest.deviceType)?.let { ByteArrayResource(it) }
            ResponseEntity.status(201)
                .header("Content-Type", passKitContentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, String.format("attachment; filename=%s-%s.jpg", user.name, user.mobileNumber))
                .body(passWalletBytes)
        } ?: ResponseEntity.notFound().build()
    }
}

data class PassCreateRequest(val userId: String, val deviceType: DeviceType)

package io.curiousoft.izinga.usermanagement.userconfig

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/user-config")
class UserConfigController(private val userConfigService: UserConfigService) {

    @PostMapping(consumes = ["application/json"], produces = ["application/json"])
    fun create(@RequestBody config: UserConfig): ResponseEntity<UserConfig> {
        val created = userConfigService.create(config)
        return ResponseEntity.ok(created)
    }

    @GetMapping(produces = ["application/json"])
    fun findAll(): ResponseEntity<List<UserConfig>> {
        return ResponseEntity.ok(userConfigService.findAll())
    }

    @GetMapping(value = ["/{userType}"], produces = ["application/json"])
    fun find(@PathVariable userType: String): ResponseEntity<UserConfig> {
        val cfg = userConfigService.find(userType)
        return if (cfg != null) ResponseEntity.ok(cfg) else ResponseEntity.notFound().build()
    }

    @PatchMapping(value = ["/{userType}"], consumes = ["application/json"], produces = ["application/json"])
    fun patch(@PathVariable userType: String, @RequestBody config: UserConfig): ResponseEntity<UserConfig> {
        val updated = userConfigService.update(userType, config)
        return ResponseEntity.ok(updated)
    }
}

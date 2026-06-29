package io.curiousoft.izinga.recon.bank

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/bank/config")
class BankConfigController(private val bankConfigService: BankConfigService) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/all")
    fun getAllBanks(): ResponseEntity<List<BankConfig>> {
        log.info("Fetching all available banks")
        return ResponseEntity.ok(bankConfigService.getAllBanks())
    }

    @GetMapping("/{bankId}")
    fun getBankConfig(@PathVariable bankId: String): ResponseEntity<BankConfig> {
        log.info("Fetching bank config for bankId: $bankId")
        return bankConfigService.getBankById(bankId)?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }

    @GetMapping("/search")
    fun searchBank(@RequestParam bankName: String): ResponseEntity<BankConfig> {
        log.info("Searching bank by name: $bankName")
        return bankConfigService.getBankByName(bankName)?.let {
            ResponseEntity.ok(it)
        } ?: ResponseEntity.status(HttpStatus.NOT_FOUND).build()
    }
}


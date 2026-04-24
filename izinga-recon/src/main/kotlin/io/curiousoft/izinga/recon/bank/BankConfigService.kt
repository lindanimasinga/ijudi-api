package io.curiousoft.izinga.recon.bank

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class BankConfigService(private val bankConfigRepository: BankConfigRepository) {

    private val banks = mutableMapOf<String, BankConfig>()
    private val log = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun initializeBanks() {
        // South African Banks - Official branch codes
        // Source: Verified bank branch codes list

        log.info("Initializing South African bank configurations...")

        val banksList = listOf(
            BankConfig(
                bankId = "632005",
                bankName = "ABSA",
                branchCode = "632005",
                switchCode = "ABSAZAJJ"
            ),
            BankConfig(
                bankId = "250655",
                bankName = "FNB / First National Bank",
                branchCode = "250655",
                switchCode = "FIRNZAJJ"
            ),
            BankConfig(
                bankId = "051001",
                bankName = "Std Bank / Standard Bank of SA",
                branchCode = "051001",
                switchCode = "SBZAZAJJ"
            ),
            BankConfig(
                bankId = "198765",
                bankName = "Nedbank",
                branchCode = "198765",
                switchCode = "NEDSZAJJ"
            ),
            BankConfig(
                bankId = "470010",
                bankName = "Capitec",
                branchCode = "470010",
                switchCode = "CAPIZBAJ"
            ),
            BankConfig(
                bankId = "720026",
                bankName = "Nedbank Corporate Saver",
                branchCode = "720026",
                switchCode = "NEDSZAJJ"
            ),
            BankConfig(
                bankId = "410506",
                bankName = "Bank of Athens",
                branchCode = "410506",
                switchCode = "ATHENAJJ"
            ),
            BankConfig(
                bankId = "430000",
                bankName = "African Bank",
                branchCode = "430000",
                switchCode = "AFBKZAJJ"
            ),
            BankConfig(
                bankId = "462005",
                bankName = "Bidvest",
                branchCode = "462005",
                switchCode = "BIDVZAJJ"
            ),
            BankConfig(
                bankId = "580105",
                bankName = "Investec",
                branchCode = "580105",
                switchCode = "INVERZAJ"
            ),
            BankConfig(
                bankId = "590000",
                bankName = "Barclays",
                branchCode = "590000",
                switchCode = "BARCZAJJ"
            ),
            BankConfig(
                bankId = "101609",
                bankName = "Cape of Good Hope (NED)",
                branchCode = "101609",
                switchCode = "CAPEZAJJ"
            ),
            BankConfig(
                bankId = "400005",
                bankName = "PEP Bank",
                branchCode = "400005",
                switchCode = "PEPBZAJJ"
            ),
            BankConfig(
                bankId = "760005",
                bankName = "Permanent Bank",
                branchCode = "760005",
                switchCode = "PERMZAJJ"
            ),
            BankConfig(
                bankId = "462005",
                bankName = "Rennies Bank",
                branchCode = "462005",
                switchCode = "RENNIAJJ"
            ),
            BankConfig(
                bankId = "730020",
                bankName = "Standard Chartered",
                branchCode = "730020",
                switchCode = "SCBLZAJJ"
            ),
            BankConfig(
                bankId = "410700",
                bankName = "Wizzit Bank",
                branchCode = "410700",
                switchCode = "WIZZAJJ"
            ),
            BankConfig(
                bankId = "888000",
                bankName = "Bank Zero",
                branchCode = "888000",
                switchCode = "ZEROZAJJ"
            ),
            BankConfig(
                bankId = "678910",
                bankName = "Thyme Bank",
                branchCode = "678910",
                switchCode = "THYMZAJJ"
            ),
            BankConfig(
                bankId = "679000",
                bankName = "Discovery Bank",
                branchCode = "679000",
                switchCode = "DISCBAJJ"
            ),
            BankConfig(
                bankId = "683000",
                bankName = "SASFIN Bank",
                branchCode = "683000",
                switchCode = "SASFZAJJ"
            ),
            BankConfig(
                bankId = "460005",
                bankName = "SA Post Bank (Post Office)",
                branchCode = "460005",
                switchCode = "POSTZA22"
            )
        )

        // Load from memory first
        banksList.forEach { bank ->
            banks[bank.bankId.lowercase()] = bank
        }

        // Persist to MongoDB if not already present
        try {
            banksList.forEach { bank ->
                val existingBank = bankConfigRepository.findByBankId(bank.bankId)
                if (existingBank == null) {
                    bankConfigRepository.save(bank)
                    log.info("Saved bank configuration to MongoDB: {} ({})", bank.bankName, bank.bankId)
                } else {
                    log.debug("Bank configuration already exists in MongoDB: {} ({})", bank.bankName, bank.bankId)
                }
            }
            log.info("Successfully initialized {} bank configurations", banksList.size)
        } catch (e: Exception) {
            log.error("Error persisting bank configurations to MongoDB", e)
            // Continue with in-memory banks if MongoDB fails
        }
    }

    fun getAllBanks(): List<BankConfig> = banks.values.toList()

    fun getBankById(bankId: String): BankConfig? = banks[bankId.lowercase()]

    fun getBankByName(bankName: String): BankConfig? =
        banks.values.find { it.bankName.equals(bankName, ignoreCase = true) }
}


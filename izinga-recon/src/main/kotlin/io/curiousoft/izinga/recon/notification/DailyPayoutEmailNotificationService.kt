package io.curiousoft.izinga.recon.notification;

import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ReconService
import io.curiousoft.izinga.recon.payout.Payout
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.repo.AmbassadorPayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class DailyPayoutEmailNotificationService(
        val userProfileRepo: UserProfileRepo,
        val storeRepository: StoreRepository,
        val reconService: ReconService,
        val ambassadorPayoutRepository: AmbassadorPayoutRepository,
        @Value("\${mailersend.apikey}") val apiKey: String,
        @Value("\${mailersend.template.daily-payout}") val dailyPayoutTemplate: String) {

    private val logger = LoggerFactory.getLogger(DailyPayoutEmailNotificationService::class.java)
    private val restTemplate = RestTemplate()

    @Scheduled(cron = "0 0 17 1/1 * ?") // every day at 5pm
    fun notifyDailyPayouts() {
        val shopPayouts = reconService.getCurrentPayoutBundleForShops()
        val messagerPayouts = reconService.getCurrentPayoutBundleForMessenger()

        shopPayouts.payouts
            .filter { !it.emailSent }
            .map { sendPayoutEmail(it) }

        messagerPayouts.payouts
            .filter { !it.emailSent }
            .map { sendPayoutEmail(it) }

        reconService.updateBundle(shopPayouts)
        reconService.updateBundle(messagerPayouts)

        processAmbassadorPayouts()
    }

    private fun processAmbassadorPayouts() {
        val pendingAmbassadorPayouts = ambassadorPayoutRepository.findByPayoutStage(PayoutStage.PENDING)
        pendingAmbassadorPayouts.forEach { payout ->
            if (!payout.emailSent) {
                sendPayoutEmail(payout)
                ambassadorPayoutRepository.save(payout)
            }
        }
    }

    private fun sendPayoutEmail(it: Payout) {
        it.emailAddress?.let { emailAddress ->
            val emailMessage = EmailRequest();
            emailMessage.template_id = dailyPayoutTemplate
            emailMessage.to = listOf(To(emailAddress))
            val data = Data(it)
            emailMessage.personalization = listOf(Personalization(emailAddress, data))

            val headers = HttpHeaders()
            headers["Content-Type"] = "application/json"
            headers["Authorization"] = "Bearer $apiKey"
            val entity: HttpEntity<EmailRequest> = HttpEntity(emailMessage, headers)
            restTemplate.postForEntity(
                "https://api.mailersend.com/v1/email",
                entity, String::class.java
            )
            it.emailSent = true
        }
    }
}

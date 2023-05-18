package io.curiousoft.izinga.recon.notification;

import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.ReconService
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
        @Value("\${mailersend.apikey}") val apiKey: String,
        @Value("\${mailersend.template.daily-payout}") val dailyPayoutTemplate: String) {

    //val Logger = LoggerFactory.getLogger(EmailNotificationService.javaClass)
    private val restTemplate = RestTemplate()

    @Scheduled(cron = "0 0 19 1/1 * ?") // every day at 7pm
    fun notifyDailyShopPayout() {
        val bundle = reconService.generateNextPayoutsToShop()
        bundle?.payouts?.map {
            val emailMessage = EmailRequest();
            emailMessage.template_id = dailyPayoutTemplate
            emailMessage.to = listOf(To(it.emailAddress))
            val data = Data(it)
            emailMessage.personalization = listOf(Personalization(it.emailAddress, data))

            val headers = HttpHeaders()
            headers["Content-Type"] = "application/json"
            headers["Authorization"] = "Bearer $apiKey"
            val entity: HttpEntity<EmailRequest> = HttpEntity(emailMessage, headers)
            restTemplate.postForEntity(
                "https://api.mailersend.com/v1/email",
                entity, String::class.java
            )
        }
    }
}

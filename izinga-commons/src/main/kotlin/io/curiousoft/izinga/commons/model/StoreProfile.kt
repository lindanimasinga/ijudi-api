package io.curiousoft.izinga.commons.model

import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.util.StringUtils
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.function.Consumer
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import kotlin.collections.HashSet

class StoreProfile(
    var storeType: @NotNull(message = "storeType is not valid") StoreType?,
    name: @NotBlank(message = "profile name not valid") String?,
    @field:Indexed(unique = true) private var shortName: @NotBlank(message = "profile short name not valid") String?,
    address: @NotBlank(message = "profile address not valid") String?,
    imageUrl: @NotBlank(message = "profile image url not valid") String?,
    mobileNumber: @NotBlank(message = "profile mobile number not valid") String?,
    var tags: @NotEmpty(message = "profile tags not valid") MutableList<String>?,
    var businessHours: @NotEmpty(message = "Business hours not valid") MutableList<BusinessHours>?,
    var ownerId: @NotBlank(message = "shop owner id not valid") String?,
    bank: @NotNull(message = "Shop bank not valid") Bank?
) : Profile(name, address, imageUrl, mobileNumber, ProfileRoles.STORE), GeoPoint {
    @Indexed(unique = true)
    var regNumber: String? = null
    var stockList: HashSet<Stock> = HashSet()
    var hasVat = false
    var hasPaymentAgreement = false
    var featured = false
    var featuredExpiry: Date? = null
    var storeMessenger: HashSet<Messager>? = HashSet()
    var storeWebsiteUrl: String? = null
    var izingaTakesCommission = false
    var scheduledDeliveryAllowed = false
    var availability = AVAILABILITY.SPECIFIC_HOURS
    var freeDeliveryMinAmount = 0.0
    var markUpPrice = true
    var minimumDepositAllowedPerc = 1.0
    var standardDeliveryPrice = 0.0
    var standardDeliveryKm = 0.0
    var ratePerKm = 0.0
    var franchiseName: String? = null
    var deliversFromFixedAddress: Boolean? = null;

    init {
        super.bank = bank
    }

    fun setMarkUp(markupPerc: Double) {
        if (markUpPrice) {
            stockList.forEach(Consumer { it: Stock -> it.setMarkupPercentage(markupPerc) })
        }
    }

    val orderUrl: String?
        get() = if (!StringUtils.isEmpty(storeWebsiteUrl)) "$storeWebsiteUrl/order/" else null

    fun getShortName(): String? {
        return shortName
    }

    fun setShortName(shortName: String?) {
        this.shortName = shortName?.lowercase(Locale.getDefault())
    }

    fun isEligibleForFreeDelivery(order: Order): Boolean {
        return freeDeliveryMinAmount > 0 && order.basketAmount >= freeDeliveryMinAmount
    }

    //should not accept orders 15 minutes before store closes
    val isStoreOffline: Boolean
        get() {
            if (availability == AVAILABILITY.OFFLINE) {
                return true
            }
            if (availability == AVAILABILITY.ONLINE24_7 || businessHours == null || businessHours!!.isEmpty()) {
                return false
            }
            if (scheduledDeliveryAllowed) {
                return false
            }
            val businessDay = businessHours!!.stream()
                .filter { day: BusinessHours -> LocalDateTime.now().dayOfWeek == day.day }
                .findFirst()
            if (!businessDay.isPresent) {
                return true
            }
            var calender = Calendar.Builder().setInstant(businessDay.get().open).build()
            val open = Date.from(
                LocalDateTime.now()
                    .withHour(calender[Calendar.HOUR_OF_DAY])
                    .withMinute(calender[Calendar.MINUTE])
                    .withSecond(calender[Calendar.SECOND])
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            )
            calender = Calendar.Builder().setInstant(businessDay.get().close).build()
            val close = Date.from(
                LocalDateTime.now()
                    .withHour(calender[Calendar.HOUR_OF_DAY])
                    .withMinute(calender[Calendar.MINUTE])
                    .withSecond(calender[Calendar.SECOND])
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            )
            val lowerBoundTime = Date.from(
                LocalDateTime.now().plusHours(2)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            )
            val upperBoundTime = Date.from(
                LocalDateTime.now().plusHours(2)
                    .plusMinutes(14) //should not accept orders 15 minutes before store closes
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            )
            return (lowerBoundTime.before(open)
                    || upperBoundTime.after(close))
        }

    //should not accept orders 15 minutes before store closes
    val isDeliverNowAllowed: Boolean
        get() {
            if (businessHours == null || businessHours!!.isEmpty()) {
                return false
            }
            val businessDay = businessHours!!.stream()
                .filter { day: BusinessHours -> LocalDateTime.now().dayOfWeek == day.day }
                .findFirst()
            if (!businessDay.isPresent) {
                return false
            }
            var calender = Calendar.Builder().setInstant(businessDay.get().open).build()
            val open = Date.from(
                LocalDateTime.now()
                    .withHour(calender[Calendar.HOUR_OF_DAY])
                    .withMinute(calender[Calendar.MINUTE])
                    .withSecond(calender[Calendar.SECOND])
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            )
            calender = Calendar.Builder().setInstant(businessDay.get().close).build()
            val close = Date.from(
                LocalDateTime.now()
                    .withHour(calender[Calendar.HOUR_OF_DAY])
                    .withMinute(calender[Calendar.MINUTE])
                    .withSecond(calender[Calendar.SECOND])
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            )
            val lowerBoundTime = Date.from(
                LocalDateTime.now().plusHours(2)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            )
            val upperBoundTime = Date.from(
                LocalDateTime.now().plusHours(2)
                    .plusMinutes(14) //should not accept orders 15 minutes before store closes
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            )
            return (lowerBoundTime.after(open)
                    && upperBoundTime.before(close))
        }

    enum class AVAILABILITY {
        OFFLINE, SPECIFIC_HOURS, ONLINE24_7
    }
}
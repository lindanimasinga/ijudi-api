package io.curiousoft.izinga.recon

import io.curiousoft.izinga.commons.model.Bank
import io.curiousoft.izinga.commons.model.BankAccType
import io.curiousoft.izinga.commons.model.ProfileRoles
import io.curiousoft.izinga.commons.model.UserProfile
import io.curiousoft.izinga.commons.profile.events.ProfileUpdatedEvent
import io.curiousoft.izinga.commons.repo.StoreRepository
import io.curiousoft.izinga.commons.repo.UserProfileRepo
import io.curiousoft.izinga.recon.payout.MessengerPayout
import io.curiousoft.izinga.recon.payout.PayoutStage
import io.curiousoft.izinga.recon.payout.repo.MessengerPayoutRepository
import io.curiousoft.izinga.recon.payout.repo.ShopPayoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class ReconServiceImplProfileUpdateTest {

    private val storeRepo = mockk<StoreRepository>()
    private val userProfileRepo = mockk<UserProfileRepo>()
    private val shopPayoutRepository = mockk<ShopPayoutRepository>()
    private val messengerPayoutRepository = mockk<MessengerPayoutRepository>()
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>()

    private lateinit var sut: ReconServiceImpl

    @BeforeEach
    fun setUp() {
        sut = ReconServiceImpl(
            storeRepo = storeRepo,
            userProfileRepo = userProfileRepo,
            shopPayoutRepo = shopPayoutRepository,
            messengerPayoutRepository = messengerPayoutRepository,
            applicationEventPublisher = applicationEventPublisher
        )
    }

    @Test
    fun `profile update refreshes pending messenger payout banking details`() {
        val messenger = UserProfile(
            "messenger-name",
            UserProfile.SignUpReason.DELIVERY_DRIVER,
            "address",
            "https://image.url",
            "0810000001",
            ProfileRoles.MESSENGER
        ).apply {
            id = "messenger-1"
            bank = Bank().apply {
                name = "New Bank"
                accountId = "+2782000111"
                type = BankAccType.SAVINGS
                phone = "0810000001"
                branchCode = "250655"
            }
        }

        val pendingPayout1 = MessengerPayout(
            toId = "messenger-1",
            toName = "Messenger 1",
            toBankName = "Old Bank",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "0111111111",
            toBranchCode = "000001",
            fromReference = "from",
            toReference = "to",
            emailNotify = "",
            orders = mutableSetOf(),
            emailAddress = "m1@x.com",
            emailSubject = "subject"
        )
        val pendingPayout2 = MessengerPayout(
            toId = "messenger-1",
            toName = "Messenger 1",
            toBankName = "Older Bank",
            toType = BankAccType.CHEQUE,
            toAccountNumber = "0222222222",
            toBranchCode = "000002",
            fromReference = "from",
            toReference = "to",
            emailNotify = "",
            orders = mutableSetOf(),
            emailAddress = "m1@x.com",
            emailSubject = "subject"
        )

        val updatedPayouts = slot<Iterable<MessengerPayout>>()
        every {
            messengerPayoutRepository.findAllByToIdAndPayoutStage("messenger-1", PayoutStage.PENDING)
        } returns listOf(pendingPayout1, pendingPayout2)
        every { messengerPayoutRepository.saveAll(capture(updatedPayouts)) } returnsArgument 0

        sut.handleProfileUpdated(ProfileUpdatedEvent(this, messenger))

        val savedPayouts = updatedPayouts.captured.toList()
        assertEquals(2, savedPayouts.size)
        savedPayouts.forEach {
            assertEquals("New Bank", it.toBankName)
            assertEquals(BankAccType.SAVINGS, it.toType)
            assertEquals("082000111", it.toAccountNumber)
            assertEquals("250655", it.toBranchCode)
        }
    }
}

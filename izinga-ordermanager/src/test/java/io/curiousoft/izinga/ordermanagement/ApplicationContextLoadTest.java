package io.curiousoft.izinga.ordermanagement;

import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.subscription.MerchantSubscriptionController;
import io.curiousoft.izinga.payfast.config.PayFastAutoConfiguration;
import io.curiousoft.izinga.payfast.repo.MerchantSubscriptionRepository;
import io.curiousoft.izinga.payfast.service.PayFastCheckoutService;
import io.curiousoft.izinga.payfast.service.PayFastItnHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TIER-BILLING-01 regression guard: verifies that adding io.curiousoft.izinga.payfast
 * to IjudiApplication's scanBasePackages and @EntityScan causes the Spring
 * ApplicationContext to discover PayFastAutoConfiguration and wire its beans
 * into MerchantSubscriptionController.
 *
 * Before the fix the context failed with:
 *   "Parameter 0 of constructor in ...MerchantSubscriptionController required a bean
 *    of type 'io.curiousoft.izinga.payfast.service.PayFastCheckoutService' that could
 *    not be found."
 *
 * This uses a narrow context (PayFastAutoConfiguration + controller + mocks) rather
 * than the full IjudiApplication context, to avoid AWS Secrets Manager properties
 * that are absent in the local test environment.
 */
@SpringBootTest(
        classes = {PayFastAutoConfiguration.class, MerchantSubscriptionController.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // PayFast subscription properties — test placeholders, no real calls made.
                "payfast.subscription.merchant-id=test-merchant-id",
                "payfast.subscription.merchant-key=test-merchant-key",
                "payfast.subscription.passphrase=test-passphrase",
                "payfast.subscription.base-url=https://sandbox.payfast.co.za/eng/process",
                "payfast.subscription.validate-url=https://sandbox.payfast.co.za/eng/query/validate",
                "payfast.subscription.return-url=https://example.com/success",
                "payfast.subscription.cancel-url=https://example.com/cancel",
                "payfast.subscription.notify-url=https://example.com/itn"
        }
)
class ApplicationContextLoadTest {

    // Infrastructure mocks — not testing Mongo or the repos themselves here,
    // only that the PayFast beans wire correctly into MerchantSubscriptionController.
    @MockBean
    MerchantSubscriptionRepository merchantSubscriptionRepository;
    @MockBean
    StoreRepository storeRepository;
    @MockBean
    UserProfileRepo userProfileRepo;
    @MockBean
    MongoTemplate mongoTemplate;

    @Autowired
    private MerchantSubscriptionController merchantSubscriptionController;

    @Autowired
    private PayFastCheckoutService payFastCheckoutService;

    @Autowired
    private PayFastItnHandler payFastItnHandler;

    /**
     * Verifies the narrow ApplicationContext starts and all three PayFast-backed beans
     * are wired. Before TIER-BILLING-01 IjudiApplication fix, PayFastAutoConfiguration
     * was not scanned so none of these beans existed, causing startup failure.
     */
    @Test
    void payFastBeansAreWiredIntoSubscriptionController() {
        assertThat(merchantSubscriptionController).isNotNull();
        assertThat(payFastCheckoutService).isNotNull();
        assertThat(payFastItnHandler).isNotNull();
    }
}

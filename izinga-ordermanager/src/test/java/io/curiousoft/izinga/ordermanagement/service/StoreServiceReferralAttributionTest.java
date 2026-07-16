package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.*;
import io.curiousoft.izinga.commons.repo.StoreRepository;
import io.curiousoft.izinga.commons.repo.UserProfileRepo;
import io.curiousoft.izinga.ordermanagement.stores.StoreService;
import io.curiousoft.izinga.usermanagement.referral.ReferralCodeService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;

import java.time.DayOfWeek;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * RP-005a: Unit tests for store referral attribution capture at store registration.
 */
@RunWith(MockitoJUnitRunner.class)
public class StoreServiceReferralAttributionTest {

    private static final String MAIN_PAY_ACCOUNT = "acc-123";
    private static final double MARKUP = 0.1;

    @Mock StoreRepository storeRepository;
    @Mock UserProfileRepo userProfileRepo;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ReferralCodeService referralCodeService;

    private StoreService storeService;

    @Before
    public void setUp() {
        storeService = new StoreService(storeRepository, userProfileRepo, MAIN_PAY_ACCOUNT, MARKUP, eventPublisher, referralCodeService);
    }

    @Test
    public void create_withValidReferralCode_setsReferredByPartnerId() throws Exception {
        String referralCode = "ABC12345";
        String partnerId = "partner-001";

        UserProfile partner = partnerProfile(partnerId);
        when(referralCodeService.resolveCode(referralCode)).thenReturn(partner);

        StoreProfile store = storeProfile();
        UserProfile owner = ownerProfile();
        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileRepo.save(any())).thenReturn(owner);

        StoreProfile result = storeService.create(store, referralCode);

        Assert.assertEquals(partnerId, result.getReferredByPartnerId());
        verify(referralCodeService).resolveCode(referralCode);
    }

    @Test
    public void create_withNullReferralCode_doesNotCallResolveCode() throws Exception {
        StoreProfile store = storeProfile();
        UserProfile owner = ownerProfile();
        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileRepo.save(any())).thenReturn(owner);

        StoreProfile result = storeService.create(store, null);

        Assert.assertNull(result.getReferredByPartnerId());
        verify(referralCodeService, never()).resolveCode(anyString());
    }

    @Test
    public void create_withBlankReferralCode_doesNotCallResolveCode() throws Exception {
        StoreProfile store = storeProfile();
        UserProfile owner = ownerProfile();
        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileRepo.save(any())).thenReturn(owner);

        StoreProfile result = storeService.create(store, "   ");

        Assert.assertNull(result.getReferredByPartnerId());
        verify(referralCodeService, never()).resolveCode(anyString());
    }

    @Test
    public void create_withUnrecognisedReferralCode_doesNotSetReferredByPartnerId() throws Exception {
        when(referralCodeService.resolveCode("NOTFOUND")).thenReturn(null);

        StoreProfile store = storeProfile();
        UserProfile owner = ownerProfile();
        when(userProfileRepo.findById("owner-001")).thenReturn(Optional.of(owner));
        when(storeRepository.findOneByIdOrShortName(any(), any())).thenReturn(Optional.empty());
        when(storeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userProfileRepo.save(any())).thenReturn(owner);

        StoreProfile result = storeService.create(store, "NOTFOUND");

        Assert.assertNull(result.getReferredByPartnerId());
        verify(referralCodeService).resolveCode("NOTFOUND");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private StoreProfile storeProfile() {
        Bank bank = new Bank();
        bank.setAccountId("acc-1");
        ArrayList<BusinessHours> hours = new ArrayList<>();
        hours.add(new BusinessHours(DayOfWeek.MONDAY, new Date(), new Date()));
        ArrayList<String> tags = new ArrayList<>();
        tags.add("food");
        return new StoreProfile(
                StoreType.FOOD, "Test Store", "test-store-unique",
                "1 Store St", "https://img.test/s.png", "0811111111",
                tags, hours, "owner-001", bank
        );
    }

    private UserProfile ownerProfile() {
        Bank bank = new Bank();
        bank.setAccountId("acc-owner");
        var p = new UserProfile("Owner", UserProfile.SignUpReason.SELL,
                "1 Owner St", "https://img.test/o.png", "0821111111", ProfileRoles.CUSTOMER);
        p.setId("owner-001");
        p.setBank(bank);
        return p;
    }

    private UserProfile partnerProfile(String id) {
        var p = new UserProfile("Partner", UserProfile.SignUpReason.BUY,
                "1 Partner St", "https://img.test/p.png", "0820000001", ProfileRoles.REFERRAL_PARTNER);
        p.setId(id);
        p.setReferralCode("ABC12345");
        return p;
    }
}

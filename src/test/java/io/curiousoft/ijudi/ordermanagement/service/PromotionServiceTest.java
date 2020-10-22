package io.curiousoft.ijudi.ordermanagement.service;

import io.curiousoft.ijudi.ordermanagement.model.Promotion;
import io.curiousoft.ijudi.ordermanagement.repo.PromotionRepo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PromotionServiceTest {

    //system under test
    private PromotionService profileService;
    @Mock
    private PromotionRepo promotionRepo;

    @Before
    public void setUp() {
        profileService = new PromotionService(promotionRepo);
    }

    @Test
    public void create() throws Exception {

        //given
        Promotion promotion = new Promotion(
                "http://image.url",
                "123456",
                new Date());

        //when
        when(promotionRepo.save(promotion)).thenReturn(promotion);
        Promotion newPromotion = profileService.create(promotion);

        //verify
        verify(promotionRepo).save(promotion);
        Assert.assertNotNull(promotion.getId());
    }

    @Test
    public void update() throws Exception {

        //given
        String profileId = "myID";
        Promotion promotion = new Promotion(
                "http://image.url",
                "123456",
                new Date());
        promotion.setId(profileId);

        Promotion promotion2 = new Promotion(
                "http://image.url2",
                "123456",
                new Date());

        //when
        when(promotionRepo.findById(profileId)).thenReturn(Optional.of(promotion));
        when(promotionRepo.save(promotion)).thenReturn(promotion);
        Promotion updatedPromotion = profileService.update(profileId, promotion2);

        //verify
        verify(promotionRepo).findById(profileId);
        verify(promotionRepo).save(promotion);
        Assert.assertEquals(promotion2.getImageUrl(), updatedPromotion.getImageUrl());
        Assert.assertEquals(promotion.getId(), updatedPromotion.getId());
    }

    @Test
    public void delete() {
        //given
        String profileId = "myID";
        //when

        profileService.delete(profileId);

        //verify
        verify(promotionRepo).deleteById(profileId);
    }

    @Test
    public void find() {

        //given
        String profileId = "myID";
        //when

        Promotion profile = profileService.find(profileId);

        //verify
        verify(promotionRepo).findById(profileId);
    }

    @Test
    public void findAll() {

        //given
        String profileId = "myID";
        //when

        List<Promotion> profile = profileService.findAll(null);

        //verify
        verify(promotionRepo).findByExpiryDateAfter(any(Date.class));
    }

    @Test
    public void findAllByStoreId() {

        //given
        String profileId = "myID";
        String storeId = "storeID";
        //when

        List<Promotion> profile = profileService.findAll(storeId);

        //verify
        verify(promotionRepo).findByShopIdAndExpiryDateAfter(eq(storeId), any(Date.class));
    }
}
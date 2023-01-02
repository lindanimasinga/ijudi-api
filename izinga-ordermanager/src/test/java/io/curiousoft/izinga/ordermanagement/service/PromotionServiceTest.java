package io.curiousoft.izinga.ordermanagement.service;

import io.curiousoft.izinga.commons.model.Promotion;
import io.curiousoft.izinga.commons.model.StoreType;
import io.curiousoft.izinga.commons.repo.PromotionRepo;
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
    private PromotionService promotionService;
    @Mock
    private PromotionRepo promotionRepo;

    @Before
    public void setUp() {
        promotionService = new PromotionService(promotionRepo);
    }

    @Test
    public void create() throws Exception {

        //given
        Promotion promotion = new Promotion(
                "http://image.url",
                "123456",
                StoreType.FOOD,
                new Date());

        //when
        when(promotionRepo.save(promotion)).thenReturn(promotion);
        Promotion newPromotion = promotionService.create(promotion);

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
                StoreType.FOOD,
                new Date());
        promotion.setId(profileId);

        Promotion promotion2 = new Promotion(
                "http://image.url2",
                "123456",
                StoreType.FOOD,
                new Date());

        //when
        when(promotionRepo.findById(profileId)).thenReturn(Optional.of(promotion));
        when(promotionRepo.save(promotion)).thenReturn(promotion);
        Promotion updatedPromotion = promotionService.update(profileId, promotion2);

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

        promotionService.delete(profileId);

        //verify
        verify(promotionRepo).deleteById(profileId);
    }

    @Test
    public void find() {

        //given
        String promotionId = "myID";

        //when
        promotionService.find(promotionId);

        //verify
        verify(promotionRepo).findById(promotionId);
    }

    @Test
    public void findAll() {

        //given
        String profileId = "myID";
        StoreType storeType = StoreType.FOOD;
        //when
        promotionService.findAll(null, storeType);

        //verify
        verify(promotionRepo).findByExpiryDateAfterAndShopType(any(Date.class), eq(storeType));
    }

    @Test
    public void findAllByStoreId() {

        //given
        String profileId = "myID";
        String storeId = "storeID";
        StoreType storeType = StoreType.FOOD;
        //when

        List<Promotion> profile = promotionService.findAll(storeId, storeType);

        //verify
        verify(promotionRepo).findByShopIdAndExpiryDateAfterAndShopType(eq(storeId), any(Date.class), eq(storeType));
    }
}
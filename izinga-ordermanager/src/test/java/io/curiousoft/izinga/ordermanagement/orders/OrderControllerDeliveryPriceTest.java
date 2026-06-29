package io.curiousoft.izinga.ordermanagement.orders;

import io.curiousoft.izinga.commons.order.OrderService;
import io.curiousoft.izinga.commons.order.DeliveryPriceEstimateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderControllerDeliveryPriceTest {

    @Mock
    private OrderService orderService;

    @Test
    public void getDeliveryPrice_returnsEstimatedPrice_whenRequestValid() {
        OrderController controller = new OrderController(orderService, List.of("app://izinga"));
        DeliveryPriceEstimateDto serviceResult = new DeliveryPriceEstimateDto(
            "Truck Delivery Driver",
            "A",
            "B",
            5.0,
            100.0,
            3.0,
            10.0,
            120.0
        );

        when(orderService.getDeliveryPriceEstimate("Truck Delivery Driver", "A", "B", "shop-1"))
                .thenReturn(serviceResult);

        ResponseEntity<?> response = controller.getDeliveryPrice("Truck Delivery Driver", "A", "B", "shop-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(serviceResult, response.getBody());
        verify(orderService).getDeliveryPriceEstimate("Truck Delivery Driver", "A", "B", "shop-1");
    }

    @Test
    public void getDeliveryPrice_returnsBadRequest_whenServiceThrowsIllegalArgumentException() {
        OrderController controller = new OrderController(orderService, List.of("app://izinga"));

        when(orderService.getDeliveryPriceEstimate("", "A", "B", null))
                .thenThrow(new IllegalArgumentException("category is required"));

        ResponseEntity<?> response = controller.getDeliveryPrice("", "A", "B", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("category is required", ((Map<?, ?>) response.getBody()).get("error"));
        verify(orderService).getDeliveryPriceEstimate("", "A", "B", null);
    }
}

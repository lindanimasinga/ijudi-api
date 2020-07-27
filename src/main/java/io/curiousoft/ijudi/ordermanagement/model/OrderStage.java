package io.curiousoft.ijudi.ordermanagement.model;

public enum OrderStage {

    STAGE_0_CUSTOMER_NOT_PAID,
    STAGE_1_WAITING_STORE_CONFIRM,
    STAGE_2_STORE_PROCESSING,
    STAGE_3_READY_FOR_COLLECTION,
    STAGE_4_ON_THE_ROAD,
    STAGE_5_ARRIVED,
    STAGE_6_WITH_CUSTOMER,
    STAGE_7_ALL_PAID
}

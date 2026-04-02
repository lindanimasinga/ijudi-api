Feature: Order Management
  As a customer
  I want to place and manage orders
  So that I can purchase products from stores

  Scenario: Calculate order total amount with delivery fee
    Given a customer places an order with the following basket items
      | name    | quantity | price  |
      | chips   | 2        | 10.111 |
      | hotdog  | 1        | 20.0   |
    And the order has a delivery fee of 11.4933
    And the order has a service fee of 1.99
    When the total amount is calculated
    Then the total amount should be 53.71
    And the basket amount should be 40.22

  Scenario: Order total amount without delivery fee (free delivery)
    Given a customer places an order with the following basket items
      | name   | quantity | price |
      | burger | 1        | 50.0  |
    And the order has free delivery
    And the order has a service fee of 2.0
    When the total amount is calculated
    Then the total amount should be 52.0

  Scenario: Order stage transitions
    Given an order in stage STAGE_0_CUSTOMER_NOT_PAID
    When the order stage is updated to STAGE_1_WAITING_STORE_CONFIRM
    Then the order stage should be STAGE_1_WAITING_STORE_CONFIRM

Feature: Store Management
  As a store owner
  I want to manage my store profile and availability
  So that customers can find and order from my store

  Scenario: Store starts offline by default
    Given a new store profile is created
    Then the store availability should be OFFLINE

  Scenario: Store with business hours is configured
    Given a new store profile is created
    When business hours are added for TUESDAY
    Then the store should have 2 business hour entries

  Scenario: Store stock list is initially empty
    Given a new store profile is created
    Then the store stock list should be empty

  Scenario: Store with tags can be created
    Given a store with the tag "Pizza"
    Then the store tags should contain "Pizza"

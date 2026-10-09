package com.booking.catalog.domain;

/** Something added to a ride, such as a child seat. */
public record Extra(String code, String name, String description, int maxQuantity, int sortOrder, boolean active) {
}

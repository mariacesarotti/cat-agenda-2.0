package com.catagenda.reasoning;

import com.catagenda.cat.Cat;

import java.time.LocalDate;

/**
 * Next-due checkup for a single cat (P2-1). A {@code record} rather than a
 * plain class: this is a DTO built once by reasoning and only read
 * afterward, so the constructor/accessors/equals/hashCode/toString that
 * {@code record} generates from the field list are all this type needs —
 * unlike CatForm/VaccinationForm, which stay mutable classes for
 * Thymeleaf's form-binding (no-arg constructor + setters).
 */
public record DueCheckup(Cat cat, LocalDate dueDate, boolean overdue) {
}

package com.catagenda.reasoning;

import com.catagenda.cat.Cat;
import com.catagenda.cat.VaccineType;

import java.time.LocalDate;

/** Next-due date for a single cat/vaccine-type pair (P2-2). */
public record DueVaccine(Cat cat, VaccineType vaccineType, LocalDate dueDate, boolean overdue) {
}

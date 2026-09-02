package com.catagenda.cat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * Backs both the add-checkup and edit-checkup forms. A checkup only has one
 * user-entered field — the date it happened — unlike VaccinationForm, which
 * also carries a vaccine type.
 */
public class CheckupForm {

    @NotNull(message = "The visit date is required to track checkup history accurately.")
    @PastOrPresent(message = "A checkup date can't be in the future.")
    private LocalDate visitDate;

    public CheckupForm() {
    }

    public CheckupForm(LocalDate visitDate) {
        this.visitDate = visitDate;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(LocalDate visitDate) {
        this.visitDate = visitDate;
    }
}

package com.catagenda.cat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class VaccinationForm {

    @NotNull(message = "Pick a vaccine type — even 'other' counts.")
    private VaccineType vaccineType;

    @NotNull(message = "The date administered is required to track boosters accurately.")
    @PastOrPresent(message = "A vaccination date can't be in the future.")
    private LocalDate dateAdministered;

    @Size(max = 255, message = "Keep the description under 255 characters.")
    private String otherDescription;

    public VaccinationForm() {
    }

    public VaccinationForm(VaccineType vaccineType, LocalDate dateAdministered, String otherDescription) {
        this.vaccineType = vaccineType;
        this.dateAdministered = dateAdministered;
        this.otherDescription = otherDescription;
    }

    public VaccineType getVaccineType() {
        return vaccineType;
    }

    public void setVaccineType(VaccineType vaccineType) {
        this.vaccineType = vaccineType;
    }

    public LocalDate getDateAdministered() {
        return dateAdministered;
    }

    public void setDateAdministered(LocalDate dateAdministered) {
        this.dateAdministered = dateAdministered;
    }

    public String getOtherDescription() {
        return otherDescription;
    }

    public void setOtherDescription(String otherDescription) {
        this.otherDescription = otherDescription;
    }
}

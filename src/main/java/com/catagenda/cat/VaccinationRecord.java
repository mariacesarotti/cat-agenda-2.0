package com.catagenda.cat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "vaccination_record")
public class VaccinationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = false)
    private Cat cat;

    @Enumerated(EnumType.STRING)
    @Column(name = "vaccine_type", nullable = false)
    private VaccineType vaccineType;

    @Column(name = "date_administered", nullable = false)
    private LocalDate dateAdministered;

    // Only meaningful when vaccineType == OTHER; see VaccineType and T4.1.
    @Column(name = "other_description")
    private String otherDescription;

    protected VaccinationRecord() {
    }

    public VaccinationRecord(Cat cat, VaccineType vaccineType, LocalDate dateAdministered, String otherDescription) {
        this.cat = cat;
        this.vaccineType = vaccineType;
        this.dateAdministered = dateAdministered;
        this.otherDescription = otherDescription;
    }

    public Long getId() {
        return id;
    }

    public Cat getCat() {
        return cat;
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

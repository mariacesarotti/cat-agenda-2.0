package com.catagenda.cat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Records that a vet checkup actually happened on a given date (P1-12).
 * Same shape as {@link VaccinationRecord} minus the type dimension — a
 * checkup isn't typed, just dated. Multiple records per cat are expected
 * (visit history), same as vaccination boosters.
 */
@Entity
@Table(name = "checkup_record")
public class CheckupRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cat_id", nullable = false)
    private Cat cat;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    protected CheckupRecord() {
    }

    public CheckupRecord(Cat cat, LocalDate visitDate) {
        this.cat = cat;
        this.visitDate = visitDate;
    }

    public Long getId() {
        return id;
    }

    public Cat getCat() {
        return cat;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(LocalDate visitDate) {
        this.visitDate = visitDate;
    }
}

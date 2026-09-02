package com.catagenda.cat;

import com.catagenda.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cat")
public class Cat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne: many Cat rows can point at the same User. JPA maps this to
    // a foreign key column (user_id) on the "cat" table, the many side. This
    // is what makes ownership queryable/enforceable at the database level —
    // ADR 0001's whole reason for a normalized schema over a flat cat list.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String name;

    private Integer age;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Column(name = "spayed_neutered")
    private Boolean spayedNeutered;

    // Nullable, same optionality convention as spayedNeutered (ADR 0005):
    // null means "not confirmed," not "false." Drives S1/S3's outdoor-only
    // reasoning rules (flea/tick supply need, FeLV vaccine relevance) added
    // in Phase 2 — see VaccinationReasoningService/SupplyReasoningService.
    @Column(name = "outdoor_access")
    private Boolean outdoorAccess;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // The inverse side of @ManyToOne above: one Cat has many child records.
    // cascade = ALL + orphanRemoval = true means deleting a Cat (or removing
    // an item from these lists) deletes the matching child rows too — chosen
    // over explicit service-level deletion (T2.9) so "delete a cat" can't
    // accidentally leave orphaned condition/vaccination rows if a future
    // code path forgets to clean them up manually.
    @OneToMany(mappedBy = "cat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("name ASC")
    private List<Condition> conditions = new ArrayList<>();

    @OneToMany(mappedBy = "cat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("vaccineType ASC, dateAdministered DESC")
    private List<VaccinationRecord> vaccinationRecords = new ArrayList<>();

    // Phase 1 addendum (P1-12/P1-13): same shape/cascade convention as
    // vaccinationRecords above. Ordered most-recent-first for the profile
    // view's "reverse-chronological" requirement; CheckupService's own
    // findFirstByCatOrderByVisitDateDesc query (not this collection) is
    // what the reasoning layer reads (T0.3), so the two stay independent.
    @OneToMany(mappedBy = "cat", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("visitDate DESC")
    private List<CheckupRecord> checkupRecords = new ArrayList<>();

    protected Cat() {
    }

    public Cat(User owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public Boolean getSpayedNeutered() {
        return spayedNeutered;
    }

    public void setSpayedNeutered(Boolean spayedNeutered) {
        this.spayedNeutered = spayedNeutered;
    }

    public Boolean getOutdoorAccess() {
        return outdoorAccess;
    }

    public void setOutdoorAccess(Boolean outdoorAccess) {
        this.outdoorAccess = outdoorAccess;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public List<VaccinationRecord> getVaccinationRecords() {
        return vaccinationRecords;
    }

    public List<CheckupRecord> getCheckupRecords() {
        return checkupRecords;
    }
}

package com.catagenda.cat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Backs both the add-cat and edit-cat forms. Only {@code name} is required
 * (T2.4, client decision) — age/sex/spayed-neutered are optional at
 * creation and can be filled in later via edit.
 */
public class CatForm {

    @NotBlank(message = "Every operative needs a name.")
    private String name;

    @Min(value = 0, message = "Age can't be negative.")
    @Max(value = 40, message = "That age seems implausible, even for a cat this cunning.")
    private Integer age;

    private Sex sex;

    private Boolean spayedNeutered;

    private Boolean outdoorAccess;

    public CatForm() {
    }

    public CatForm(String name, Integer age, Sex sex, Boolean spayedNeutered) {
        this(name, age, sex, spayedNeutered, null);
    }

    public CatForm(String name, Integer age, Sex sex, Boolean spayedNeutered, Boolean outdoorAccess) {
        this.name = name;
        this.age = age;
        this.sex = sex;
        this.spayedNeutered = spayedNeutered;
        this.outdoorAccess = outdoorAccess;
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
}

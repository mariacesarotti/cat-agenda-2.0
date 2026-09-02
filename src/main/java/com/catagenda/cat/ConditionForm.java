package com.catagenda.cat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ConditionForm {

    @NotBlank(message = "Every condition needs a name — even secret ones.")
    private String name;

    @Size(max = 2000, message = "That's a lot of notes. Trim it down a little.")
    private String notes;

    public ConditionForm() {
    }

    public ConditionForm(String name, String notes) {
        this.name = name;
        this.notes = notes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

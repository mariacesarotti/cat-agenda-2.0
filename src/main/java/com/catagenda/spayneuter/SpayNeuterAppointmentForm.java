package com.catagenda.spayneuter;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Backs the schedule/edit-appointment form (P6-2). A single user-entered
 * field, same shape as {@code CheckupForm} — unlike a checkup's visit date,
 * an appointment date is expected to be in the future (or today), so it
 * deliberately carries no {@code @PastOrPresent}-style constraint.
 */
public class SpayNeuterAppointmentForm {

    @NotNull(message = "Pick a date for the appointment — the agenda needs a target to work toward.")
    private LocalDate appointmentDate;

    public SpayNeuterAppointmentForm() {
    }

    public SpayNeuterAppointmentForm(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }
}

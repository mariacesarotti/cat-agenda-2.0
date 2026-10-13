package com.catagenda.spayneuter;

/**
 * Thrown when trying to schedule/edit an appointment date (P6-2) for a cat
 * with no {@link SpayNeuterPlan} row — intent must be flagged (P6-1) first.
 * See {@link AlreadySpayedNeuteredException}'s note on why this is a plain
 * {@code RuntimeException} rather than a {@code BusinessRuleException}: the
 * appointment form still exists here, but this guard is enforced
 * server-side regardless of whatever the UI already hides, and the
 * controller surfaces it inline on that form rather than via
 * {@code BindingResult} field-rejection (there's no single form field this
 * error belongs to — the whole action is invalid).
 */
public class SpayNeuterIntentNotFlaggedException extends RuntimeException {

    public SpayNeuterIntentNotFlaggedException(String message) {
        super(message);
    }
}

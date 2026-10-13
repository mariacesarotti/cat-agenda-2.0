package com.catagenda.spayneuter;

/**
 * Thrown when flagging spay/neuter intent (P6-1) on a cat whose Phase 1
 * {@code spayedNeutered} status is already {@code true} — the AC's "on a
 * cat that isn't already marked spayed/neutered" guard. Not a
 * {@link com.catagenda.common.BusinessRuleException}: that base type exists
 * for form-bound validation errors a controller feeds back into
 * {@code BindingResult} (see {@code MissingOtherDescriptionException}), but
 * flagging intent is a plain POST button, not a form — the controller
 * catches this directly and surfaces the message as a flash attribute
 * instead, the same "flash message for a non-form action's error" pattern
 * {@code CartController} already uses for its date-parsing/future-date
 * guards.
 */
public class AlreadySpayedNeuteredException extends RuntimeException {

    public AlreadySpayedNeuteredException(String message) {
        super(message);
    }
}

package com.catagenda.common;

/**
 * Base type for service-level validation failures that Bean Validation
 * can't express because they require a DB lookup or cross-field business
 * logic (e.g. "email already registered", "password too weak"). Carries
 * the form field the error belongs to so controllers can uniformly wire
 * it back into BindingResult and re-render the form, the same way for
 * every feature (see T5.1) instead of each controller inventing its own
 * error handling.
 */
public abstract class BusinessRuleException extends RuntimeException {

    private final String field;

    protected BusinessRuleException(String field, String onBrandMessage) {
        super(onBrandMessage);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

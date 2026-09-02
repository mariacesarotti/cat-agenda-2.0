package com.catagenda.cat;

import com.catagenda.common.BusinessRuleException;

/**
 * Thrown when vaccineType == OTHER but otherDescription is blank. This is
 * a cross-field rule (one field's requiredness depends on another field's
 * value), which is why it's enforced here rather than with a plain Bean
 * Validation annotation on VaccinationForm — same convention as
 * DuplicateEmailException/WeakPasswordException (T5.1).
 */
public class MissingOtherDescriptionException extends BusinessRuleException {

    public MissingOtherDescriptionException() {
        super("otherDescription",
                "Classify the mystery vaccine — a name or brief description is required when the type is \"Other.\"");
    }
}

package com.catagenda.user;

import com.catagenda.common.BusinessRuleException;

public class WeakPasswordException extends BusinessRuleException {

    public WeakPasswordException() {
        super("password", "That password wouldn't survive a single security audit, let alone a takeover. "
                + "Use at least " + UserService.MIN_PASSWORD_LENGTH + " characters.");
    }
}

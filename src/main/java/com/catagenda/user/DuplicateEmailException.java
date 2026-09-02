package com.catagenda.user;

import com.catagenda.common.BusinessRuleException;

public class DuplicateEmailException extends BusinessRuleException {

    public DuplicateEmailException(String email) {
        super("email", "That email is already commanding a household. "
                + "Log in instead, or use another address to recruit a second base of operations.");
    }
}

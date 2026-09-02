package com.catagenda.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown whenever a lookup by id fails, including when a record exists but
 * doesn't belong to the current user. Ownership failures are deliberately
 * indistinguishable from "doesn't exist" — a 403 that confirms a record
 * exists (just not yours) would leak information a not-found response
 * doesn't.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

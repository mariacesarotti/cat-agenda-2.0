package com.catagenda.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Data the registration form binds to. Kept separate from the {@link User}
 * entity so the web layer never binds request parameters straight onto a
 * persistence object (mass-assignment risk) and so validation annotations
 * describe "what a valid submission looks like," not "what a stored row
 * looks like" — those aren't always the same shape.
 */
public class RegistrationForm {

    @NotBlank(message = "The recruitment form needs an email address.")
    @Email(message = "That doesn't look like a real email address.")
    private String email;

    @NotBlank(message = "A password is required to guard the secret plans.")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

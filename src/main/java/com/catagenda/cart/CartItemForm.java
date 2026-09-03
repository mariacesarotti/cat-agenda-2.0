package com.catagenda.cart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Backs the manual-add form (P4-4) — a custom item needs "at least a name." */
public class CartItemForm {

    @NotBlank(message = "Every requisition needs a name — even a mysterious one.")
    @Size(max = 200, message = "That name's a bit much for a supply list. Keep it under 200 characters.")
    private String name;

    public CartItemForm() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

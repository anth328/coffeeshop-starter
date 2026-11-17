package ek.osnb.demo.coffeeshop.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}

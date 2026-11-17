package ek.osnb.demo.coffeeshop.security;

import java.util.List;

record MeResponse(
        String username,
        List<String> roles
) {}

package ek.osnb.demo.coffeeshop.security;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RegistrationController {

    private final AccountService accountService;

    RegistrationController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/api/register")
    ResponseEntity<Void> register(
            @Valid @RequestBody RegisterRequest request) {

        accountService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}

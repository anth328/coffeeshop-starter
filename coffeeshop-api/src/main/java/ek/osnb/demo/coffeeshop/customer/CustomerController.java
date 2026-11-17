package ek.osnb.demo.coffeeshop.customer;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/me")
    CustomerResponse me(Authentication authentication) {
        return service.findByAuthenticatedEmail(
                authentication.getName()
        );
    }

    @PutMapping("/me")
    CustomerResponse updateMe(
            Authentication authentication,
            @Valid @RequestBody CustomerRequest request) {

        return service.saveProfile(
                authentication.getName(), request
        );
    }
}
package ek.osnb.demo.coffeeshop.order;

import ek.osnb.demo.coffeeshop.common.NotFoundException;
import ek.osnb.demo.coffeeshop.customer.CustomerApi;
import ek.osnb.demo.coffeeshop.customer.CustomerInfo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService service;
    private final CustomerApi customers;

    OrderController(OrderService service, CustomerApi customers) {
        this.service = service;
        this.customers = customers;
    }

    private Long authenticatedCustomerId(Authentication authentication) {
        return customers.findCustomerByEmail(authentication.getName())
                .map(CustomerInfo::id)
                .orElseThrow(() ->
                        new NotFoundException("Customer profile not found"));
    }

    // Checkout: turns a cart into an order
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse placeOrder(
            Authentication authentication,
            @Valid @RequestBody PlaceOrderRequest request) {

        Long customerId = authenticatedCustomerId(authentication);

        return service.placeOrder(customerId, request);
    }

    // GET /api/orders?customerId=1 - newest first
    @GetMapping
    List<OrderSummaryResponse> findMyOrders(Authentication authentication) {
        Long customerId = authenticatedCustomerId(authentication);
        return service.findByCustomer(customerId);
    }

    @GetMapping("/{id}")
    OrderResponse findById(
            @PathVariable Long id,
            Authentication authentication) {

        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"))) {
            return service.findById(id);
        }

        return service.findOwnedOrder(
                authenticatedCustomerId(authentication), id
        );
    }

    @PostMapping("/{id}/cancel")
    OrderResponse cancel(
            @PathVariable Long id,
            Authentication authentication) {

        return service.cancelOwnedOrder(
                authenticatedCustomerId(authentication), id
        );
    }

    // Should only be allowed for staff - a job for Spring Security later
    @PostMapping("/{id}/ship")
    OrderResponse ship(@PathVariable Long id) {
        return service.ship(id);
    }
}

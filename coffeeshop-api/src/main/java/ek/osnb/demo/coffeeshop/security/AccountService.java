package ek.osnb.demo.coffeeshop.security;

import ek.osnb.demo.coffeeshop.common.ConflictException;
import ek.osnb.demo.coffeeshop.common.InvalidInputException;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
class AccountService {

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    AccountService(AccountRepository repository,
                   PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    void register(RegisterRequest request) {

        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (request.password().length() < 8) {
            throw new InvalidInputException(
                    "Password must contain at least 8 characters"
            );
        }

        if (repository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }

        String hash = passwordEncoder.encode(request.password());

        repository.save(Account.create(email, hash));
    }
}

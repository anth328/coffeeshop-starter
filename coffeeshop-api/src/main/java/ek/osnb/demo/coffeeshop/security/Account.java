package ek.osnb.demo.coffeeshop.security;

import jakarta.persistence.*;

@Entity
@Table(name = "accounts")
class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected Account() {}

    static Account create(String email, String passwordHash) {
        Account account = new Account();
        account.email = email;
        account.passwordHash = passwordHash;
        account.role = Role.CUSTOMER;
        return account;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Role getRole() {
        return role;
    }
}
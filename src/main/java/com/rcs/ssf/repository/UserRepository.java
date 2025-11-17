package com.rcs.ssf.repository;

import com.rcs.ssf.entity.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Reactive repository for users backed by R2DBC.
 *
 * Note: This repository requires a R2DBC ConnectionFactory and the appropriate
 * r2dbc driver on the classpath. If you intend to use blocking JDBC instead,
 * replace this extends R2dbcRepository with a Spring Data JPA/CrudRepository
 * backed by a DataSource and configure the blocking repository package.
 */
public interface UserRepository extends R2dbcRepository<User, UUID> {
    Mono<User> findByUsername(String username);
    Mono<User> findByEmail(String email);
}

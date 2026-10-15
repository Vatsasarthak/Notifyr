package com.yash.Notifyr.repository;

import com.yash.Notifyr.entity.Role;
import com.yash.Notifyr.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByRole(Role role);
}

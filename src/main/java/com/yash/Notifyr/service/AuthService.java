package com.yash.Notifyr.service;

import com.yash.Notifyr.dto.AuthResponse;
import com.yash.Notifyr.dto.LoginRequest;
import com.yash.Notifyr.dto.RegisterRequest;
import com.yash.Notifyr.entity.Role;
import com.yash.Notifyr.entity.User;
import com.yash.Notifyr.exception.AdminRegistrationNotAllowedException;
import com.yash.Notifyr.exception.DuplicateUsernameException;
import com.yash.Notifyr.repository.UserRepository;
import com.yash.Notifyr.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import com.yash.Notifyr.exception.InvalidCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request){

        if(request.getRole() == Role.ADMIN && !canRegisterAdmin()){
            throw new AdminRegistrationNotAllowedException();
        }

        userRepository.findByUsername(request.getUsername()).ifPresent(user -> {
            throw new DuplicateUsernameException(request.getUsername());
        });

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername(),  user.getRole().toString());
        return new AuthResponse(token, user.getUsername(), user.getRole().toString());
    }

    public AuthResponse login(LoginRequest request){

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidCredentialsException::new);

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().toString());
        return new AuthResponse(token, user.getUsername(), user.getRole().toString());
    }

    // Only an authenticated ADMIN may create another ADMIN. The first ADMIN comes from configuration
    // (AdminBootstrap), so the public endpoint can never grant administrator rights, not even on a fresh install.
    private boolean canRegisterAdmin(){
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        return caller != null && caller.isAuthenticated() && caller.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}

package com.infernokun.amaterasu.services.alt;

import com.infernokun.amaterasu.exceptions.AuthFailedException;
import com.infernokun.amaterasu.exceptions.WrongPasswordException;
import com.infernokun.amaterasu.models.dto.LoginResponseDTO;
import com.infernokun.amaterasu.models.dto.RegistrationDTO;
import com.infernokun.amaterasu.models.entities.RefreshToken;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.enums.Role;
import com.infernokun.amaterasu.repositories.UserRepository;
import com.infernokun.amaterasu.services.BaseService;
import com.infernokun.amaterasu.services.entity.RefreshTokenService;
import com.infernokun.amaterasu.services.entity.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class AuthenticationService extends BaseService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, TokenService tokenService, RefreshTokenService refreshTokenService, UserService userService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
    }

    public boolean registerUser(RegistrationDTO user) {
        if (user == null || user.getUsername() == null || user.getPassword() == null) {
            throw new AuthFailedException("Username and password required!");
        }

        if (userService.existsByUsernameIgnoreCase(user.getUsername())) {
            throw new AuthFailedException("Username already exists!");
        }

        String encodedPassword = this.passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);

        User newUser = new User(user.getUsername(), user.getPassword());
        newUser.setRole(Role.MEMBER);

        userRepository.save(newUser);

        LOGGER.info("User registered: {}", newUser.getUsername());
        return true;
    }

    public LoginResponseDTO loginUser(String username, String password) {
        try {
            User user = userService.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), password)
            );

            User authenticatedUser = (User) auth.getPrincipal();
            String token = tokenService.generateJwt(authenticatedUser);
            return new LoginResponseDTO(token, authenticatedUser);

        } catch (BadCredentialsException e) {
            throw new WrongPasswordException("Invalid username or password");
        } catch (AuthenticationException e) {
            throw new AuthFailedException("Authentication failed");
        }
    }

    public LoginResponseDTO revalidateToken(String oldToken) {
        LOGGER.info("old token: {}", oldToken);
        RefreshToken oldRefreshToken = this.refreshTokenService.findByToken(oldToken);
        LOGGER.info("OLD REFRESH TOKEN - Found!");
        if (Objects.equals(oldRefreshToken.getToken(), oldToken)) {
            LOGGER.info("OLD REFRESH TOKEN - Matches database for user {}!", oldRefreshToken.getUser().getId());
            Optional<User> user = this.userRepository.findById(oldRefreshToken.getUser().getId());
            if (user.isPresent()) {
                LOGGER.info("OLD REFRESH TOKEN - Token replaced!");
                String token = this.tokenService.generateJwt(user.get());
                return new LoginResponseDTO(token, user.get());
            }
        }
        return null;
    }
}

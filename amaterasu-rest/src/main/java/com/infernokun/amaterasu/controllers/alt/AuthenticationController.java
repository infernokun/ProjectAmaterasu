package com.infernokun.amaterasu.controllers.alt;

import com.infernokun.amaterasu.controllers.BaseController;
import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.dto.LoginResponseDTO;
import com.infernokun.amaterasu.models.dto.RegistrationDTO;
import com.infernokun.amaterasu.models.entities.RefreshToken;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.services.alt.AuthenticationService;
import com.infernokun.amaterasu.services.entity.RefreshTokenService;
import com.infernokun.amaterasu.services.entity.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthenticationController extends BaseController {
    private final AuthenticationService authenticationService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    public AuthenticationController(AuthenticationService authenticationService, UserService userService,
                                    RefreshTokenService refreshTokenService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping(value = "/register", consumes = "application/json")
    public ResponseEntity<ApiResponse<Boolean>> registerUser(@RequestBody User user) {
        return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                .code(HttpStatus.OK.value())
                .message("User registered successfully.")
                .data(authenticationService.registerUser(user))
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> loginUser(@RequestBody RegistrationDTO user) {
        LoginResponseDTO loginResponseDTO = authenticationService.loginUser(
                user.getUsername(), user.getPassword());

        return ResponseEntity.ok(ApiResponse.<LoginResponseDTO>builder()
                .code(HttpStatus.OK.value())
                .message("Login successful..")
                .data(loginResponseDTO)
                .build());
    }

    @PostMapping("/token")
    public ResponseEntity<LoginResponseDTO> revalidateToken(@RequestBody String token) {
        return ResponseEntity.ok(authenticationService.revalidateToken(token));
    }

    @PostMapping("/token/check")
    public ResponseEntity<Boolean> checkToken(@RequestBody String token) {
        Optional<RefreshToken> refreshToken = refreshTokenService.findByToken(token);

        if (refreshToken.isPresent()) {
            return ResponseEntity.ok(true);
        }
        return ResponseEntity.ok(false);
    }

    @DeleteMapping("/token/logout/{username}")
    public ResponseEntity<?> logoutUser(@PathVariable String username) {
        Optional<RefreshToken> refreshTokenOptional = refreshTokenService.deleteToken(username);

        if (refreshTokenOptional.isPresent()) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.ok("Something crazy....");
        }
    }
}

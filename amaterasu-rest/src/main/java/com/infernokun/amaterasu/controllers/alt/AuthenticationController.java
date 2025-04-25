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
    public ResponseEntity<ApiResponse<Boolean>> registerUser(@RequestBody RegistrationDTO registrationDTO) {
        return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                .code(HttpStatus.OK.value())
                .message("User registered successfully.")
                .data(authenticationService.registerUser(registrationDTO))
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
    public ResponseEntity<ApiResponse<LoginResponseDTO>> revalidateToken(@RequestBody String token) {
        return ResponseEntity.ok(ApiResponse.<LoginResponseDTO>builder()
                .code(HttpStatus.OK.value())
                .message("Token revalidated")
                .data(authenticationService.revalidateToken(token))
                .build());
    }

    @PostMapping("/token/check")
    public ResponseEntity<ApiResponse<Boolean>> checkToken(@RequestBody String token) {
        return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                .code(HttpStatus.OK.value())
                .message("Token revalidated")
                .data(refreshTokenService.findByToken(token) != null)
                .build());
    }

    @DeleteMapping("/token/logout/{id}")
    public ResponseEntity<?> logoutUser(@PathVariable String id) {
        Optional<RefreshToken> refreshTokenOptional = refreshTokenService.deleteToken(id);

        if (refreshTokenOptional.isPresent()) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.ok("Something crazy....");
        }
    }
}

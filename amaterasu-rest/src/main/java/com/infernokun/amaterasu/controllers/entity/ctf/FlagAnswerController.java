package com.infernokun.amaterasu.controllers.entity.ctf;

import com.infernokun.amaterasu.models.ApiResponse;
import com.infernokun.amaterasu.models.dto.ctf.FlagAnswerRequest;
import com.infernokun.amaterasu.models.dto.ctf.AnsweredCTFEntityResponse;
import com.infernokun.amaterasu.models.entities.User;
import com.infernokun.amaterasu.models.entities.ctf.AnsweredCTFEntity;
import com.infernokun.amaterasu.services.entity.ctf.AnsweredCTFEntityService;
import com.infernokun.amaterasu.services.entity.ctf.FlagService;
import com.infernokun.amaterasu.services.entity.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import static com.infernokun.amaterasu.utils.AmaterasuConstants.buildSuccessResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/answer")
public class FlagAnswerController {
    private final FlagService flagService;
    private final AnsweredCTFEntityService answeredCTFEntityService;
    private final UserService userService;

    @PostMapping()
    public ResponseEntity<ApiResponse<AnsweredCTFEntityResponse>> answerQuestion(@RequestBody FlagAnswerRequest flagAnswerRequest) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (flagAnswerRequest == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        User user = userService.findUserById(flagAnswerRequest.getUserId());

        if (!user.getId().equals(authentication.getName())) {
            // If usernames don't match, return unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean isAnswerCorrect = this.flagService.validateFlag(flagAnswerRequest);

        return buildSuccessResponse("Got some answer", flagService
                .addAnsweredCTFEntity(authentication.getName(), flagAnswerRequest, isAnswerCorrect), HttpStatus.OK);
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse<AnsweredCTFEntity>> checkChallengeStatus(@RequestParam String ctfEntityId) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        User user = this.userService.findUserById(authentication.getName());

        return buildSuccessResponse("Got some answer", answeredCTFEntityService
                .findByUserIdAndCtfEntityId(user.getId(), ctfEntityId), HttpStatus.OK);
    }
}

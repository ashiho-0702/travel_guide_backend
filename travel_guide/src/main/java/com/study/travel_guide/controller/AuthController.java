package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.dto.LoginRequest;
import com.study.travel_guide.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return Result.ok(authService.login(request.getCode(), request.getInviterId()));
    }
}

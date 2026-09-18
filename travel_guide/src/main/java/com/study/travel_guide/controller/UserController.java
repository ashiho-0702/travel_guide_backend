package com.study.travel_guide.controller;

import com.study.travel_guide.common.Result;
import com.study.travel_guide.dto.ProfileRequest;
import com.study.travel_guide.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/profile")
    public Result<Map<String, String>> updateProfile(@RequestAttribute("userId") Long userId,
                                                     @RequestBody ProfileRequest request) {
        return Result.ok(userService.updateProfile(userId, request.getNickname(), request.getAvatar()));
    }
}

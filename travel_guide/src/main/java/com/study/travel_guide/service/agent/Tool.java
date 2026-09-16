package com.study.travel_guide.service.agent;

import java.util.Map;

public interface Tool {
    String name();

    String description();

    Map<String, Object> parameters();

    String execute(Map<String, Object> args);
}

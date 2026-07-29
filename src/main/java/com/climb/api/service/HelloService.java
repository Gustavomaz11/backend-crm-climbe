package com.climb.api.service;

import org.springframework.stereotype.Service;

@Service
public class HelloService {

    public String greet() {
        return "Hello, Climbe!";
    }
}

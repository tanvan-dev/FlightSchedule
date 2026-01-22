package com.tanvan.ecommerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FlightBoardController {

    @GetMapping("/")
    public String flightBoard() {
        return "FlightBoard"; // không cần .html
    }
}


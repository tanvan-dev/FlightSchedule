package com.tanvan.ecommerce.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class FlightBoardController {

    @GetMapping("/")
    public String flightBoard() {
        return "FlightBoard"; // không cần .html
    }
}


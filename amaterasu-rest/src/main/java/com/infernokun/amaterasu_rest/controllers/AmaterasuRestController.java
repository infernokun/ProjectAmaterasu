package com.infernokun.amaterasu_rest.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping()
public class AmaterasuRestController {

    @GetMapping
    public String getDefaultPage() {
        return "Default Page Content";
    }
}

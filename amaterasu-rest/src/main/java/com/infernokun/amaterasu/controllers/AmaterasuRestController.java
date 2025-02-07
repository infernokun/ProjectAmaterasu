package com.infernokun.amaterasu.controllers;

import com.infernokun.amaterasu.controllers.base.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping()
public class AmaterasuRestController extends BaseController {

    @GetMapping
    public String getDefaultPage() {
        return "Default Page Content";
    }
}

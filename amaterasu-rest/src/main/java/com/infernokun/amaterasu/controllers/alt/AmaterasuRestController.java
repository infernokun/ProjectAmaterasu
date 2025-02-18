package com.infernokun.amaterasu.controllers.alt;

import com.infernokun.amaterasu.controllers.BaseController;
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

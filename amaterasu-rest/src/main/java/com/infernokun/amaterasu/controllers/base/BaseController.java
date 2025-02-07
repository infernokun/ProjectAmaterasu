package com.infernokun.amaterasu.controllers.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

@RestController
public abstract class BaseController {
    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
}

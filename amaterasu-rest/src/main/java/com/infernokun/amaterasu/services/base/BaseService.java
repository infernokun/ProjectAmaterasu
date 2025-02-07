package com.infernokun.amaterasu.services.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public abstract class BaseService {
    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
}

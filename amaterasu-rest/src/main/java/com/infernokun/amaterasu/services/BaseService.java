package com.infernokun.amaterasu.services;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Transactional
public abstract class BaseService {
    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    protected BaseService() {}
}

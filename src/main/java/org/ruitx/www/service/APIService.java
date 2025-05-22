package org.ruitx.www.service;

import org.ruitx.www.dto.api.PingResponse;

public class APIService {

    public APIService() {
    }

    public PingResponse ping() {
        return PingResponse.ok();
    }
} 
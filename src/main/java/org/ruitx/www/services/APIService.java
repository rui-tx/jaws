package org.ruitx.www.services;

import org.ruitx.www.models.responses.PingResponse;

public class APIService {
    
    public APIService() {
    }

    public PingResponse ping() {
        return PingResponse.ok();
    }
} 
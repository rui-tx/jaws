package org.ruitx.www.services;

import org.ruitx.jaws.types.PingResponse;

public class APIService {

    public APIService() {
    }

    public PingResponse ping() {
        return PingResponse.ok();
    }
} 
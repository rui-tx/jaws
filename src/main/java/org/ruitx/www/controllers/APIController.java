package org.ruitx.www.controllers;

import org.ruitx.jaws.interfaces.Route;
import org.ruitx.www.services.APIService;

import static org.ruitx.jaws.strings.ResponseCode.*;

import org.ruitx.jaws.components.Bragi;

public class APIController extends Bragi {

    private static final String API_ENDPOINT = "/api/v1/";
    private final APIService apiService;

    public APIController() {
        this.apiService = new APIService();
    }

    @Route(endpoint = API_ENDPOINT + "ping")
    public void ping() {
        sendSucessfulResponse(OK, apiService.ping());
    }
}
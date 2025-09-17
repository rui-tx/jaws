package org.ruitx.www.controller;

import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.JSON;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.www.service.APIService;

public class APIController extends Bragi {

  private static final String API_ENDPOINT = "/api/v1/";
  private final APIService apiService;

  public APIController() {
    this.apiService = new APIService();
  }

  @Route(endpoint = API_ENDPOINT + "ping", responseType = JSON)
  public void ping() {
    sendSuccess(OK, apiService.ping());
  }

}

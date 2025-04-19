package org.ruitx.www.examples;

import org.ruitx.jaws.components.BaseController;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.interfaces.IsolationLevel;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.interfaces.Transactional;
import org.ruitx.jaws.utils.Row;

import java.util.Date;
import java.util.List;

import static org.ruitx.jaws.strings.ResponseCode.*;

public class API extends BaseController {

    private static final String API_ENDPOINT = "/api/v1/";
    private Mimir db;

    public API() {
        this.db = new Mimir();
    }

    @Route(endpoint = API_ENDPOINT + "ping")
    public void ping() {
        sendJSONResponse(OK, "pong");
    }

    @Transactional
    @Route(endpoint = API_ENDPOINT + "increment")
    public void increment() {
        Date timestamp = new Date();
        int affectedRows = db.executeSql("INSERT INTO STRESS_TEST (created_at) VALUES (?)", timestamp);
        if (affectedRows == 0) {
            sendJSONResponse(INTERNAL_SERVER_ERROR, "Failed to insert into STRESS_TEST");
            return;
        }
        
        sendJSONResponse(OK, null);
    }

    @Route(endpoint = API_ENDPOINT + "current")
    public void getAllStressTest() {
        List<Row> rows = db.getRows("SELECT * FROM STRESS_TEST;");
        sendJSONResponse(OK, rows);
    }
}

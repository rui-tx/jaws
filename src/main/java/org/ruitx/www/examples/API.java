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

    //@Transactional(readOnly = false)
    @Route(endpoint = API_ENDPOINT + "increment")
    public void increment() {
        Date timestamp = new Date();
        int affectedRows = db.executeSql("INSERT INTO STRESS_TEST (created_at) VALUES (?)", timestamp);
        int affectedRowss = db.executeSql("INSERT INTO STRESS_TEST2 (created_at) VALUES (?)", timestamp);
        
        if (affectedRows == 0) {
            throw new RuntimeException("Failed to insert into STRESS_TEST");
        }
        
        sendJSONResponse(OK, null);
    }

    @Transactional(readOnly = true, isolation = IsolationLevel.READ_UNCOMMITTED)
    @Route(endpoint = API_ENDPOINT + "current")
    public void getAllStressTest() {
        List<Row> rows = db.getRows("SELECT * FROM STRESS_TEST;");
        sendJSONResponse(OK, rows);
    }
}

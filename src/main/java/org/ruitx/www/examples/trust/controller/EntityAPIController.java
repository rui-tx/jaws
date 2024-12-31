package org.ruitx.www.examples.trust.controller;

import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.utils.APIHandler;
import org.ruitx.jaws.utils.APIResponse;
import org.ruitx.www.examples.trust.dto.entity.EntityNameAndDateDTO;
import org.ruitx.www.examples.trust.model.Entity;
import org.ruitx.www.examples.trust.service.EntityService;

import java.io.IOException;
import java.util.*;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.JSON;

public class EntityAPIController {

    private static final String VERSION = "v1";
    public static final String URL = "api/" + VERSION;
    private static final String API = "/api/" + VERSION;

    public EntityAPIController() {
    }

    @Route(endpoint = API + "/entity", method = GET, responseType = JSON)
    public void getAllEntities(Yggdrasill.RequestHandler rh) throws IOException {
        Map<String, String> queryParams = validateParams(rh.getQueryParams(), "id", "name", "birth_date");
        EntityService entityService = new EntityService();
        List<Entity> entities = entityService.getEntity(queryParams);

        rh.sendJSONResponse(OK, APIHandler.encode(
                new APIResponse<>(
                        true,
                        entities,
                        null))
        );
    }

    @Route(endpoint = API + "/entity/question2", method = GET, responseType = JSON)
    public void getQuestion2(Yggdrasill.RequestHandler rh) throws IOException {
        EntityService entityService = new EntityService();
        List<EntityNameAndDateDTO> entities = entityService.getQuestion2();

        rh.sendJSONResponse(OK, APIHandler.encode(
                new APIResponse<>(
                        true,
                        entities,
                        null))
        );
    }

    private Map<String, String> validateParams(Map<String, String> map, String... keysToKeep) {
        if (map == null) {
            return new HashMap<>();
        }

        Set<String> keysSet = new HashSet<>(Arrays.asList(keysToKeep));
        Map<String, String> result = new HashMap<>();

        map.forEach((key, value) -> {
            if (keysSet.contains(key)) {
                result.put(key, value);
            }
        });

        return result;
    }
}

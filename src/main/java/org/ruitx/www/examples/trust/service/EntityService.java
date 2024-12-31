package org.ruitx.www.examples.trust.service;

import org.ruitx.jaws.utils.Row;
import org.ruitx.www.examples.trust.dto.entity.EntityNameAndDateDTO;
import org.ruitx.www.examples.trust.model.Entity;
import org.ruitx.www.examples.trust.repository.EntityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EntityService {
    public List<Entity> getEntity(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return getAllEntities();
        }

        if (queryParams.containsKey("id")) {
            List<Entity> entity = new ArrayList<>();
            entity.add(getEntityById(queryParams.get("id")));
            return entity;
        }

        if (queryParams.containsKey("name")) {
            return getEntityByName(queryParams.get("name"));
        }

        if (queryParams.containsKey("birth_date")) {
            return getEntityByBirthDate(queryParams.get("birth_date"));
        }

        // this is never reached
        return null;
    }

    public Entity getEntityById(String id) {
        EntityRepository entityRepository = new EntityRepository();
        Row row = entityRepository.getEntityById(id);

        return row != null ?
                new Entity(
                        row.getInt("id"),
                        row.getString("name"),
                        row.getString("birth_date"),
                        row.getInt("type_id"),
                        row.getInt("insurance_id"))

                : null;
    }

    public List<Entity> getEntityByName(String name) {
        EntityRepository entityRepository = new EntityRepository();
        List<Row> rows = entityRepository.getEntityByName(name);

        return rows != null ?
                rows.stream().map(row ->
                                new Entity(
                                        row.getInt("id"),
                                        row.getString("name"),
                                        row.getString("birth_date"),
                                        row.getInt("type_id"),
                                        row.getInt("insurance_id")))
                        .toList()
                : null;
    }

    public List<Entity> getEntityByBirthDate(String name) {
        EntityRepository entityRepository = new EntityRepository();
        List<Row> rows = entityRepository.getEntityByBirthDate(name);

        return rows != null ?
                rows.stream().map(row ->
                                new Entity(
                                        row.getInt("id"),
                                        row.getString("name"),
                                        row.getString("birth_date"),
                                        row.getInt("type_id"),
                                        row.getInt("insurance_id")))
                        .toList()
                : null;
    }


    private List<Entity> getAllEntities() {
        EntityRepository entityRepository = new EntityRepository();
        List<Row> rows = entityRepository.getAllEntities();

        return rows != null ?
                rows.stream()
                        .map(row ->
                                new Entity(
                                        row.getInt("id"),
                                        row.getString("name"),
                                        row.getString("birth_date"),
                                        row.getInt("type_id"),
                                        row.getInt("insurance_id")))
                        .toList()
                : null;
    }

    public List<EntityNameAndDateDTO> getQuestion2() {
        EntityRepository entityRepository = new EntityRepository();
        List<Row> rows = entityRepository.getQuestion2();

        return rows != null ?
                rows.stream()
                        .map(row ->
                                new EntityNameAndDateDTO(
                                        row.getString("name"),
                                        row.getString("birth_date")))
                        .toList()
                : null;
    }
}

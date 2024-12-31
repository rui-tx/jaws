package org.ruitx.www.examples.trust.repository;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.utils.Row;

import java.util.List;

public class EntityRepository {
    public List<Row> getAllEntities() {
        Mimir db = new Mimir();
        return db.getRows("SELECT * FROM ENTITY");
    }

    public List<Row> getQuestion2() {
        Mimir db = new Mimir();
        String sql = """
                SELECT Entity.name, Entity.birth_date
                FROM Entity
                INNER JOIN Insurance ON Entity.insurance_id = Insurance.id
                INNER JOIN Involved ON Involved.entity_id = Entity.id
                INNER JOIN Incident ON Involved.incident_id = Incident.id
                INNER JOIN IncidentType ON Incident.incident_type_id = IncidentType.id
                WHERE (strftime('%Y', 'now') - strftime('%Y', Entity.birth_date)) < 40
                AND (UPPER(Insurance.company_name) = 'ZURICH' OR UPPER(Insurance.company_name) = 'AGEAS')
                AND UPPER(IncidentType.type) = 'ROAD ACCIDENT'
                AND Entity.type_id NOT IN (1, 2)
                ORDER BY Entity.birth_date DESC;
                """;
        return db.getRows(sql);
    }

    public Row getEntityById(String id) {
        Mimir db = new Mimir();
        return db.getRow("SELECT * FROM ENTITY WHERE id = ?", id);
    }

    public List<Row> getEntityByName(String name) {
        Mimir db = new Mimir();
        return db.getRows("SELECT * FROM ENTITY WHERE name = ?", name);
    }

    public List<Row> getEntityByBirthDate(String birthDate) {
        Mimir db = new Mimir();
        return db.getRows("SELECT * FROM ENTITY WHERE birth_date = ?", birthDate);
    }
}

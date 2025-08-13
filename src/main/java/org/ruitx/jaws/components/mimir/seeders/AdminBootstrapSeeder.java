package org.ruitx.jaws.components.mimir.seeders;

import java.time.Instant;
import java.util.Optional;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.mimir.DatabaseSeeder;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.JawsUtils;
import org.tinylog.Logger;

/**
 * Ensures there is an 'admin' role and an 'admin' user, and assigns the role. Idempotent: checks
 * existence before creating. Uses TinyLog only (pre-JawsLogger bootstrap).
 */
public class AdminBootstrapSeeder implements DatabaseSeeder {

  @Override
  public String name() {
    return "AdminBootstrapSeeder";
  }

  @Override
  public void run(Mimir db) throws Exception {
    // Ensure ROLE 'admin'
    Optional<Row> roleRow = db.getRow("SELECT id FROM ROLE WHERE name = ?", "admin");
    Integer roleId;
    if (roleRow.isEmpty()) {
      int created = db.execute(
          "INSERT INTO ROLE (name, description, created_at) VALUES (?, ?, ?)",
          "admin", "Administrator role", Instant.now().getEpochSecond());
      if (created <= 0) {
        throw new IllegalStateException("Failed to create 'admin' role");
      }
      roleId = db.getRow("SELECT id FROM ROLE WHERE name = ?", "admin")
          .flatMap(r -> r.getInt("id")).orElseThrow();
      Logger.info("Seeder: Created 'admin' role (id={})", roleId);
    } else {
      roleId = roleRow.get().getInt("id").orElseThrow();
    }

    // Ensure USER 'admin'
    Optional<Row> userRow = db.getRow("SELECT id FROM USER WHERE user = ?", "admin");
    Integer userId;
    if (userRow.isEmpty()) {
      String password = JawsUtils.newPassword().orElse("Lee7Pa$$w00rd");
      String hash = JawsUtils.hashPassword(password);
      int created = db.execute(
          "INSERT INTO USER (user, password_hash, first_name, last_name, created_at) VALUES (?, ?, ?, ?, ?)",
          "admin", hash, "Admin", "User", Instant.now().getEpochSecond());
      if (created <= 0) {
        throw new IllegalStateException("Failed to create 'admin' user");
      }
      userId = db.getRow("SELECT id FROM USER WHERE user = ?", "admin")
          .flatMap(r -> r.getInt("id")).orElseThrow();
      Logger.warn("Seeder: Created default 'admin' user with password: {}", password);
      Logger.warn("Seeder: Please change this password immediately after first login.");
    } else {
      userId = userRow.get().getInt("id").orElseThrow();
    }

    // Ensure assignment USER_ROLE
    Optional<Row> link = db.getRow(
        "SELECT id FROM USER_ROLE WHERE user_id = ? AND role_id = ?",
        userId, roleId);
    if (link.isEmpty()) {
      int created = db.execute(
          "INSERT INTO USER_ROLE (user_id, role_id, assigned_at, assigned_by) VALUES (?, ?, ?, ?)",
          userId, roleId, Instant.now().getEpochSecond(), userId);
      if (created <= 0) {
        throw new IllegalStateException("Failed to assign 'admin' role to 'admin' user");
      }
      Logger.info("Seeder: Assigned 'admin' role to user 'admin'");
    }
  }
}

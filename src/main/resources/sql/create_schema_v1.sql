-- examples database schema
DROP TABLE IF EXISTS TODO;
DROP TABLE IF EXISTS USER;
DROP TABLE IF EXISTS GALLERY;

CREATE TABLE IF NOT EXISTS USER (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS TODO (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    todo TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER,
    FOREIGN KEY (user_id) REFERENCES USER(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS GALLERY (
    id TEXT PRIMARY KEY,
    url TEXT NOT NULL,
    description TEXT NOT NULL,
    author_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO GALLERY (id, url, description, author_id) VALUES
('8hj2ok', 'https://images.unsplash.com/photo-1731432245362-26f9c0f1ba2f?q=80&w=1471&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D', 'Malin Head, County Donegal, Irland', 1),
('sipm5t', 'https://images.unsplash.com/photo-1734597949864-0ee6637b0c3f?q=80&w=1567&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D', 'New York, NY, USA', 1),
('jqzvtx', 'https://images.unsplash.com/photo-1726333629906-9a52575d4b78?q=80&w=1471&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D', 'Stunning view of Mt. Baker at sunset from the top of Sauk Mountain in Washington, I really love the cloud inversion between the two evergreen-topped mountain ridges.',2),
('m9gata', 'https://images.unsplash.com/photo-1668365187350-05c997d09eba?q=80&w=1470&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D', 'Very early on the morning looking over the sea stacks at Ribeira da Janela on the Island of Madeira.',3);

DROP TABLE IF EXISTS Entity;
DROP TABLE IF EXISTS EntityType;
DROP TABLE IF EXISTS Insurance;
DROP TABLE IF EXISTS Incident;
DROP TABLE IF EXISTS IncidentType;
DROP TABLE IF EXISTS Involved;
DROP TABLE IF EXISTS Report;
DROP TABLE IF EXISTS ReportType;
DROP TABLE IF EXISTS Upload;

CREATE TABLE IF NOT EXISTS Insurance (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    insurance_policy_id TEXT NOT NULL,
    company_name TEXT NOT NULL,
    agent_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS EntityType (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS Entity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    birth_date DATE,
    type_id INTEGER,
    insurance_id INTEGER,
    FOREIGN KEY (type_id) REFERENCES EntityType(id) ON DELETE CASCADE,
    FOREIGN KEY (insurance_id) REFERENCES Insurance(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS IncidentType (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS Incident (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed BOOLEAN DEFAULT FALSE,
    incident_type_id INTEGER,
    FOREIGN KEY (incident_type_id) REFERENCES IncidentType(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Involved (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_id INTEGER NOT NULL,
    incident_id INTEGER NOT NULL,
    FOREIGN KEY (entity_id) REFERENCES Entity(id) ON DELETE CASCADE,
    FOREIGN KEY (incident_id) REFERENCES Incident(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ReportType (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS Report (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    description TEXT NOT NULL,
    report_type_id INTEGER,
    incident_id INTEGER,
    FOREIGN KEY (report_type_id) REFERENCES ReportType(id) ON DELETE CASCADE,
    FOREIGN KEY (incident_id) REFERENCES Incident(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS Upload (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    url TEXT NOT NULL,
    report_id INTEGER,
    FOREIGN KEY (report_id) REFERENCES Report(id) ON DELETE CASCADE
);

-- An LLM (chatgpt) made this dummy data
-- Inserting data into the EntityType table
INSERT INTO EntityType (type) VALUES
('Internal'),
('Vehicle'),
('Policyholder'),
('Insured'),
('Claimant'),
('Driver'),
('Passenger'),
('Third Party'),
('Witness'),
('Victim');

-- Inserting data into the Insurance table
INSERT INTO Insurance (insurance_policy_id, company_name, agent_name) VALUES
('INS123456789', 'Zurich', 'John Doe'),
('INS987654321', 'Ageas', 'Jane Smith'),
('INS112233445', 'Allianz', 'Michael Johnson'),
('INS556677889', 'AXA', 'Emily Davis'),
('INS998877665', 'Generali', 'James Wilson'),
('INS223344556', 'Liberty Mutual', 'Sarah Lee'),
('INS443322110', 'State Farm', 'David Brown'),
('INS667788990', 'Progressive', 'Linda Taylor'),
('INS778899667', 'Zurich', 'John Doe'),
('INS990011223', 'Ageas', 'Jane Smith'),
('INS333444555', 'Allianz', 'Michael Johnson'),
('INS555666777', 'AXA', 'Emily Davis'),
('INS888999000', 'Generali', 'James Wilson'),
('INS222333444', 'Liberty Mutual', 'Sarah Lee'),
('INS777888999', 'State Farm', 'David Brown'),
('INS999000111', 'Progressive', 'Linda Taylor'),
('INS112233667', 'Zurich', 'John Doe'),
('INS334455889', 'Ageas', 'Jane Smith'),
('INS445566778', 'Allianz', 'Michael Johnson'),
('INS667788223', 'AXA', 'Emily Davis'),
('INS999000555', 'Generali', 'James Wilson'),
('INS223344778', 'Liberty Mutual', 'Sarah Lee'),
('INS556677223', 'State Farm', 'David Brown'),
('INS778899112', 'Progressive', 'Linda Taylor');

-- Inserting data into the IncidentType table
INSERT INTO IncidentType (type) VALUES
('Road Accident'),
('Theft'),
('Vandalism'),
('Fire'),
('Flood'),
('Burglary'),
('Collision'),
('Natural Disaster'),
('Workplace Accident'),
('Medical Emergency'),
('Property Damage'),
('Hit and Run'),
('Lost Luggage'),
('Cyber Attack'),
('Personal Injury');

-- Inserting data into the ReportType table
INSERT INTO ReportType (type) VALUES
('Damage Report'),
('Theft Report'),
('Accident Report');

-- Inserting data into the Entity table
INSERT INTO Entity (name, birth_date, type_id, insurance_id) VALUES
('John Doe', '1985-07-20', 1, 1),
('Vehicle A', '2020-01-15', 2, 2),
('Jane Smith', '1990-03-10', 3, 2),
('Alice Johnson', '1987-05-05', 4, 3),
('Vehicle B', '2021-07-25', 2, 4),
('Robert Brown', '1992-11-11', 5, 4),
('Vehicle C', '2022-09-01', 2, 5),
('Emily Davis', '1989-03-15', 6, 5),
('Vehicle D', '2020-08-18', 2, 6),
('Michael Wilson', '1984-04-25', 7, 6),
('Vehicle E', '2021-02-20', 2, 7),
('Sarah Lee', '1991-12-30', 8, 7),
('Vehicle F', '2022-01-10', 2, 8),
('David Brown', '1986-06-14', 9, 8),
('Vehicle G', '2020-05-05', 2, 9),
('Linda Taylor', '1983-09-09', 10, 9),
('Vehicle H', '2021-11-22', 2, 10),
('James Wilson', '1993-01-01', 5, 10),
('Vehicle I', '2022-03-03', 2, 11),
('Sarah Lee', '1990-08-13', 6, 11),
('Vehicle J', '2021-06-06', 2, 12),
('David Brown', '1985-02-14', 7, 12),
('Vehicle K', '2022-04-17', 2, 13),
('Linda Taylor', '1987-07-21', 8, 13),
('Vehicle L', '2021-10-10', 2, 14),
('John Doe', '1985-07-20', 9, 14);

-- Inserting data into the Incident table
INSERT INTO Incident (created_at, updated_at, completed_at, completed, incident_type_id) VALUES
('2024-12-19 10:00:00', '2024-12-19 10:05:00', '2024-12-19 10:30:00', TRUE, 1),
('2024-12-19 11:00:00', '2024-12-19 11:05:00', NULL, FALSE, 2),
('2024-12-20 12:00:00', '2024-12-20 12:10:00', '2024-12-20 12:45:00', TRUE, 3),
('2024-12-21 14:00:00', '2024-12-21 14:05:00', NULL, FALSE, 1),
('2024-12-22 15:30:00', '2024-12-22 15:35:00', '2024-12-22 16:00:00', TRUE, 4),
('2024-12-23 17:20:00', '2024-12-23 17:25:00', NULL, FALSE, 5),
('2024-12-24 18:00:00', '2024-12-24 18:05:00', '2024-12-24 18:30:00', TRUE, 1),
('2024-12-25 09:00:00', '2024-12-25 09:10:00', NULL, FALSE, 2),
('2024-12-26 13:45:00', '2024-12-26 13:50:00', NULL, FALSE, 6),
('2024-12-27 16:00:00', '2024-12-27 16:05:00', '2024-12-27 16:30:00', TRUE, 3),
('2024-12-28 19:00:00', '2024-12-28 19:05:00', '2024-12-28 19:30:00', TRUE, 4),
('2024-12-29 20:00:00', '2024-12-29 20:05:00', NULL, FALSE, 5),
('2024-12-30 21:30:00', '2024-12-30 21:35:00', '2024-12-30 22:00:00', TRUE, 1),
('2024-12-31 07:45:00', '2024-12-31 07:50:00', '2024-12-31 08:15:00', TRUE, 1),
('2025-01-01 08:30:00', '2025-01-01 08:35:00', NULL, FALSE, 1),
('2025-01-02 09:00:00', '2025-01-02 09:05:00', '2025-01-02 09:30:00', TRUE, 1),
('2025-01-03 11:00:00', '2025-01-03 11:05:00', NULL, FALSE, 1),
('2025-01-04 12:30:00', '2025-01-04 12:35:00', '2025-01-04 13:00:00', TRUE, 1),
('2025-01-05 14:00:00', '2025-01-05 14:05:00', '2025-01-05 14:30:00', TRUE, 1),
('2025-01-06 16:00:00', '2025-01-06 16:05:00', NULL, FALSE, 1);


-- Inserting data into the Involved table
INSERT INTO Involved (entity_id, incident_id) VALUES
(1, 1),
(2, 1),
(3, 2),
(4, 3),
(5, 4),
(6, 5),
(7, 6),
(8, 7),
(9, 8),
(10, 9),
(11, 10),
(12, 11),
(13, 12),
(14, 13),
(15, 14),
(16, 15),
(17, 16),
(18, 17),
(19, 18),
(20, 19),
(21, 20);

-- Inserting data into the Report table
INSERT INTO Report (created_at, updated_at, description, report_type_id, incident_id) VALUES
('2024-12-19 12:00:00', '2024-12-19 12:05:00', 'Road Accident Report for John Doe and Vehicle A', 3, 1),
('2024-12-19 12:30:00', '2024-12-19 12:35:00', 'Theft Report for Jane Smith', 2, 2),
('2024-12-19 14:00:00', '2024-12-19 14:05:00', 'Vandalism Report for Vehicle B', 1, 3),
('2024-12-19 14:30:00', '2024-12-19 14:35:00', 'Hit-and-run Report for Vehicle C', 3, 4),
('2024-12-19 15:00:00', '2024-12-19 15:05:00', 'Fire Incident Report for Vehicle D', 1, 5),
('2024-12-19 15:30:00', '2024-12-19 15:35:00', 'Flood Incident Report for Vehicle E', 2, 6),
('2024-12-19 16:00:00', '2024-12-19 16:05:00', 'Road Accident Report for Michael Johnson and Vehicle F', 3, 7),
('2024-12-19 16:30:00', '2024-12-19 16:35:00', 'Theft Report for Sarah Lee', 2, 8),
('2024-12-19 17:00:00', '2024-12-19 17:05:00', 'Vandalism Report for Vehicle G', 1, 9),
('2024-12-19 17:30:00', '2024-12-19 17:35:00', 'Hit-and-run Report for Vehicle H', 3, 10);

-- Inserting data into the Upload table
INSERT INTO Upload (created_at, updated_at, url, report_id) VALUES
('2024-12-19 13:00:00', '2024-12-19 13:05:00', 'http://example.com/uploads/report1.jpg', 1),
('2024-12-19 13:10:00', '2024-12-19 13:15:00', 'http://example.com/uploads/report2.jpg', 2),
('2024-12-19 14:10:00', '2024-12-19 14:15:00', 'http://example.com/uploads/report3.jpg', 3),
('2024-12-19 14:40:00', '2024-12-19 14:45:00', 'http://example.com/uploads/report4.jpg', 4),
('2024-12-19 15:10:00', '2024-12-19 15:15:00', 'http://example.com/uploads/report5.jpg', 5),
('2024-12-19 15:40:00', '2024-12-19 15:45:00', 'http://example.com/uploads/report6.jpg', 6),
('2024-12-19 16:10:00', '2024-12-19 16:15:00', 'http://example.com/uploads/report7.jpg', 7),
('2024-12-19 16:40:00', '2024-12-19 16:45:00', 'http://example.com/uploads/report8.jpg', 8),
('2024-12-19 17:10:00', '2024-12-19 17:15:00', 'http://example.com/uploads/report9.jpg', 9),
('2024-12-19 17:40:00', '2024-12-19 17:45:00', 'http://example.com/uploads/report10.jpg', 10);

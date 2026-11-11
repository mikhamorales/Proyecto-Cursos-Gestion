-- =====================================================================
-- Base de datos de la institución - Proyecto Organización de cursos
-- =====================================================================

DROP DATABASE IF EXISTS institucion;
CREATE DATABASE institucion;

\connect institucion

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- para hashear contraseñas de empresas (bcrypt)

-- ---------------------------------------------------------------------
-- 1. EMPRESAS solicitantes
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS empresa  (
    id                  SERIAL PRIMARY KEY,
    razon_social        VARCHAR(150) NOT NULL,
    cuit                VARCHAR(20)  NOT NULL UNIQUE,
    direccion           VARCHAR(200),
    contacto_nombre     VARCHAR(120) NOT NULL,
    contacto_email      VARCHAR(120) NOT NULL,
    contacto_telefono   VARCHAR(40),
    fecha_alta          TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 2. CUENTA DE ACCESO de la empresa (actividad "Habilitar acceso de la empresa")
--    Una cuenta por empresa; se reutiliza si contrata otro curso.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cuenta_empresa  (
    id              SERIAL PRIMARY KEY,
    empresa_id      INTEGER NOT NULL UNIQUE REFERENCES empresa(id) ON DELETE CASCADE,
    usuario         VARCHAR(60) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    fecha_creacion  TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 3. CURSOS (cada solicitud = un curso = una instancia de proceso en Flowable)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS curso  (
    id                       SERIAL PRIMARY KEY,
    empresa_id               INTEGER NOT NULL REFERENCES empresa(id),
    process_instance_id      VARCHAR(64) UNIQUE,      -- ID de la instancia en Flowable
    nombre                   VARCHAR(150) NOT NULL,   -- temática solicitada
    descripcion              TEXT,
    cant_participantes_est   INTEGER CHECK (cant_participantes_est > 0),
    fechas_preferidas        TEXT,                    -- descripción libre del formulario
    programa                 TEXT,                    -- de la propuesta vigente/aceptada
    presupuesto              NUMERIC(12,2) CHECK (presupuesto >= 0),
    fecha_inicio             TIMESTAMP,               -- fecha acordada (variable fechaCurso)
    fecha_fin                TIMESTAMP,
    informe_resultados       TEXT,                    -- texto o ruta al PDF del informe
    estado                   VARCHAR(30) NOT NULL DEFAULT 'SOLICITADO'
        CHECK (estado IN (
            -- estados en curso 
            'SOLICITADO', 'EN_ACUERDO', 'EN_PREPARACION', 'CONFIRMADO',
            'EN_DESARROLLO', 'EN_INFORME',
            -- estados finales              
            'FINALIZADO', 'CANCELADO', 'RECHAZADO',
            'CERRADO_POR_FALTA_DE_RESPUESTA', 'NO_VIABLE')),
    fecha_solicitud          TIMESTAMP NOT NULL DEFAULT now(),
    fecha_cierre             TIMESTAMP
);

CREATE INDEX idx_curso_empresa ON curso(empresa_id);
CREATE INDEX idx_curso_estado  ON curso(estado);

-- ---------------------------------------------------------------------
-- 4. PROPUESTAS 
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS propuesta (
    id                SERIAL PRIMARY KEY,
    curso_id          INTEGER NOT NULL REFERENCES curso(id) ON DELETE CASCADE,
    version_          INTEGER NOT NULL CHECK (version_ >= 1),
    programa          TEXT NOT NULL,
    presupuesto       NUMERIC(12,2) NOT NULL CHECK (presupuesto >= 0),
    fechas_posibles   TEXT NOT NULL,
    observaciones     TEXT,                  -- observaciones de la empresa que motivaron la versión
    pdf_ruta          VARCHAR(300),          -- ruta del PDF generado
    fecha_creacion    TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (curso_id, version_)
);

-- ---------------------------------------------------------------------
-- Datos de prueba
-- ---------------------------------------------------------------------
INSERT INTO empresa (razon_social, cuit, direccion, contacto_nombre, contacto_email, contacto_telefono) VALUES
 ('Acme S.A.',            '30-11111111-1', 'Av. Sabattini 100, Río Cuarto',  'Laura Pérez',   'laura@acme.com',     '358-4111111'),
 ('Agroindustrias del Sur','30-22222222-2', 'Ruta 8 km 603, Río Cuarto',      'Martín Gómez',  'martin@agrosur.com', '358-4222222'),
 ('TecnoSoft SRL',        '30-33333333-3', 'Belgrano 450, Córdoba',          'Ana Rodríguez', 'ana@tecnosoft.com',  '351-4333333');

-- Contraseña de prueba de todas las cuentas: empresa123
INSERT INTO cuenta_empresa (empresa_id, usuario, password_hash) VALUES
 (1, 'acme',      crypt('empresa123', gen_salt('bf'))),
 (2, 'agrosur',   crypt('empresa123', gen_salt('bf'))),
 (3, 'tecnosoft', crypt('empresa123', gen_salt('bf')));

INSERT INTO curso (empresa_id, process_instance_id, nombre, descripcion, cant_participantes_est,
                   fechas_preferidas, programa, presupuesto, fecha_inicio, estado) VALUES
 (1, NULL, 'Excel avanzado', 'Capacitación en tablas dinámicas y macros', 15,
     'Primera quincena de diciembre', NULL, NULL, NULL, 'SOLICITADO'),
 (1, NULL, 'Seguridad e higiene laboral', 'Normativa y simulacros', 30,
     'Marzo 2027', 'Módulo 1: Normativa; Módulo 2: Simulacros', 450000.00,
     '2027-03-10 09:00', 'EN_PREPARACION'),
 (2, NULL, 'Gestión de calidad ISO 9001', 'Introducción a la norma ISO 9001', 20,
     'Cualquier día de la semana', 'Introducción, auditoría interna, mejora continua', 380000.00,
     NULL, 'RECHAZADO'),
 (3, NULL, 'Introducción a Python', 'Programación para no programadores', 12,
     'Octubre 2026', 'Sintaxis, estructuras de datos, scripts', 250000.00,
     '2026-10-20 14:00', 'FINALIZADO');

INSERT INTO propuesta (curso_id, version_, programa, presupuesto, fechas_posibles, observaciones) VALUES
 (2, 1, 'Módulo 1: Normativa; Módulo 2: Simulacros', 500000.00, '10/03/2027; 17/03/2027', NULL),
 (2, 2, 'Módulo 1: Normativa; Módulo 2: Simulacros', 450000.00, '10/03/2027; 17/03/2027', 'Solicitan reducir el presupuesto'),
 (3, 1, 'Introducción, auditoría interna, mejora continua', 380000.00, '05/11/2026', NULL),
 (4, 1, 'Sintaxis, estructuras de datos, scripts', 250000.00, '20/10/2026', NULL);

-- Vista útil para la consulta de cursos de una empresa (sitio web)
CREATE VIEW v_cursos_empresa AS
SELECT c.id, c.nombre, c.estado, c.fecha_inicio, c.process_instance_id,
       e.id AS empresa_id, ce.usuario
FROM curso c
JOIN empresa e ON e.id = c.empresa_id
       LEFT JOIN cuenta_empresa ce ON ce.empresa_id = e.id;


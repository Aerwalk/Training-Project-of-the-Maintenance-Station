

-- 1. Роли пользователей
CREATE TABLE roles (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
);

INSERT INTO roles (name, description) VALUES
    ('admin',   'Администратор системы'),
    ('manager', 'Мастер-приёмщик'),
    ('master',  'Автомобильный мастер'),
    ('client',  'Клиент СТО');

-- 2. Пользователи системы
CREATE TABLE users (
    id            SERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role_id       INTEGER NOT NULL REFERENCES roles(id),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Клиенты
CREATE TABLE clients (
    id         SERIAL PRIMARY KEY,
    full_name  VARCHAR(150) NOT NULL,
    phone      VARCHAR(20)  NOT NULL UNIQUE,
    email      VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Автомобили клиентов
CREATE TABLE cars (
    id        SERIAL PRIMARY KEY,
    client_id INTEGER NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    brand     VARCHAR(50)  NOT NULL,
    model     VARCHAR(50)  NOT NULL,
    year      INTEGER CHECK (year BETWEEN 1950 AND 2100),
    plate_num VARCHAR(15)  NOT NULL UNIQUE,
    vin       VARCHAR(17)
);

-- 5. Мастера
CREATE TABLE masters (
    id             SERIAL PRIMARY KEY,
    user_id        INTEGER NOT NULL REFERENCES users(id),
    specialization VARCHAR(100),
    is_active      BOOLEAN DEFAULT TRUE
);

-- 6. Услуги
CREATE TABLE services (
    id           SERIAL PRIMARY KEY,
    name         VARCHAR(150) NOT NULL,
    price        NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    duration_min INTEGER CHECK (duration_min > 0)
);

-- 7. Статусы заявок
CREATE TABLE order_statuses (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO order_statuses (name) VALUES
    ('новая'), ('в работе'), ('завершена'), ('отменена');

-- 8. Заявки
CREATE TABLE orders (
    id          SERIAL PRIMARY KEY,
    client_id   INTEGER NOT NULL REFERENCES clients(id),
    car_id      INTEGER NOT NULL REFERENCES cars(id),
    master_id   INTEGER REFERENCES masters(id),
    status_id   INTEGER NOT NULL REFERENCES order_statuses(id) DEFAULT 1,
    description TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    closed_at   TIMESTAMP
);

-- 9. Услуги в заявке
CREATE TABLE order_services (
    id         SERIAL PRIMARY KEY,
    order_id   INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    service_id INTEGER NOT NULL REFERENCES services(id),
    quantity   INTEGER DEFAULT 1 CHECK (quantity > 0),
    price_at   NUMERIC(10,2) NOT NULL
);

-- 10. Журнал изменений статусов (для триггера)
CREATE TABLE order_status_log (
    id         SERIAL PRIMARY KEY,
    order_id   INTEGER NOT NULL,
    old_status INTEGER,
    new_status INTEGER,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    changed_by TEXT DEFAULT current_user
);

-- Индексы
CREATE INDEX idx_orders_client ON orders(client_id);
CREATE INDEX idx_orders_master ON orders(master_id);
CREATE INDEX idx_orders_status ON orders(status_id);
CREATE INDEX idx_cars_client   ON cars(client_id);

-- Представление для расчёта стоимости заявки
CREATE VIEW v_order_total AS
SELECT
    o.id           AS order_id,
    c.full_name    AS client,
    cr.plate_num   AS car,
    os.name        AS status,
    COALESCE(SUM(osv.price_at * osv.quantity), 0) AS total_cost
FROM orders o
JOIN clients c         ON c.id = o.client_id
JOIN cars cr           ON cr.id = o.car_id
JOIN order_statuses os ON os.id = o.status_id
LEFT JOIN order_services osv ON osv.order_id = o.id
GROUP BY o.id, c.full_name, cr.plate_num, os.name;

-- Создание базовых ролей 
CREATE ROLE sto_admin   LOGIN PASSWORD 'admin_pass';
CREATE ROLE sto_manager LOGIN PASSWORD 'manager_pass';
CREATE ROLE sto_master  LOGIN PASSWORD 'master_pass';
CREATE ROLE sto_client  LOGIN PASSWORD 'client_pass';
-- Права администратора (полный доступ)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO sto_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO sto_admin;
-- Права мастера-приёмщика (работа с заявками и клиентами)
GRANT SELECT, INSERT, UPDATE ON clients        TO sto_manager;
GRANT SELECT, INSERT, UPDATE ON cars           TO sto_manager;
GRANT SELECT, INSERT, UPDATE ON orders         TO sto_manager;
GRANT SELECT, INSERT, UPDATE ON order_services TO sto_manager;
GRANT SELECT ON services                       TO sto_manager;
GRANT SELECT ON order_statuses                 TO sto_manager;
GRANT SELECT ON masters                        TO sto_manager;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sto_manager;

-- Права мастера (ограниченное обновление)
GRANT SELECT ON orders                 TO sto_master;
GRANT SELECT ON clients                TO sto_master;
GRANT SELECT ON cars                   TO sto_master;
GRANT SELECT ON services               TO sto_master;
GRANT SELECT, INSERT ON order_services TO sto_master;
GRANT UPDATE (status_id, closed_at) ON orders TO sto_master;

-- Права клиента (только просмотр через представление)
GRANT SELECT ON v_order_total TO sto_client;

-- 6.1. Создание заявки
CREATE OR REPLACE FUNCTION create_order(
    p_client_id   INTEGER,
    p_car_id      INTEGER,
    p_master_id   INTEGER,
    p_description TEXT
)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_order_id INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM clients WHERE id = p_client_id) THEN
        RAISE EXCEPTION 'Клиент с id=% не найден', p_client_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM cars WHERE id = p_car_id) THEN
        RAISE EXCEPTION 'Автомобиль с id=% не найден', p_car_id;
    END IF;

    INSERT INTO orders (client_id, car_id, master_id, status_id, description)
    VALUES (p_client_id, p_car_id, p_master_id, 1, p_description)
    RETURNING id INTO v_order_id;

    RETURN v_order_id;
END;
$$;

-- 6.2. Добавление услуги в заявку
CREATE OR REPLACE FUNCTION add_service_to_order(
    p_order_id   INTEGER,
    p_service_id INTEGER,
    p_quantity   INTEGER DEFAULT 1
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_price NUMERIC(10,2);
BEGIN
    SELECT price INTO v_price FROM services WHERE id = p_service_id;
    IF v_price IS NULL THEN
        RAISE EXCEPTION 'Услуга с id=% не найдена', p_service_id;
    END IF;

    INSERT INTO order_services (order_id, service_id, quantity, price_at)
    VALUES (p_order_id, p_service_id, p_quantity, v_price);
END;
$$;

-- 6.3. Закрытие заявки
CREATE OR REPLACE FUNCTION close_order(p_order_id INTEGER)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM orders WHERE id = p_order_id) THEN
        RAISE EXCEPTION 'Заявка с id=% не найдена', p_order_id;
    END IF;

    UPDATE orders
    SET status_id = (SELECT id FROM order_statuses WHERE name = 'завершена')
    WHERE id = p_order_id;
END;
$$;

-- 6.4. Расчёт стоимости заявки
CREATE OR REPLACE FUNCTION calculate_order_total(p_order_id INTEGER)
RETURNS NUMERIC
LANGUAGE plpgsql
AS $$
DECLARE
    v_total NUMERIC(10,2);
BEGIN
    SELECT COALESCE(SUM(price_at * quantity), 0)
    INTO v_total
    FROM order_services
    WHERE order_id = p_order_id;

    RETURN v_total;
END;
$$;

-- 6.5. Получение заявок клиента
CREATE OR REPLACE FUNCTION get_client_orders(p_client_id INTEGER)
RETURNS TABLE (
    order_id   INTEGER,
    car_plate  VARCHAR,
    status     VARCHAR,
    total_cost NUMERIC,
    created_at TIMESTAMP
)
LANGUAGE plpgsql
AS $$
BEGIN
    RETURN QUERY
    SELECT o.id,
           cr.plate_num,
           os.name::VARCHAR,
           COALESCE(SUM(osv.price_at * osv.quantity), 0),
           o.created_at
    FROM orders o
    JOIN cars cr           ON cr.id = o.car_id
    JOIN order_statuses os ON os.id = o.status_id
    LEFT JOIN order_services osv ON osv.order_id = o.id
    WHERE o.client_id = p_client_id
    GROUP BY o.id, cr.plate_num, os.name, o.created_at
    ORDER BY o.created_at DESC;
END;
$$;

-- Права на выполнение хранимых функций (выдаются после их создания)
GRANT EXECUTE ON FUNCTION create_order(INTEGER, INTEGER, INTEGER, TEXT) TO sto_manager;
GRANT EXECUTE ON FUNCTION add_service_to_order(INTEGER, INTEGER, INTEGER) TO sto_manager, sto_master;
GRANT EXECUTE ON FUNCTION close_order(INTEGER) TO sto_master, sto_manager;
GRANT EXECUTE ON FUNCTION calculate_order_total(INTEGER) TO sto_manager, sto_master, sto_client;
GRANT EXECUTE ON FUNCTION get_client_orders(INTEGER) TO sto_manager, sto_client;

-- 7.1. Триггер: автозаполнение closed_at
CREATE OR REPLACE FUNCTION trg_set_closed_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status_id = (SELECT id FROM order_statuses WHERE name = 'завершена')
       AND NEW.closed_at IS NULL THEN
        NEW.closed_at := CURRENT_TIMESTAMP;
    END IF;

    IF NEW.status_id <> (SELECT id FROM order_statuses WHERE name = 'завершена') THEN
        NEW.closed_at := NULL;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trigger_set_closed_at
BEFORE UPDATE OF status_id ON orders
FOR EACH ROW
EXECUTE FUNCTION trg_set_closed_at();


-- 7.2. Триггер: логирование изменений статуса
CREATE OR REPLACE FUNCTION trg_log_status_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status_id IS DISTINCT FROM OLD.status_id THEN
        INSERT INTO order_status_log (order_id, old_status, new_status)
        VALUES (NEW.id, OLD.status_id, NEW.status_id);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trigger_log_status_change
AFTER UPDATE OF status_id ON orders
FOR EACH ROW
EXECUTE FUNCTION trg_log_status_change();


-- 7.3. Триггер: запрет добавления услуг в завершённую заявку
CREATE OR REPLACE FUNCTION trg_check_order_status()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_status VARCHAR(50);
BEGIN
    SELECT os.name INTO v_status
    FROM orders o
    JOIN order_statuses os ON os.id = o.status_id
    WHERE o.id = NEW.order_id;

    IF v_status IN ('завершена', 'отменена') THEN
        RAISE EXCEPTION 'Нельзя добавить услугу в заявку со статусом «%»', v_status;
    END IF;
	
    RETURN NEW;
END;
$$;

CREATE TRIGGER trigger_check_order_status
BEFORE INSERT ON order_services
FOR EACH ROW
EXECUTE FUNCTION trg_check_order_status();

-- 1. Создаём группу
CREATE ROLE sto_readonly_group;

-- 2. Даём группе права только на чтение
GRANT SELECT ON services       TO sto_readonly_group;
GRANT SELECT ON order_statuses TO sto_readonly_group;
GRANT SELECT ON masters        TO sto_readonly_group;

-- 3. Добавляем роли в группу с наследованием
GRANT sto_readonly_group TO sto_manager WITH INHERIT TRUE;
GRANT sto_readonly_group TO sto_master  WITH INHERIT TRUE;

-- 4. Отзываем прямое право у manager, чтобы проверить наследование
REVOKE SELECT ON services FROM sto_manager;

-- отзыв права на обновление закрытых заявок
SELECT grantee, privilege_type, column_name
FROM information_schema.column_privileges
WHERE table_name = 'orders'
  AND grantee = 'sto_master';

  REVOKE UPDATE (closed_at) ON orders FROM sto_master;
  -- возвращение права на обновление закрытых заявок
GRANT UPDATE (closed_at) ON orders TO sto_master;

SELECT
    rolname        AS "Роль",
    rolinherit     AS "Наследование",
    rolcanlogin    AS "Может входить"
FROM pg_catalog.pg_roles
WHERE rolname NOT LIKE 'pg_%'
ORDER BY rolname;

SELECT
    r.rolname       AS "Роль",
    t.table_name    AS "Таблица",
    t.privilege_type AS "Право"
FROM pg_catalog.pg_roles r
JOIN information_schema.role_table_grants t
    ON t.grantee = r.rolname
WHERE r.rolname NOT LIKE 'pg_%'
ORDER BY r.rolname, t.table_name, t.privilege_type;

SELECT
    grantee         AS "Роль",
    table_name      AS "Таблица",
    column_name     AS "Столбец",
    privilege_type  AS "Право"
FROM information_schema.column_privileges
WHERE grantee LIKE 'sto_%'
ORDER BY grantee, table_name, column_name;

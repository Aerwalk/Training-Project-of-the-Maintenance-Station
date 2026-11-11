-- ===========================================================================
-- Схема базы данных учебного проекта СТО (PostgreSQL 16).
--
-- За основу взят файл finalbdsto.sql. Схема дополнена тем, что требуется
-- приложению:
--   * привязка учётной записи клиента к карточке клиента (users.client_id);
--   * представление v_my_orders для роли sto_client;
--   * учёт попыток входа и блокировка учётных записей;
--   * роли БД и разграничение прав на таблицы, представления и функции.
--
-- Демонстрационные данные находятся в docker/init/02_seed.sql.
-- ===========================================================================

-- Расширение для bcrypt-хеширования паролей (crypt/gen_salt).
-- Нужно до создания функций app_hash_password()/create_account(), поэтому
-- подключается здесь, а не только в seed-скрипте.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

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

-- Привязка учётной записи к карточке клиента.
-- Заполняется только для пользователей с ролью client: это позволяет
-- показывать им исключительно собственные заявки.
ALTER TABLE users
    ADD COLUMN client_id INTEGER REFERENCES clients(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX idx_users_client
    ON users(client_id)
    WHERE client_id IS NOT NULL;

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

-- 11. Учёт попыток входа и блокировка пользователей (защита от brute-force)
CREATE TABLE user_security (
    user_id         INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    blocked_until   TIMESTAMP,
    is_blocked      BOOLEAN NOT NULL DEFAULT FALSE,
    last_attempt_at TIMESTAMP
);

CREATE TABLE login_attempts (
    id           SERIAL PRIMARY KEY,
    username     VARCHAR(50) NOT NULL,
    success      BOOLEAN NOT NULL,
    ip_address   INET,
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы
CREATE INDEX idx_orders_client ON orders(client_id);
CREATE INDEX idx_orders_master ON orders(master_id);
CREATE INDEX idx_orders_status ON orders(status_id);
CREATE INDEX idx_cars_client   ON cars(client_id);
CREATE INDEX idx_login_attempts_username ON login_attempts(username);

-- Представление для расчёта стоимости заявки (для сотрудников)
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

-- Представление для клиента: возвращает только заявки текущего клиента.
-- Идентификатор клиента передаётся приложением в сессионной переменной app.client_id.
CREATE VIEW v_my_orders AS
SELECT
    o.id           AS order_id,
    cr.plate_num   AS car,
    os.name        AS status,
    COALESCE(SUM(osv.price_at * osv.quantity), 0) AS total_cost,
    o.created_at
FROM orders o
JOIN cars cr           ON cr.id = o.car_id
JOIN order_statuses os ON os.id = o.status_id
LEFT JOIN order_services osv ON osv.order_id = o.id
WHERE o.client_id = NULLIF(current_setting('app.client_id', true), '')::INTEGER
GROUP BY o.id, cr.plate_num, os.name, o.created_at;

-- 6.1. Создание заявки
-- SECURITY DEFINER позволяет мастеру-приёмщику создавать заявки, не имея
-- прямого права INSERT на таблицу orders.
CREATE OR REPLACE FUNCTION create_order(
    p_client_id   INTEGER,
    p_car_id      INTEGER,
    p_master_id   INTEGER,
    p_description TEXT
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_order_id INTEGER;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM clients WHERE id = p_client_id) THEN
        RAISE EXCEPTION 'Клиент с id=% не найден', p_client_id;
    END IF;

    -- Автомобиль должен принадлежать указанному клиенту
    IF NOT EXISTS (
        SELECT 1 FROM cars
        WHERE id = p_car_id AND client_id = p_client_id
    ) THEN
        RAISE EXCEPTION 'Автомобиль с id=% не принадлежит клиенту с id=%',
            p_car_id, p_client_id;
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

-- 6.5. Получение заявок клиента (для сотрудников)
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
SECURITY DEFINER
SET search_path = public, pg_temp
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

-- 7.3. Триггер: запрет добавления и изменения услуг в завершённой заявке
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
BEFORE INSERT OR UPDATE ON order_services
FOR EACH ROW
EXECUTE FUNCTION trg_check_order_status();

-- 11.1. Регистрация попытки входа и автоматическая блокировка
CREATE OR REPLACE FUNCTION register_login_attempt(
    p_username VARCHAR,
    p_success  BOOLEAN,
    p_ip       INET DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id  INTEGER;
    v_attempts INTEGER;
BEGIN
    INSERT INTO login_attempts (username, success, ip_address)
    VALUES (p_username, p_success, p_ip);

    SELECT id INTO v_user_id FROM users WHERE username = p_username;
    IF v_user_id IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO user_security (user_id) VALUES (v_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    IF p_success THEN
        UPDATE user_security
        SET failed_attempts = 0,
            is_blocked = FALSE,
            blocked_until = NULL,
            last_attempt_at = CURRENT_TIMESTAMP
        WHERE user_id = v_user_id;
    ELSE
        UPDATE user_security
        SET failed_attempts = failed_attempts + 1,
            last_attempt_at = CURRENT_TIMESTAMP
        WHERE user_id = v_user_id
        RETURNING failed_attempts INTO v_attempts;

        IF v_attempts >= 5 THEN
            UPDATE user_security
            SET is_blocked = TRUE,
                blocked_until = CURRENT_TIMESTAMP + INTERVAL '15 minutes'
            WHERE user_id = v_user_id;
        END IF;
    END IF;
END;
$$;

-- 11.2. Проверка блокировки перед аутентификацией
CREATE OR REPLACE FUNCTION is_user_blocked(p_username VARCHAR)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_blocked BOOLEAN;
BEGIN
    SELECT (us.is_blocked
            AND (us.blocked_until IS NULL OR us.blocked_until > CURRENT_TIMESTAMP))
    INTO v_blocked
    FROM user_security us
    JOIN users u ON u.id = us.user_id
    WHERE u.username = p_username;

    RETURN COALESCE(v_blocked, FALSE);
END;
$$;

-- 11.3. Снятие истёкших блокировок (может вызываться по расписанию pg_cron)
CREATE OR REPLACE FUNCTION unblock_expired_users()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_count INTEGER;
BEGIN
    UPDATE user_security
    SET is_blocked = FALSE,
        blocked_until = NULL,
        failed_attempts = 0
    WHERE is_blocked = TRUE
      AND blocked_until IS NOT NULL
      AND blocked_until <= CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$;

-- ---------------------------------------------------------------------------
-- 12. Учётные записи: хеширование паролей и создание пользователей.
-- Хеширование выполняет PostgreSQL (pgcrypto, bcrypt), поэтому открытый
-- пароль не хранится и не попадает в приложение в виде хеша.
-- ---------------------------------------------------------------------------

-- 12.1. Хеширование пароля. Стоимость bcrypt — 10 (около 100 мс на проверку).
CREATE OR REPLACE FUNCTION app_hash_password(p_password TEXT)
RETURNS TEXT
LANGUAGE sql
VOLATILE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT crypt(p_password, gen_salt('bf', 10));
$$;

-- 12.2. Проверка пароля по сохранённому хешу.
CREATE OR REPLACE FUNCTION app_check_password(
    p_password      TEXT,
    p_password_hash TEXT
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    SELECT p_password_hash IS NOT NULL
       AND p_password_hash = crypt(p_password, p_password_hash);
$$;

-- 12.3. Создание учётной записи.
--   p_username  — логин (приводится к нижнему регистру);
--   p_password  — открытый пароль, хешируется внутри функции;
--   p_full_name — ФИО сотрудника или клиента;
--   p_role      — прикладная роль из таблицы roles:
--                 admin | manager | master | client;
--   p_phone     — телефон клиента (обязателен для новой карточки clients);
--   p_email     — e-mail клиента (необязателен);
--   p_client_id — существующая карточка клиента (необязательна).
-- Для роли client создаётся карточка clients либо используется готовая;
-- для остальных ролей создаётся только запись в users.
CREATE OR REPLACE FUNCTION create_account(
    p_username  VARCHAR,
    p_password  TEXT,
    p_full_name VARCHAR,
    p_role      VARCHAR,
    p_phone     VARCHAR DEFAULT NULL,
    p_email     VARCHAR DEFAULT NULL,
    p_client_id INTEGER DEFAULT NULL
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_username  VARCHAR(50);
    v_role_name VARCHAR(50);
    v_role_id   INTEGER;
    v_client_id INTEGER;
    v_user_id   INTEGER;
BEGIN
    v_username := lower(btrim(p_username));
    v_role_name := lower(btrim(p_role));

    IF v_username IS NULL OR v_username = '' THEN
        RAISE EXCEPTION 'Логин не может быть пустым';
    END IF;

    IF p_password IS NULL OR length(p_password) < 8 THEN
        RAISE EXCEPTION 'Пароль должен содержать не менее 8 символов';
    END IF;

    IF p_full_name IS NULL OR btrim(p_full_name) = '' THEN
        RAISE EXCEPTION 'ФИО не может быть пустым';
    END IF;

    SELECT id INTO v_role_id FROM roles WHERE name = v_role_name;
    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'Неизвестная роль «%». Допустимые роли: %',
            p_role,
            (SELECT string_agg(name, ', ' ORDER BY name) FROM roles);
    END IF;

    IF EXISTS (SELECT 1 FROM users WHERE username = v_username) THEN
        RAISE EXCEPTION 'Пользователь с логином «%» уже существует', v_username;
    END IF;

    IF v_role_name = 'client' THEN
        IF p_client_id IS NOT NULL THEN
            IF NOT EXISTS (SELECT 1 FROM clients WHERE id = p_client_id) THEN
                RAISE EXCEPTION 'Клиент с id=% не найден', p_client_id;
            END IF;

            IF EXISTS (SELECT 1 FROM users WHERE client_id = p_client_id) THEN
                RAISE EXCEPTION 'К карточке клиента id=% уже привязана учётная запись',
                    p_client_id;
            END IF;

            v_client_id := p_client_id;
        ELSE
            IF p_phone IS NULL OR btrim(p_phone) = '' THEN
                RAISE EXCEPTION 'Для новой карточки клиента обязательно указать телефон';
            END IF;

            IF EXISTS (SELECT 1 FROM clients WHERE phone = btrim(p_phone)) THEN
                RAISE EXCEPTION 'Клиент с телефоном % уже существует; передайте p_client_id',
                    p_phone;
            END IF;

            INSERT INTO clients (full_name, phone, email)
            VALUES (btrim(p_full_name), btrim(p_phone), p_email)
            RETURNING id INTO v_client_id;
        END IF;
    END IF;

    INSERT INTO users (username, password_hash, full_name, role_id, client_id)
    VALUES (
        v_username,
        app_hash_password(p_password),
        btrim(p_full_name),
        v_role_id,
        v_client_id
    )
    RETURNING id INTO v_user_id;

    INSERT INTO user_security (user_id) VALUES (v_user_id)
    ON CONFLICT (user_id) DO NOTHING;

    RETURN v_user_id;
END;
$$;

-- ---------------------------------------------------------------------------
-- Роли БД и разграничение прав.
-- Приложение работает под sto_admin; остальные роли демонстрируют, как
-- доступ ограничивается на уровне PostgreSQL.
-- ---------------------------------------------------------------------------
CREATE ROLE sto_admin   LOGIN PASSWORD 'admin_pass';
CREATE ROLE sto_manager LOGIN PASSWORD 'manager_pass';
CREATE ROLE sto_master  LOGIN PASSWORD 'master_pass';
CREATE ROLE sto_client  LOGIN PASSWORD 'client_pass';

-- Права администратора (полный доступ)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO sto_admin;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO sto_admin;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO sto_admin;

-- Права мастера-приёмщика (работа с заявками и клиентами).
-- Прямого INSERT на orders нет: создание заявки выполняется через create_order().
-- UPDATE ограничен колонкой status_id: дату закрытия ставит только триггер.
GRANT SELECT, INSERT, UPDATE ON clients        TO sto_manager;
GRANT SELECT, INSERT, UPDATE ON cars           TO sto_manager;
GRANT SELECT ON orders                         TO sto_manager;
GRANT UPDATE (status_id) ON orders             TO sto_manager;
GRANT SELECT, INSERT, UPDATE ON order_services TO sto_manager;
GRANT SELECT ON services                       TO sto_manager;
GRANT SELECT ON order_statuses                 TO sto_manager;
GRANT SELECT ON masters                        TO sto_manager;
GRANT SELECT ON v_order_total                  TO sto_manager;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sto_manager;

-- Права мастера (ограниченное обновление)
GRANT SELECT ON orders                 TO sto_master;
GRANT SELECT ON clients                TO sto_master;
GRANT SELECT ON cars                   TO sto_master;
GRANT SELECT ON order_statuses         TO sto_master;
GRANT SELECT, INSERT ON order_services TO sto_master;
GRANT UPDATE (status_id)               ON orders TO sto_master;
GRANT SELECT ON v_order_total          TO sto_master;

-- Права клиента: только собственные заявки через представление v_my_orders
-- и просмотр прайс-листа.
GRANT SELECT ON v_my_orders TO sto_client;
GRANT SELECT ON services    TO sto_client;

-- Права на выполнение хранимых функций выдаются после их создания
GRANT EXECUTE ON FUNCTION create_order(INTEGER, INTEGER, INTEGER, TEXT) TO sto_manager;
GRANT EXECUTE ON FUNCTION add_service_to_order(INTEGER, INTEGER, INTEGER) TO sto_manager, sto_master;
GRANT EXECUTE ON FUNCTION close_order(INTEGER) TO sto_master, sto_manager;
GRANT EXECUTE ON FUNCTION calculate_order_total(INTEGER) TO sto_manager, sto_master;
GRANT EXECUTE ON FUNCTION get_client_orders(INTEGER) TO sto_manager;
GRANT EXECUTE ON FUNCTION register_login_attempt(VARCHAR, BOOLEAN, INET) TO sto_admin, sto_manager;
GRANT EXECUTE ON FUNCTION is_user_blocked(VARCHAR) TO sto_admin, sto_manager;
GRANT EXECUTE ON FUNCTION unblock_expired_users() TO sto_admin;

-- Функции учётных записей: по умолчанию PostgreSQL разрешает EXECUTE всем
-- (PUBLIC), поэтому право нужно явно отозвать, иначе любой пользователь БД
-- сможет создать себе учётную запись с ролью admin.
REVOKE ALL ON FUNCTION app_hash_password(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION app_check_password(TEXT, TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION create_account(VARCHAR, TEXT, VARCHAR, VARCHAR, VARCHAR, VARCHAR, INTEGER) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION app_check_password(TEXT, TEXT) TO sto_admin;
GRANT EXECUTE ON FUNCTION create_account(VARCHAR, TEXT, VARCHAR, VARCHAR, VARCHAR, VARCHAR, INTEGER) TO sto_admin;

-- Группа «только чтение» для демонстрации наследования прав.
CREATE ROLE sto_readonly_group;

GRANT SELECT ON services       TO sto_readonly_group;
GRANT SELECT ON order_statuses TO sto_readonly_group;
GRANT SELECT ON masters        TO sto_readonly_group;

GRANT sto_readonly_group TO sto_manager;
GRANT sto_readonly_group TO sto_master;

-- Отзываем прямое право у manager: доступ к services остаётся через группу.
REVOKE SELECT ON services FROM sto_manager;

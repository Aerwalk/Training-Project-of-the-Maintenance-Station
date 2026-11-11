-- ---------------------------------------------------------------------------
-- Тестовые (демонстрационные) данные для стенда СТО.
-- Выполняется автоматически после sto.sql при первом запуске контейнера.
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Клиенты (создаются до пользователей: учётная запись клиента ссылается на карточку)
INSERT INTO clients (full_name, phone, email) VALUES
    ('Иванов Иван Иванович',    '+7-900-100-10-01', 'ivanov@example.ru'),
    ('Петрова Мария Сергеевна', '+7-900-100-10-02', 'petrova@example.ru'),
    ('Сидоров Алексей Петрович','+7-900-100-10-03', 'sidorov@example.ru');

-- Пользователи. Пароли хешируются bcrypt средствами PostgreSQL (pgcrypto).
INSERT INTO users (username, password_hash, full_name, role_id)
SELECT 'admin', crypt('admin123', gen_salt('bf')), 'Поляков Роман Евгеньевич', r.id
FROM roles r WHERE r.name = 'admin';

INSERT INTO users (username, password_hash, full_name, role_id)
SELECT 'manager', crypt('manager123', gen_salt('bf')), 'Смирнова Анна Викторовна', r.id
FROM roles r WHERE r.name = 'manager';

INSERT INTO users (username, password_hash, full_name, role_id)
SELECT 'master', crypt('master123', gen_salt('bf')), 'Кузнецов Дмитрий Сергеевич', r.id
FROM roles r WHERE r.name = 'master';

-- Учётная запись клиента привязана к карточке клиента:
-- благодаря этому роль client видит только свои заявки.
INSERT INTO users (username, password_hash, full_name, role_id, client_id)
SELECT 'client', crypt('client123', gen_salt('bf')), 'Иванов Иван Иванович', r.id, c.id
FROM roles r
JOIN clients c ON c.phone = '+7-900-100-10-01'
WHERE r.name = 'client';

-- Строки учёта безопасности для всех пользователей
INSERT INTO user_security (user_id)
SELECT id FROM users;

-- Мастер
INSERT INTO masters (user_id, specialization, is_active)
SELECT u.id, 'Диагностика, двигатель, ходовая часть', TRUE
FROM users u WHERE u.username = 'master';

-- Автомобили
INSERT INTO cars (client_id, brand, model, year, plate_num, vin)
SELECT c.id, 'Lada',  'Vesta', 2021, 'А123ВС64', 'XTA218050M0123456'
FROM clients c WHERE c.phone = '+7-900-100-10-01';

INSERT INTO cars (client_id, brand, model, year, plate_num, vin)
SELECT c.id, 'Toyota', 'Camry', 2019, 'В456ЕК64', 'JTNBF3HK803012345'
FROM clients c WHERE c.phone = '+7-900-100-10-02';

INSERT INTO cars (client_id, brand, model, year, plate_num, vin)
SELECT c.id, 'Kia', 'Rio', 2020, 'С789МН64', 'Z94C241ABLR123456'
FROM clients c WHERE c.phone = '+7-900-100-10-03';

INSERT INTO cars (client_id, brand, model, year, plate_num, vin)
SELECT c.id, 'Lada', 'Granta', 2017, 'Е321ОР64', 'XTA219050J0123456'
FROM clients c WHERE c.phone = '+7-900-100-10-01';

-- Услуги
INSERT INTO services (name, price, duration_min) VALUES
    ('Компьютерная диагностика', 2000.00, 45),
    ('Замена моторного масла',    800.00, 30),
    ('Замена тормозных колодок', 2500.00, 90),
    ('Развал-схождение',         3000.00, 60),
    ('Замена ремня ГРМ',         6500.00, 180),
    ('Шиномонтаж (комплект)',    3200.00, 60);

-- Заявка №1: новая, без мастера (клиент Иванов)
INSERT INTO orders (client_id, car_id, status_id, description)
SELECT c.id, cr.id, s.id, 'Стук в передней подвеске при проезде неровностей'
FROM clients c
JOIN cars cr ON cr.client_id = c.id
CROSS JOIN (SELECT id FROM order_statuses WHERE name = 'новая') s
WHERE c.phone = '+7-900-100-10-01' AND cr.plate_num = 'А123ВС64';

-- Заявка №2: в работе, назначен мастер (клиент Петрова)
INSERT INTO orders (client_id, car_id, master_id, status_id, description)
SELECT c.id, cr.id, m.id, s.id, 'Плановое ТО: масло, фильтры, диагностика'
FROM clients c
JOIN cars cr ON cr.client_id = c.id
CROSS JOIN (SELECT id FROM masters LIMIT 1) m
CROSS JOIN (SELECT id FROM order_statuses WHERE name = 'в работе') s
WHERE c.phone = '+7-900-100-10-02' AND cr.plate_num = 'В456ЕК64';

-- Заявка №3: завершённая (клиент Сидоров).
-- Создаём в статусе «новая», добавляем услуги и только потом закрываем:
-- триггер запрещает менять состав завершённой заявки.
INSERT INTO orders (client_id, car_id, master_id, status_id, description)
SELECT c.id, cr.id, m.id, s.id, 'Замена тормозных колодок и развал-схождение'
FROM clients c
JOIN cars cr ON cr.client_id = c.id
CROSS JOIN (SELECT id FROM masters LIMIT 1) m
CROSS JOIN (SELECT id FROM order_statuses WHERE name = 'новая') s
WHERE c.phone = '+7-900-100-10-03' AND cr.plate_num = 'С789МН64';

-- Заявка №4: в работе (клиент Иванов, второй автомобиль)
INSERT INTO orders (client_id, car_id, master_id, status_id, description)
SELECT c.id, cr.id, m.id, s.id, 'Шиномонтаж и развал-схождение'
FROM clients c
JOIN cars cr ON cr.client_id = c.id
CROSS JOIN (SELECT id FROM masters LIMIT 1) m
CROSS JOIN (SELECT id FROM order_statuses WHERE name = 'в работе') s
WHERE c.phone = '+7-900-100-10-01' AND cr.plate_num = 'Е321ОР64';

-- Услуги в заявке №2
INSERT INTO order_services (order_id, service_id, quantity, price_at)
SELECT o.id, s.id, 1, s.price
FROM orders o
JOIN services s ON s.name IN ('Компьютерная диагностика', 'Замена моторного масла')
WHERE o.description = 'Плановое ТО: масло, фильтры, диагностика';

-- Услуги в заявке №3
INSERT INTO order_services (order_id, service_id, quantity, price_at)
SELECT o.id, s.id, 1, s.price
FROM orders o
JOIN services s ON s.name IN ('Замена тормозных колодок', 'Развал-схождение')
WHERE o.description = 'Замена тормозных колодок и развал-схождение';

-- Услуги в заявке №4
INSERT INTO order_services (order_id, service_id, quantity, price_at)
SELECT o.id, s.id, 1, s.price
FROM orders o
JOIN services s ON s.name IN ('Шиномонтаж (комплект)', 'Развал-схождение')
WHERE o.description = 'Шиномонтаж и развал-схождение';

-- Закрытие заявки №3 (триггеры заполнят closed_at и запишут журнал статусов)
UPDATE orders
SET status_id = (SELECT id FROM order_statuses WHERE name = 'завершена')
WHERE description = 'Замена тормозных колодок и развал-схождение';

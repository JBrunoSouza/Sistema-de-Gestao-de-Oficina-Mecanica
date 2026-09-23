-- Dados ficticios, inseridos apenas na primeira inicializacao.
INSERT INTO customer(name, phone, email) VALUES
 ('Ana Lima', '(87) 99911-1001', 'ana@example.com'),
 ('Bruno Santos', '(87) 99922-1002', 'bruno@example.com'),
 ('Carla Souza', '(87) 99933-1003', 'carla@example.com');
INSERT INTO vehicle(plate,brand,model,mileage,manufacture_year,customer_id) VALUES
 ('ABC1D23','Fiat','Argo',42000,2021,1),('DEF4G56','Toyota','Corolla',68000,2020,2),('GHI7J89','Chevrolet','Onix',31000,2022,3);
INSERT INTO part(name,unit_price,stock) VALUES ('Filtro de óleo',35.90,12),('Óleo 5W30 (litro)',49.90,20),('Pastilha de freio',180.00,6);
INSERT INTO service_order(customer_id,vehicle_id,complaint,entry_date,mileage,responsible,status,diagnosis,decision_at,decision_by,budget_total,payment_method,pickup_date) VALUES
 (1,1,'Revisão anterior',CURRENT_DATE,40000,'Matheus','CLOSED','Revisão realizada',CURRENT_TIMESTAMP,'Matheus',120.00,'Dinheiro',CURRENT_DATE);
INSERT INTO service_order(customer_id,vehicle_id,complaint,entry_date,mileage,responsible,status,diagnosis) VALUES
 (1,1,'Ruído ao frear',CURRENT_DATE,42000,'Matheus','OPEN','');
INSERT INTO service_order(customer_id,vehicle_id,complaint,entry_date,mileage,responsible,status,diagnosis,decision_at,decision_by,budget_total) VALUES
 (2,2,'Revisão preventiva',CURRENT_DATE,68000,'Thiago','APPROVED','Substituir filtro de óleo',CURRENT_TIMESTAMP,'Thiago',155.90),
 (3,3,'Verificar alinhamento',CURRENT_DATE,31000,'Matheus','REJECTED','Alinhamento recomendado',CURRENT_TIMESTAMP,'Matheus',90.00);
INSERT INTO active_order(vehicle_id,order_id) VALUES(1,2),(2,3),(3,4);
INSERT INTO order_item(order_id,kind,description,quantity,unit_price,completed) VALUES
 (1,'SERVICE','Revisão básica',1,120.00,TRUE),
 (3,'SERVICE','Troca de óleo',1,120.00,FALSE),
 (4,'SERVICE','Alinhamento',1,90.00,FALSE);
INSERT INTO order_item(order_id,kind,description,quantity,unit_price,part_id) VALUES(3,'PART','Filtro de óleo',1,35.90,1);
INSERT INTO app_meta(version) VALUES(1);

INSERT INTO app_user (name, username, password_hash, role) VALUES
 ('Administrador', 'admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'GERENTE'),
 ('Ana Atendimento', 'ana', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'ATENDENTE'),
 ('Carlos Mecânico', 'carlos', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'MECANICO');


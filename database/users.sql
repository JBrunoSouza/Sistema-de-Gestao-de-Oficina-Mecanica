-- Idempotent provisioning of the project's demonstration accounts.
-- Existing passwords, names and roles are preserved.
INSERT INTO app_user(name,username,password_hash,role)
SELECT 'Administrador','admin','8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92','GERENTE'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username='admin');
INSERT INTO app_user(name,username,password_hash,role)
SELECT 'Ana Atendimento','ana','8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92','ATENDENTE'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username='ana');
INSERT INTO app_user(name,username,password_hash,role)
SELECT 'Carlos Mecânico','carlos','8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92','MECANICO'
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username='carlos');

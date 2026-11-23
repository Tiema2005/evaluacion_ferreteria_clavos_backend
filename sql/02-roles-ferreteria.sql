-- EJECUTAR UNA VEZ EN LA MISMA BD DEL EQUIPO (tras 01-usuarios.sql).
-- No crea administradores por defecto ni introduce contraseñas en SQL.
INSERT INTO public.rol (nombre, descripcion) VALUES
('ADMINISTRADOR', 'Gestiona usuarios, permisos, catalogo y pedidos'),
('VENDEDOR', 'Gestiona ventas y pedidos'),
('CONTRATISTA', 'Cliente con perfil de contratista'),
('CLIENTE', 'Cliente registrado')
ON CONFLICT (nombre) DO NOTHING;

SELECT id_rol, nombre FROM public.rol ORDER BY id_rol;

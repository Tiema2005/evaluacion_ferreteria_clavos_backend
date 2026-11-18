-- Confirma la base y las tablas antes de iniciar la API.
SELECT current_database() AS base,
       to_regclass('public.usuario') AS tabla_usuario,
       to_regclass('public.rol') AS tabla_rol;

SELECT id_rol, nombre FROM public.rol ORDER BY id_rol;

-- Cambia el correo por el de Postman. La consulta muestra el formato, sin mostrar el hash.
SELECT u.id_usuario, u.correo, r.nombre AS rol, u.activo,
       u.password_hash LIKE '$2a$%' AS hash_bcrypt
FROM public.usuario u
JOIN public.rol r ON r.id_rol = u.id_rol
WHERE u.correo = 'avance18@example.com';

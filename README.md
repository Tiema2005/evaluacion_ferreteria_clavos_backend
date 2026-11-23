# Ferretería Los Maestros · Backend

Avance de DSY1104, equipo 4. API de usuarios con Spring Boot y PostgreSQL. El módulo usa `public.usuario` y `public.rol` del esquema del equipo. El servidor registra las cuentas públicas con el rol `CLIENTE`, guarda la contraseña con BCrypt y emite un JWT firmado con vigencia de una hora al iniciar sesión.

## Estado del avance

| Operación | Estado | Evidencia o paso pendiente |
| --- | --- | --- |
| Tabla de usuarios | En avance | Verificar en el RDS del equipo una cuenta de prueba antes y después de reiniciar la API. |
| Inicio de sesión | En avance | Verificar en Postman un `200` con JWT y un `401` con credenciales incorrectas. |
| Registro | En avance | Verificar en Postman un `201` y en RDS el rol asignado y el hash. |

Actualiza cada fila a **Listo** cuando el equipo complete las pruebas en su entorno. Mantén los resultados reales en esta tabla.

La API se probó en PostgreSQL 16 local. El registro persistió después de reiniciar Spring Boot y el login entregó un JWT firmado. Queda repetir la verificación con el RDS del equipo.

## Arranque en EC2

Requiere JDK 17 o superior, Maven y acceso al RDS desde EC2. Conéctate a `ferreteria_db` en tu cliente SQL y verifica las tablas con la primera consulta de [verificar-usuarios.sql](sql/verificar-usuarios.sql). El archivo [01-usuarios.sql](sql/01-usuarios.sql) crea `public.rol` y `public.usuario` si faltan y deja disponible el rol `CLIENTE`. Si el equipo ya usa otro nombre para el rol de cliente, conserva ese nombre y configura `FERRETERIA_AUTH_ROL_CLIENTE` con su valor exacto.

En la terminal de EC2, en la carpeta donde está el `pom.xml`, utiliza los valores privados del equipo. Cambia `HOST_RDS`, `USUARIO_RDS` y, si corresponde, la ruta al certificado CA que ya usa el proyecto.

```bash
export DB_URL='jdbc:postgresql://HOST_RDS:5432/ferreteria_db?sslmode=verify-full&sslrootcert=/home/ubuntu/global-bundle.pem'
export DB_USER='USUARIO_RDS'
read -rsp 'Contraseña de PostgreSQL: ' DB_PASSWORD
printf '\n'
export DB_PASSWORD
export JWT_SECRET="$(openssl rand -hex 32)"
mvn spring-boot:run
```

Para reiniciar durante la prueba, detén con Ctrl+C y vuelve a ejecutar `mvn spring-boot:run` en la misma terminal. Así se conserva la configuración de la sesión. Si lo ejecutas como servicio, configura las variables en el servicio. Mantén la misma clave JWT entre reinicios si quieres conservar los tokens ya emitidos. La API escucha en `127.0.0.1:8080` por defecto.

Si el Spring Boot del equipo ya tiene código, integra el módulo usando su configuración de PostgreSQL. Copia `src/main/java/cl/ferreteria/avance/`, incorpora las dependencias del `pom.xml` de este paquete que falten en el existente y agrega `@Import(cl.ferreteria.avance.ConfiguracionUsuarios.class)` a la clase principal si queda fuera del escaneo del proyecto. Agrega la propiedad `ferreteria.auth.jwt-secret=${JWT_SECRET}` a tu configuración. Verifica si el backend ya tiene rutas o una cadena de Spring Security para usuarios. En ese caso integra las rutas y permisos en esa configuración antes de arrancar. Añade esta tabla a tu README.

## Pruebas del avance en Postman

Si Postman está en tu computador, reenvía el puerto 8080 desde VS Code Remote-SSH en **Ports** o ejecuta `ssh -N -L 8080:127.0.0.1:8080 lab-springboot` desde tu computador, si ese es tu alias de SSH. Conserva esa conexión abierta. Importa [Ferreteria-Avance.postman_collection.json](postman/Ferreteria-Avance.postman_collection.json), configura `correo` con una cuenta de prueba nueva y usa una contraseña exclusiva de prueba en `password`. La colección apunta a `http://127.0.0.1:8080/api/v1`.

1. Ejecuta **Registrar usuario**. Espera un `201` y comprueba que la respuesta indica el rol que asignó el servidor.
2. Ejecuta la consulta final de `sql/verificar-usuarios.sql` con el mismo correo. Espera una fila, el rol del paso anterior y `hash_bcrypt = true`.
3. Reinicia Spring Boot sin cambiar la base de datos. Repite la consulta y comprueba que devuelve el mismo ID. Ejecuta **Iniciar sesión** con esa cuenta. Espera un `200` con `token` y `expiraEnSegundos = 3600`.
4. Ejecuta **Rechazar credenciales incorrectas**. Espera un `401` con `Credenciales incorrectas.`. Prueba también un correo inexistente para comprobar el mismo mensaje.

## Repositorio del avance

La revisión del docente se hace en GitHub, en la rama principal `main`. El
`README.md` y los commits del avance deben verse desde una ventana privada.
Las credenciales de PostgreSQL y el secreto de JWT se configuran en el entorno
de ejecución de EC2.

[Repositorio público del backend](https://github.com/Tiema2005/evaluacion_ferreteria_clavos_backend)



## Ampliación del domingo 22: CRUD, roles, JWT y Swagger (código preparado)

**No marques como "Listo" hasta ejecutar las pruebas reales contra el RDS del equipo.**
Este ZIP se construyó a partir del respaldo compartido; conserva el código de registro, login y sus archivos SQL originales. Se agregaron los archivos `UsuariosCrudController`, `SeguridadUsuarios`, `ErroresUsuarios`, un SQL de roles y una colección de Postman para el CRUD. Verifica la compatibilidad con el esquema del equipo antes de fusionar estos cambios a `main`.

### Configuración y arranque

1. Confirma que `public.usuario` tenga las columnas `id_usuario`, `id_rol`, `nombre`, `apellido`, `correo`, `password_hash`, `activo` y que `public.rol` tenga `id_rol` y `nombre`.
2. Ejecuta `sql/01-usuarios.sql` **solo si aún no existen las tablas**. Este script usa `CREATE TABLE IF NOT EXISTS` y no migra tablas preexistentes. Revisa primero el esquema real del equipo. Ejecuta `sql/02-roles-ferreteria.sql` para crear los roles que falten.
3. Exporta `DB_URL`, `DB_USER`, `DB_PASSWORD` y `JWT_SECRET` con valores privados del equipo; no los agregues al repositorio. En Windows PowerShell puedes establecerlos durante la sesión así (reemplaza los valores de ejemplo):

```powershell
$env:DB_URL = 'jdbc:postgresql://HOST_RDS:5432/ferreteria_db?sslmode=require'
$env:DB_USER = 'USUARIO_RDS'
$env:DB_PASSWORD = 'TU_PASSWORD_PRIVADA'
# Genera y guarda un secreto hexadecimal de 32 bytes en un lugar seguro del equipo;
# el valor aquí es deliberadamente un marcador de posición, NO una clave válida.
$env:JWT_SECRET = 'PEGA_AQUI_64_CARACTERES_HEX_GENERADOS_EN_PRIVADO'
mvn spring-boot:run
```

Si dispones de certificado RDS usa `sslmode=verify-full` con `sslrootcert` tal como en la configuración original del equipo; `require` solo cifra el canal, sin validar el nombre del servidor. **No pegues estos valores reales en ChatGPT ni en GitHub**. En EC2 utiliza variables de entorno equivalentes (ver sección anterior). Configura opcionalmente `CORS_ORIGINS` con el origen EXACTO de React (`https://...`), separado por comas si son varios.

4. Accede a Swagger en `http://localhost:8080/swagger-ui/index.html` o mediante túnel SSH seguro hacia EC2. La aplicación escucha solo en `127.0.0.1:8080` salvo que `SERVER_ADDRESS` indique otra interfaz. Evita exponer el puerto 8080 públicamente; usa túnel SSH o un proxy HTTPS con configuración adecuada.
5. Importa `postman/Ferreteria-CRUD-JWT.postman_collection.json`. Registra una cuenta de prueba nueva, inicia sesión y utiliza la variable `token` que guarda el login. Los ejemplos usan correos ficticios; cambia el correo si ya existe.

### Roles y permisos

- Registro público: crea siempre un usuario con el rol `CLIENTE`, decidido por el backend.
- `ADMINISTRADOR`: puede listar, consultar, crear empleados, editar roles y desactivar usuarios.
- `CLIENTE`, `VENDEDOR`, `CONTRATISTA`: pueden ver y editar únicamente **su propia cuenta**, no listar otras cuentas ni modificar roles.
- Los roles se consultan en PostgreSQL en cada operación protegida; la firma y expiración del JWT se verifican en Spring Security antes del controlador.
- Un usuario desactivado pierde acceso a las rutas de usuarios. Para cerrar el login inmediatamente después de desactivar, una autenticación posterior se rechaza.

**Crear el primer administrador para las pruebas:** regístrate con un correo exclusivo del equipo y, usando tu cliente SQL autorizado y acceso de DBA, actualiza manualmente **solo esa cuenta de prueba** a `ADMINISTRADOR`:

```sql
UPDATE public.usuario
SET id_rol = (SELECT id_rol FROM public.rol WHERE nombre = 'ADMINISTRADOR')
WHERE correo = 'CORREO_DE_PRUEBA_DEL_EQUIPO';
```

Comprueba con un `SELECT` el rol asignado y vuelve a iniciar sesión; guarda el token recibido solo en Postman. **Nunca crees una cuenta administrativa pública ni guardes contraseñas de prueba en GitHub.**

### API REST

| Método | Ruta | Acceso | Respuesta esperada |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/registro` | Público | 201 o 400/409 |
| POST | `/api/v1/auth/login` | Público | 200 con JWT o 401 |
| GET | `/api/v1/usuarios` | ADMINISTRADOR | 200; sin JWT 401, otro rol 403 |
| GET | `/api/v1/usuarios/{id}` | Titular o ADMINISTRADOR | 200, 403 o 404 |
| POST | `/api/v1/usuarios` | ADMINISTRADOR | 201, 400 o 409 |
| PUT | `/api/v1/usuarios/{id}` | Titular o ADMINISTRADOR | 200, 403, 404 o 409 |
| DELETE | `/api/v1/usuarios/{id}` | ADMINISTRADOR | 204, 403 o 404 (desactivación lógica) |

Se puede enviar un `PUT` parcial: los campos ausentes se mantienen. Solo el ADMINISTRADOR puede establecer `rol`. Una contraseña nueva se convierte a BCrypt. La respuesta jamás incluye el hash.

### Pruebas que deben quedar documentadas

- Registrar → 201. SQL confirma nuevo usuario, rol CLIENTE, y `password_hash` con BCrypt.
- Reiniciar backend → el usuario sigue en PostgreSQL/RDS.
- Login correcto → 200 + JWT con `expiraEnSegundos = 3600`. Incorrecto → 401 genérico.
- Sin token → 401; token modificado o caducado → 401; rol incorrecto → 403.
- Un CLIENTE puede editar su cuenta pero **no** ver ni editar el ID de otra persona → 403.
- ADMINISTRADOR puede listar y crear empleados, cambiar rol y desactivar una cuenta → 204.
- Swagger muestra todas las rutas y permite usar "Authorize" para las protegidas.

### Tabla de estado para el docente

| Operación | Estado actual (a verificar) | Evidencia requerida |
| --- | --- | --- |
| Crear usuarios | Código preparado | Postman 201 + SQL |
| Listar usuarios | Código preparado | Swagger 200 (administrador) |
| Consultar usuario | Código preparado | Swagger 200/403 (titular / ajeno) |
| Modificar usuario | Código preparado | Swagger 200 + SQL |
| Desactivar usuario | Código preparado | Swagger 204 + SQL |
| Login JWT | Código previo + verificación agregada | Postman 200, 401, 403 |
| Roles en base | SQL preparado | SELECT public.rol y pruebas de permiso |
| Swagger | Dependencia y rutas preparadas | Captura Swagger en entorno real |
| AWS/RDS | **No verificado en este ZIP** | Conexión, pruebas y reinicio en EC2 |

**Nota sobre el estado:** el código se ha preparado, pero **no se ha compilado ni ejecutado aquí con AWS/RDS**. Registra las pruebas reales realizadas por el equipo antes de la entrega.

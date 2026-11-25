# Ferretería Los Maestros — Backend

**Asignatura:** DSY1104 · Desarrollo Fullstack II (2026)  
**Proyecto:** Ferretería Los Maestros  
**Equipo:** 4  
**Etapa documentada:** Evaluación Parcial 3 — usuarios, autenticación, roles e integración con React  
**Actualización del estado:** 24 de noviembre de 2026

## 1. Descripción del proyecto

Backend REST desarrollado con **Java 21, Spring Boot 3.5.8, Spring Security y PostgreSQL** para el caso de Ferretería Los Maestros. Se ejecutó en una instancia **Amazon EC2 (Ubuntu)** y utiliza una base de datos relacional **PostgreSQL en Amazon RDS**.

En esta etapa, el módulo implementado gestiona usuarios, registro, inicio de sesión, JWT, permisos por rol y desactivación lógica de cuentas. El frontend está desarrollado con **React y Vite** y se comunica con la API mediante solicitudes HTTP. Se utiliza **Swagger/OpenAPI** para documentar y probar los endpoints.

**Alcance real de esta etapa:** el módulo de usuarios está integrado y probado. Las funcionalidades de catálogo y pedidos/solicitudes forman parte de la siguiente etapa de la Evaluación Parcial 3; no se presentan aquí como CRUD del backend ya terminado.

## 2. Repositorios y enlaces de demostración

- [Repositorio backend (GitHub)](https://github.com/Tiema2005/evaluacion_ferreteria_clavos_backend)
- [Repositorio frontend (GitHub)](https://github.com/Tiema2005/evaluacion_ferreteria_clavos_frontend)
- **Frontend publicado — última IP probada:** http://44.192.82.241/
- **Swagger UI — última IP probada:** http://44.192.82.241:8080/swagger-ui/index.html
- **Documento OpenAPI:** http://44.192.82.241:8080/v3/api-docs

> **Importante:** `44.192.82.241` fue la IP pública verificada durante las pruebas del 24/11/2026. No es una dirección permanente: al detener e iniciar EC2 puede cambiar. Antes de una demostración se deben actualizar los enlaces y, si cambia la IP utilizada en la compilación de React, volver a generar y desplegar el frontend con la nueva `VITE_API_URL`. Que un enlace histórico aparezca aquí no garantiza disponibilidad continua.
>
> El laboratorio utilizó **HTTP**, no HTTPS. Es un despliegue académico de demostración: no se deben utilizar credenciales personales ni tratar esta configuración como un despliegue de producción seguro.

## 3. Arquitectura y responsabilidades

```text
Navegador / React (Vite)
       |
       | solicitudes a /api/v1/...
       v
Spring Boot REST + Spring Security (EC2, puerto 8080)
       |
       | JDBC / consultas y operaciones sobre usuarios
       v
Amazon RDS PostgreSQL (base ferreteria_db, puerto 5432)

Nginx (EC2, puerto 80) entrega los archivos compilados de React.
Swagger UI documenta los endpoints implementados por Spring Boot.
```

- **React:** formularios, estado de sesión, navegación, mensajes y pantallas según el rol. La autorización efectiva se realiza nuevamente en el servidor.
- **Controladores Spring Boot:** reciben solicitudes HTTP y devuelven respuestas y códigos de estado.
- **Servicios y repositorios:** gestionan reglas de usuarios y acceso a datos.
- **Spring Security/JWT:** verifica la autenticación y restringe operaciones según los permisos.
- **PostgreSQL RDS:** guarda usuarios y roles independientemente de que se reinicie el proceso Java.

El código incluye clases del módulo de usuarios bajo `src/main/java/cl/ferreteria/avance/`, como `UsuariosController`, `UsuariosCrudController`, `UsuariosServicio`, `UsuariosRepositorio`, `SeguridadUsuarios` y `TokenUsuarios`.

## 4. Modelo de datos

En el RDS del equipo se verificó la existencia de **11 tablas**: `categoria`, `cliente`, `cuenta_corriente`, `despacho`, `detalle_pedido`, `movimiento_cuenta`, `movimiento_inventario`, `pedido`, `producto`, `rol` y `usuario`.

El módulo implementado en esta entrega trabaja principalmente con:

- **`public.rol`:** `id_rol`, `nombre`. Roles registrados: `ADMINISTRADOR`, `VENDEDOR`, `CONTRATISTA` y `CLIENTE`.
- **`public.usuario`:** `id_usuario`, `id_rol`, `nombre`, `apellido`, `correo`, `password_hash`, `activo` y `fecha_registro`.
- **Relación:** cada usuario referencia un rol mediante `usuario.id_rol`.
- **Integridad:** el correo es único; las contraseñas se almacenan mediante hash BCrypt y no se devuelven en las respuestas de la API.
- **Desactivación lógica:** la operación DELETE marca al usuario como inactivo; no borra físicamente su fila.

**Scripts presentes en el proyecto:** `sql/01-usuarios.sql`, `sql/02-roles-ferreteria.sql` y `sql/verificar-usuarios.sql`. **No se deben ejecutar scripts de creación sobre la base existente sin revisar previamente el esquema y los datos.**

## 5. Autenticación, sesiones y permisos

1. `POST /api/v1/auth/registro` valida los datos, crea la cuenta y asigna desde el **servidor** el rol inicial `CLIENTE`.
2. `POST /api/v1/auth/login` verifica las credenciales y devuelve un **JWT Bearer** con vigencia de **3600 segundos (1 hora)**.
3. El frontend envía el token en las solicitudes protegidas mediante el encabezado `Authorization: Bearer <token>`.
4. Spring Security verifica el JWT; el backend también aplica las reglas de acceso por rol y titularidad.
5. El frontend muestra el usuario y su rol, mantiene la sesión al recargar la página y permite cerrarla. El mecanismo concreto de almacenamiento de la sesión debe consultarse en `src/context/AuthContext.jsx` del frontend.

### Matriz de permisos del módulo de usuarios

| Operación | ADMINISTRADOR | CLIENTE / VENDEDOR / CONTRATISTA |
| --- | --- | --- |
| Registrarse e iniciar sesión | Sí | Sí |
| Listar usuarios | Sí | No |
| Crear usuarios desde el panel administrativo | Sí | No |
| Consultar datos de otro usuario | Sí | No |
| Consultar y modificar su propia cuenta | Sí | Sí |
| Cambiar roles | Sí | No |
| Desactivar cuentas | Sí | No |

**Seguridad:** una ruta protegida responde `401` si falta un token válido o el token venció, y `403` si el usuario autenticado no tiene permisos. El frontend también oculta las opciones no autorizadas, pero **eso por sí solo no sustituye los controles del backend**.

Para las pruebas se habilitó una cuenta administradora de forma controlada en RDS. El registro público no debe permitir elegir libremente el rol `ADMINISTRADOR`.

## 6. Contratos de la API REST implementada

**Prefijo de versión:** `/api/v1`

| Método | Endpoint | Acceso | Resultado esperado |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/registro` | Público | `201` al registrar; `400`/`409` para entradas inválidas o duplicados |
| POST | `/api/v1/auth/login` | Público | `200` con JWT; `401` con credenciales incorrectas |
| GET | `/api/v1/usuarios` | ADMINISTRADOR | `200` con listado; `401`/`403` según corresponda |
| GET | `/api/v1/usuarios/{id}` | Titular o ADMINISTRADOR | `200`, `403` o `404` |
| POST | `/api/v1/usuarios` | ADMINISTRADOR | `201`, `400` o `409` |
| PUT | `/api/v1/usuarios/{id}` | Titular o ADMINISTRADOR | `200`, `400`, `403`, `404` o `409` |
| DELETE | `/api/v1/usuarios/{id}` | ADMINISTRADOR | `204` al desactivar; `403`/`404` según corresponda |

El `PUT` implementado admite actualizar los campos enviados; los campos ausentes se conservan. Solo un administrador puede modificar el rol. Las respuestas no deben incluir `password_hash`.

En Swagger se observaron los siete endpoints anteriores agrupados en `usuarios-controller` y `usuarios-crud-controller`. No se documenta `/api/v1/auth/me` como implementado porque no apareció en las comprobaciones de esta versión.

## 7. Estado del trabajo y evidencias de prueba

Los resultados siguientes corresponden a las comprobaciones efectuadas por el equipo durante el 23 y 24 de noviembre de 2026, no a una garantía de disponibilidad permanente.

| Componente u operación | Estado | Evidencia observada |
| --- | --- | --- |
| Conexión EC2 → RDS | **Probado** | Conexión PostgreSQL desde Ubuntu y consulta de las tablas existentes |
| Tabla de usuarios y roles | **Probado** | Consulta SQL y usuarios persistidos en RDS |
| Registro público | **Probado** | Solicitud desde Swagger; usuario creado y visible en la base de datos |
| Inicio de sesión con JWT | **Probado** | Login desde Swagger y desde React con credenciales de prueba |
| CRUD de usuarios | **Probado** | Listado, creación, modificación de datos/rol y desactivación desde React; operaciones comprobadas también desde otro PC |
| Persistencia | **Probado** | Los cambios de usuario continuaron visibles al actualizar la página y al consultar RDS |
| Restricciones por rol | **Probado en la interfaz** | Cliente sin acceso al panel administrativo; administrador con acceso; sin sesión se redirige a login |
| Seguridad de endpoints | **Comprobación parcial** | Respuesta `401` sin token y uso de JWT en Swagger; conservar pruebas explícitas de token alterado/caducado y de acceso ajeno |
| Swagger/OpenAPI | **Probado** | Swagger disponible públicamente en EC2; siete endpoints del módulo de usuarios |
| Frontend React y API | **Probado** | Inicio de sesión y gestión de usuarios en React comunicándose con el backend en AWS |
| Nginx y frontend publicado | **Probado** | Página servida por puerto 80; archivos JavaScript cargados correctamente tras corregir el despliegue |
| Catálogo y pedidos con CRUD real integrado | **Pendiente de etapa posterior** | No se declara implementado en el backend de esta entrega |
| Inicio automático del backend al reiniciar EC2 | **Pendiente** | Se utilizó ejecución mediante `nohup`; no se ha configurado un servicio de arranque automático |
| HTTPS | **Pendiente** | La demostración se realizó por HTTP |

**Pruebas recomendadas para conservar como evidencia adicional:** rechazo de un JWT alterado o expirado, `403` desde una petición directa al backend con un rol sin permiso, acceso a un ID ajeno y validaciones `400`/`409`.

## 8. Ejecución y verificación en AWS EC2

### Requisitos

- Instancia EC2 Ubuntu con Java 21 y conectividad a Amazon RDS.
- Archivo JAR generado por Maven, disponible en `/home/ubuntu/ferreteria-backend/ferreteria-los-maestros-backend-0.1.0-SNAPSHOT.jar` durante las pruebas.
- Variables de entorno `DB_URL`, `DB_USER`, `DB_PASSWORD` y `JWT_SECRET` con valores válidos, mantenidos fuera de GitHub.
- Grupo de seguridad que permita los puertos necesarios para la demostración; acceso a RDS restringido a los componentes autorizados.

### Verificar el estado antes de ejecutar

En una conexión SSH a EC2:

```bash
sudo ss -ltnp | grep ':8080' || echo 'Backend no esta escuchando'
sudo systemctl is-active nginx
curl -sS -o /dev/null -w 'Swagger: HTTP %{http_code}\n' --max-time 10 http://127.0.0.1:8080/v3/api-docs
curl -sS -o /dev/null -w 'Frontend: HTTP %{http_code}\n' --max-time 10 http://127.0.0.1/
```

Si el backend ya responde `200` en `/v3/api-docs`, no es necesario iniciarlo otra vez. Si está apagado, configurar primero las variables privadas y luego iniciar el JAR.

### Ejemplo de arranque manual del JAR

**Ejemplo para cuando el backend no está activo** (no incluir la contraseña real en scripts, historial ni README):

```bash
export DB_URL='jdbc:postgresql://ferreteria-maestros-rds.c0fk3bg9ufjz.us-east-1.rds.amazonaws.com:5432/ferreteria_db?sslmode=require'
export DB_USER='postgres'
read -rsp 'Contrasena RDS: ' DB_PASSWORD
printf '\n'
export DB_PASSWORD

# Usar una clave JWT de 32 bytes o mas, guardada en un lugar seguro del equipo.
# Generar otra invalida las sesiones emitidas con la clave anterior.
export JWT_SECRET="$(openssl rand -hex 32)"

nohup java -jar /home/ubuntu/ferreteria-backend/ferreteria-los-maestros-backend-0.1.0-SNAPSHOT.jar \
  --server.address=0.0.0.0 --server.port=8080 \
  > /home/ubuntu/ferreteria-backend/springboot.log 2>&1 < /dev/null &
```

Comprueba el arranque:

```bash
curl -sS -o /dev/null -w 'Swagger: HTTP %{http_code}\n' --max-time 10 http://127.0.0.1:8080/v3/api-docs
tail -n 30 /home/ubuntu/ferreteria-backend/springboot.log
```

**Limitaciones de este arranque:** `nohup` permite conservar el proceso al cerrar SSH, pero **no lo vuelve a iniciar automáticamente al reiniciar EC2**. Además, el secreto generado con `openssl` cambia entre ejecuciones y puede invalidar tokens anteriores. Para un despliegue permanente deben configurarse un servicio administrado, un secreto persistente y HTTPS.

### Frontend en Nginx

Los archivos compilados de React se publicaron en `/var/www/ferreteria/` y Nginx respondió por el puerto `80`. El frontend se compila con la variable **`VITE_API_URL`**, por ejemplo:

```dotenv
VITE_API_URL=http://44.192.82.241:8080/api/v1
```

**No subas el archivo `.env` al repositorio.** La IP anterior es la última verificada, no un valor permanente. Después de cambiar `VITE_API_URL`, ejecuta nuevamente `npm run build` en el proyecto React y despliega la carpeta `dist` actualizada. Reiniciar Nginx sin reconstruir React no cambia una URL ya incluida en el JavaScript generado.

## 9. Comprobación rápida de la aplicación

1. Verificar la IP pública actual en EC2; comprobar que el frontend abre desde un equipo externo y que Swagger responde.
2. Registrar o utilizar una **cuenta ficticia** y realizar un login desde la aplicación.
3. Comprobar que el usuario y su rol aparecen en la navegación y que la sesión se conserva al recargar.
4. Entrar con `CLIENTE` e intentar abrir `/admin/usuarios`: la interfaz debe denegar el acceso.
5. Entrar con `ADMINISTRADOR`, abrir `/admin/usuarios` y crear, listar, editar y desactivar una cuenta ficticia.
6. Recargar y comprobar la persistencia; después abrir Swagger, usar **Authorize** con un JWT de prueba y ejecutar una operación protegida.
7. Confirmar que el endpoint `DELETE` devuelve `204` al **desactivar** una cuenta, sin eliminar físicamente la fila de PostgreSQL.

No incluir en capturas públicas contraseñas, JWT completos ni variables privadas.

## 10. Integración con React

El frontend utiliza React, React Router, servicios para consumir la API y componentes para los formularios. Durante la integración se incorporaron archivos como:

- `src/services/api.js` — configuración común de solicitudes a la API.
- `src/services/authService.js` — llamadas de registro e inicio de sesión.
- `src/context/AuthContext.jsx` — estado y acciones de autenticación.
- `src/components/guards/RutaProtegida.jsx` — control visual de rutas según sesión y rol.
- `src/pages/admin/UsuariosAdmin.jsx` — administración de usuarios desde la interfaz.

Los permisos deben comprobarse tanto en React como **en cada endpoint protegido del backend**. La integración de usuarios fue probada desde el frontend y desde Swagger; las pantallas de catálogo y pedidos no se contabilizan como API integrada en esta fase.

## 11. Control de versiones y estado de la evaluación

Se mantiene un repositorio separado para **backend** y **frontend**, con `main` como rama principal y una rama de integración `feature/ep3-integracion`.

- **Backend:** en la verificación del equipo, `origin/main` y `origin/feature/ep3-integracion` presentaban el mismo historial (`0 0`). El último commit comprobado era `d591102`, relacionado con CRUD de usuarios, roles, JWT y Swagger.
- **Frontend:** se integraron mediante Pull Request #1 dos commits: `df80c85` (registro, login y JWT) y `9d548aa` (permisos, rutas protegidas y CRUD de usuarios). El Pull Request fue fusionado a `main`.
- **Para la presentación:** el alcance demostrado es autenticación, sesiones, control de acceso, CRUD de usuarios y documentación en Swagger.
- **Para la entrega posterior:** desarrollar y probar catálogo, pedidos/solicitudes y su regla de stock (`409`), además de completar ERS, documentos de integración, manual por rol y evidencias adicionales, según la pauta.

El estado de este README debe actualizarse cuando se incorporen y prueben esas funcionalidades; no deben marcarse como terminadas por aparecer como pantallas o tablas existentes.

## 12. Seguridad y documentación pendiente

- Nunca publicar credenciales, contraseñas de prueba, tokens JWT, `.env`, archivos `.pem` ni secretos de firma.
- La clave de firma JWT no debe regenerarse involuntariamente entre reinicios si se espera conservar sesiones emitidas.
- La base de datos RDS no necesita estar accesible directamente desde Internet; la aplicación backend debe conectarse desde EC2.
- HTTPS, configuración de arranque automático y evidencias detalladas de pruebas de seguridad quedan por fortalecer.
- Las guías del curso solicitan enlaces accesibles al **diagrama entidad-relación** y al **diagrama de secuencia de autenticación**. Deben agregarse aquí cuando el equipo confirme las URL compartidas con permisos de lectura; este README no inventa enlaces.

---

**Nota de alcance:** este documento describe el avance que el equipo efectivamente mostró y comprobó hasta el 24/11/2026. No reemplaza las verificaciones finales en AWS después de detener o reiniciar la instancia.

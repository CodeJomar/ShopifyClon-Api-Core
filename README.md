# Shopify Clon API Core - Backend Transaccional Multi-Tenant

> **Núcleo de Servicios Backend de Comercio Electrónico Multi-Tienda de Alta Concurrencia y Disponibilidad.**  
> Diseñado bajo **Arquitectura Hexagonal (Ports & Adapters)**, **Controladores Atómicos** y **Domain-Driven Design (DDD)** con **Spring Boot 3.3.4**, **Java 17 LTS** y **PostgreSQL 14+ (Supabase)**.

---

## 1. Propósito del Sistema

El backend **Shopify Clon API Core** provee el motor transaccional, de autenticación y de catálogo para una plataforma de comercio electrónico multi-tienda (Multi-Tenant SaaS).

### Objetivos Clave:

- **Aislamiento Multi-Tenant Real:** Capacidad de operar múltiples tiendas independientes en una misma base de datos relacional mediante segregación por contexto (`X-Tienda-Id` o subdominio dinámico).
- **Independencia de Proveedor Cloud:** Aunque se utiliza la infraestructura de base de datos de Supabase (PostgreSQL), **toda la seguridad, hashing, tokens JWT, lógica de roles y control de acceso están implementados al 100% en código Java nativo**, evitando cualquier acoplamiento con las librerías propietarias de Supabase Auth.
- **Transaccionalidad y Consistencia:** Control estricto de variantes e inventario atómico en el catálogo de productos con soft-delete universal y auditoría de cambios.
- **Preparado para Escala:** Arquitectura orientada a eventos internos desacoplados (`PublicadorEvento`) con procesamiento asíncrono para notificaciones y correos.

---

## 2. Stack Tecnológico

| Componente | Tecnología | Versión | Descripción / Rol |
| :--- | :--- | :--- | :--- |
| **Lenguaje** | Java OpenJDK | 17 LTS | Plataforma base con soporte para Records, Sealed Classes y mejoras de GC. |
| **Framework Base** | Spring Boot | 3.3.4 | Framework empresarial para APIs REST reactivas y transaccionales. |
| **Seguridad** | Spring Security | 6.3.x | Filtros de seguridad stateless, protección de rutas y sesiones HTTP. |
| **Criptografía JWT** | JJWT (Java JWT) | 0.12.6 | Generación y validación criptográfica HMAC-SHA256 con API moderna. |
| **Persistencia ORM** | Hibernate / Spring Data JPA | 6.5.x | Mapeo objeto-relacional con validación estricta de esquema (`ddl-auto=validate`). |
| **Base de Datos** | PostgreSQL (Supabase) | 14+ / 15 | Motor relacional transaccional ACID con soporte para tipos `UUID` nativos. |
| **Pool de Conexiones** | HikariCP | 5.1.0 | Pool ultrarrápido con detección de fugas (*leak-detection*) y keep-alive. |
| **Mapeo de Objetos** | MapStruct | 1.5.5.Final | Generación de código de mapeo en tiempo de compilación (cero overhead de reflexión). |
| **Documentación API** | Springdoc OpenAPI / Swagger | 2.6.0 | Documentación interactiva en `/swagger-ui/index.html` con soporte BearerAuth. |
| **Variables de Entorno** | Java Dotenv | 5.2.2 | Carga de credenciales locales desde `.env` sin exponer secretos al repositorio. |
| **Utilidades de Código** | Project Lombok | 1.18.34 | Reducción de código boilerplate (`@Getter`, `@Setter`, `@Builder`). |

---

## 3. Arquitectura del Software

El proyecto implementa una **Arquitectura Hexagonal (Puertos y Adaptadores)** modular, combinada con el patrón de **Controladores Atómicos** (Single-Responsibility Principle: un controlador por cada acción HTTP).

```text
src/main/java/shopify/api/core/
├── config/                     # Configuraciones transversales del Framework (CORS, JPA, MVC, OpenAPI)
├── shared/                     # Kernel compartido por todos los módulos (sin dependencias de negocio)
│   ├── auditoria/              # Auditoría automática JPA (CreatedDate, CreatedBy, etc.)
│   ├── dto/                    # Contratos base (BaseInputDto, ConsultaPaginadaDto, EstadoOperacionDto, RespuestaApi)
│   ├── event/                  # Bus interno de eventos desacoplados (PublicadorEvento)
│   ├── exception/              # Manejo global de excepciones (@RestControllerAdvice)
│   ├── multitenancy/           # Contexto y extractor del tenant actual (TenantContextHolder, TenantInterceptor)
│   └── seguridad/              # Infraestructura JWT, SecurityConfig y SecurityFilterChain
└── modules/                    # Módulos de dominio verticalmente aislados
    ├── catalogo/               # Gestión de Productos, Variantes, Inventario y Categorías
    ├── tienda/                 # Gestión de Tiendas y resolución por Subdominio
    ├── usuario/                # Identidad, Roles, Autenticación, Activación y Recuperación
    └── notificacion/           # Consumidor de eventos para envío asíncrono de correos
```

# Anatomía Interna de Cada Módulo

Cada paquete en `modules/*` está estructurado en **3 capas concéntricas**:

---

## `domain/` (Núcleo Puro)

- Contiene modelos de entidad puros en Java (POJOs), libres de anotaciones de Spring o JPA.
- Define los **Puertos de Salida** (`*RepositorioPuerto.java`) como interfaces que describen qué necesita el dominio del exterior.

---

## `application/` (Casos de Uso y DTOs)

- **1 Caso de Uso = 1 Clase de Servicio**: Cada acción de negocio es una clase aislada (`CrearProductoCasoUso`, `AutenticarUsuarioCasoUso`), facilitando pruebas unitarias y evitando clases "Manager" gigantes.
- **DTOs de Entrada** (`application/dto/request/`) extendiendo de `BaseInputDto` o `PaginacionQueryInput`.
- **DTOs de Salida** (`application/dto/response/`) específicos para cada vista de datos.
- **Mapeadores de aplicación** generados por MapStruct.

---

## `infrastructure/` (Adaptadores Técnicos)

- **`persistencia/`**: Entidades JPA (`*Entidad.java`) anotadas con nombres PascalCase entrecomillados para respetar PostgreSQL, Repositorios de Spring Data y el Adaptador que implementa el puerto del dominio.
- **`rest/` (Controladores Atómicos)**: 1 clase de controlador por cada endpoint REST (`CrearProductoControlador`, `ListarProductosControlador`), garantizando que los cambios en un endpoint no afecten a los demás.

---

# 4. Descripción de Dependencias y su Utilidad

Las siguientes dependencias se encuentran declaradas en el archivo `pom.xml`:

| Dependencia | Utilidad |
|---|---|
| `spring-boot-starter-web` | Provee el contenedor Tomcat embebido, Jackson para serialización JSON y los controladores `@RestController`. |
| `spring-boot-starter-security` | Habilita el pipeline de seguridad, filtros interceptores y el componente `BCryptPasswordEncoder`. |
| `spring-boot-starter-data-jpa` | Conecta el framework con Hibernate y Spring Data para generar repositorios automáticos basados en interfaces. |
| `spring-boot-starter-validation` | Aplica validaciones declarativas basadas en Hibernate Validator (`@NotBlank`, `@Email`, `@Size`, `@DecimalMin`). |
| `spring-boot-starter-mail` | Permite la integración con servidores SMTP para el despacho de correos electrónicos. |
| `postgresql` (JDBC Driver) | Controlador oficial de conectividad de bajo nivel entre Java y PostgreSQL. |
| `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.6) | Biblioteca de vanguardia para firmar y validar tokens JWT criptográficamente seguros mediante HMAC-SHA256 sin usar APIs obsoletas. |
| `java-dotenv` | Carga las variables de entorno locales del archivo `.env` antes del arranque del contexto de Spring mediante `DotenvInitializer`. |
| `springdoc-openapi-starter-webmvc-ui` (2.6.0) | Genera la especificación OpenAPI 3 y la interfaz web interactiva de Swagger UI con soporte de autorización por encabezado Bearer. |
| `mapstruct` y `mapstruct-processor` | Procesador de anotaciones que compila código Java optimizado para mapear objetos de Dominio, Entidades JPA y DTOs en tiempo de compilación. |
| `lombok` | Genera constructores, getters, setters y patrones Builder en tiempo de compilación. |

---

# 5. Estrategia de Ramas en Git (GitFlow)

El proyecto sigue una convención estricta de branching para garantizar orden, trazabilidad y evitar colisiones de código:

```text
┌── feature/auth (Usuarios, Roles, JWT, Email) ──┐
                  │                                                │
main ────────── develop ── feature/catalogo (Categorías, Productos) ── develop ──> main (Releases)
                  │                                                │
                  └── feature/tienda (Tiendas, Multi-Tenancy) ─────┘
```

## Convención de Ramas

- **`main`**: Rama de producción. Contiene código auditado, probado y listo para despliegue.
- **`develop`**: Rama de integración continua donde convergen todos los módulos finalizados.
- **`feature/<modulo>`**: Ramas individuales de trabajo:
    - `feature/auth`: Identidad, registro, activación por correo, login JWT y recuperación de contraseñas.
    - `feature/catalogo`: Entidades y casos de uso de categorías, productos, variantes e inventario.
    - `feature/tienda`: Entidades y casos de uso de multi-tenancy y configuración de tiendas.

---

# 6. Configuración y Despliegue Local

## Requisitos Previos

- **Java Development Kit (JDK)**: Versión 17 o superior instalada (`java -version`).
- **Git**: Para clonar el repositorio.
- **Conexión a PostgreSQL / Supabase**: Instancia de base de datos activa con las tablas ejecutadas mediante el script DDL.

## Paso 1: Clonar el Repositorio

```powershell
git clone <URL_DEL_REPOSITORIO>
cd ShopifyClon-Api-Core
```

## Paso 2: Configurar las Variables de Entorno (`.env`)

En la raíz del proyecto, crea un archivo llamado `.env` con tus propias credenciales de conexión a PostgreSQL/Supabase y tus claves JWT. **No incluyas credenciales reales en el repositorio.** A continuación se muestra un ejemplo genérico de las variables que debes definir (reemplaza los valores por los tuyos):

```properties
# Conexión JDBC PostgreSQL
DB_HOST=tu_host_de_base_de_datos
DB_PORT=5432
DB_NAME=tu_base_de_datos
DB_USERNAME=tu_usuario
DB_PASSWORD=tu_contraseña
DB_SSL_MODE=require

# Pool HikariCP
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
DB_POOL_CONNECTION_TIMEOUT=20000
DB_POOL_IDLE_TIMEOUT=300000
DB_POOL_MAX_LIFETIME=1800000

# Seguridad y token JWT (HMAC-SHA256 mínimo 256 bits)
JWT_SECRET_KEY=tu_clave_secreta_jwt
JWT_EXPIRATION_TIME=86400000

# Claves auxiliares Supabase (si aplica)
SUPABASE_URL=tu_url_de_supabase
SUPABASE_PUBLISHABLE_KEY=tu_clave_publica
SUPABASE_SECRET_KEY=tu_clave_secreta
```

## Paso 3: Compilar y Ejecutar la Aplicación

### En Windows (PowerShell)

```powershell
# 1. Limpiar y compilar el proyecto
.\mvnw clean compile

# 2. Iniciar el servidor Spring Boot
.\mvnw spring-boot:run
```

### En Linux / macOS (Bash)

```bash
./mvnw clean compile
./mvnw spring-boot:run
```

El servidor iniciará en el puerto **8080** bajo el prefijo global `/api/v1`:

```text
http://localhost:8080/api/v1
```

---

# 7. Documentación Interactiva (Swagger UI)

Una vez iniciado el servidor, puedes explorar y probar todos los endpoints en tiempo real accediendo a:

👉 **URL de Swagger UI**:

```text
http://localhost:8080/api/v1/swagger-ui/index.html
```

👉 **Especificación OpenAPI (JSON)**:

```text
http://localhost:8080/api/v1/v3/api-docs
```

## Autenticación en Swagger

1. Ejecuta el endpoint `POST /usuarios/login` para obtener un `token_acceso`.
2. Haz clic en el botón superior verde **Authorize**.
3. Ingresa el token con el formato: `Bearer <tu_token_aqui>` y presiona **Authorize**.
4. Todos los endpoints protegidos se ejecutarán con tus credenciales.

---

# 8. Catálogo de Endpoints del Entregable 1

## Módulo: Usuarios y Seguridad (`/api/v1/usuarios`)

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/usuarios/registro` | Registrar nueva cuenta (nace inactiva, emite token de activación). |
| GET | `/usuarios/activar-cuenta?token={token}` | Activa la cuenta de usuario mediante el token recibido por correo. |
| POST | `/usuarios/login` | Inicio de sesión con credenciales; emite JWT Bearer. |
| POST | `/usuarios/recuperar-clave/solicitar` | Solicita código OTP de 6 dígitos para recuperación de clave. |
| POST | `/usuarios/recuperar-clave/confirmar` | Valida el código OTP y actualiza la contraseña. |
| GET | `/usuarios/perfil` | (Protegido) Obtiene los datos del usuario autenticado actual. |

## Módulo: Tiendas Multi-Tenant (`/api/v1/tiendas`)

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/tiendas` | (Protegido) Crea una nueva tienda asociada al usuario autenticado como propietario. |
| GET | `/tiendas/{subdominio}` | (Público) Consulta la tienda y su moneda por su subdominio web. |
| GET | `/tiendas/mis-tiendas` | (Protegido) Lista todas las tiendas administradas por el usuario logueado. |
| PUT | `/tiendas/{id}` | (Protegido) Actualiza los datos y dominio personalizado de la tienda. |

## Módulo: Catálogo - Categorías (`/api/v1/catalogo/categorias`)

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/catalogo/categorias` | (Protegido) Crea una nueva categoría para la tienda activa. |
| GET | `/catalogo/categorias` | (Público) Lista categorías con paginación y búsqueda por nombre. |
| GET | `/catalogo/categorias/{id}` | (Público) Detalle de una categoría específica. |
| PUT | `/catalogo/categorias/{id}` | (Protegido) Actualiza nombre, descripción o slug. |
| DELETE | `/catalogo/categorias/{id}` | (Protegido) Eliminación lógica (soft-delete). |

## Módulo: Catálogo - Productos e Inventario (`/api/v1/catalogo/productos`)

| Método | Endpoint | Descripción |
|---|---|---|
| POST | `/catalogo/productos` | (Protegido) Crea un producto con su variante por defecto, stock inicial, categoría e imagen principal de forma atómica. Requiere encabezado `X-Tienda-Id`. |
| GET | `/catalogo/productos` | (Público) Listado paginado con cálculo en vivo de `precioDesde`, `stockTotal` y `foto principal`. |
| GET | `/catalogo/productos/{id}` | (Público) Detalle del producto con su lista completa de variantes y stock individual. |
| PUT | `/catalogo/productos/{id}` | (Protegido) Actualiza título, descripción y proveedor del producto. |
| DELETE | `/catalogo/productos/{id}` | (Protegido) Eliminación lógica en cascada del producto y sus variantes. |
| PATCH | `/catalogo/variantes/{varianteId}/inventario` | (Protegido) Ajuste directo de stock disponible (`CantidadDisponible`). |

---

# 9. Formato Estándar de Respuestas JSON

Todas las respuestas de la API están normalizadas en formato `snake_case` siguiendo el patrón **Envelope unificado**:

## Respuesta Exitosa Simple (200 OK / 201 Created)

```json
{
  "exito": true,
  "codigo_estado": 201,
  "mensaje": "Producto creado exitosamente con variante por defecto e inventario",
  "datos": {
    "exito": true,
    "codigo_estado": 201,
    "mensaje": "Producto creado exitosamente con variante por defecto e inventario",
    "id": "7b8f9e02-1234-4567-89ab-cdef01234567",
    "codigo": "chaqueta-overcoat-lana",
    "marca_tiempo": "2026-09-22T23:00:00Z"
  },
  "marca_tiempo": "2026-09-22T23:00:00Z"
}
```

## Respuesta Exitosa Paginada (200 OK)

```json
{
  "exito": true,
  "codigo_estado": 200,
  "mensaje": "Productos recuperados exitosamente",
  "datos": {
    "datos": [
      {
        "id": "7b8f9e02-1234-4567-89ab-cdef01234567",
        "titulo": "Chaqueta Overcoat Lana",
        "slug": "chaqueta-overcoat-lana",
        "proveedor": "Zara",
        "esta_publicado": true,
        "precio_desde": 189.00,
        "stock_total": 45,
        "imagen_principal": "https://cdn.shopifyclon.com/foto1.jpg",
        "fecha_creacion": "2026-09-22T22:30:00Z"
      }
    ],
    "total": 1,
    "pagina_actual": 0,
    "tamano_pagina": 10,
    "total_paginas": 1,
    "tiene_mas_paginas": false,
    "marca_tiempo": "2026-09-22T23:00:00Z"
  },
  "marca_tiempo": "2026-09-22T23:00:00Z"
}
```

## Respuesta de Error de Validación (422 Unprocessable Entity)

```json
{
  "exito": false,
  "codigo_estado": 422,
  "mensaje": "Uno o más campos no cumplen con el formato requerido",
  "error": "Fallo de validación de datos",
  "ruta": "/api/v1/catalogo/productos",
  "validaciones": {
    "precio": "El precio no puede ser negativo",
    "sku": "El SKU es obligatorio"
  },
  "marca_tiempo": "2026-09-22T23:00:00Z"
}
```

---

# 10. Datos Semilla (Seed Data para Pruebas Iniciales)

Si tu base de datos en Supabase está vacía, puedes ejecutar el siguiente bloque SQL en el **SQL Editor** de Supabase para contar de inmediato con los roles del sistema y una tienda de prueba para tus pruebas en Postman:

```sql
-- 1. Roles del Sistema
INSERT INTO "Roles" ("Id", "Nombre", "Codigo", "Descripcion")
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Administrador de Tienda', 'ADMIN_TIENDA', 'Acceso total a la administración de la tienda'),
    ('a0000000-0000-0000-0000-000000000002', 'Cliente Comprador', 'CLIENTE', 'Acceso a catálogo, carrito y compras')
ON CONFLICT ("Codigo") DO NOTHING;
```

---

# 11. Autores y Licencia

- **Proyecto Académico**: Clon Shopify API Core (Desarrollo Fullstack).
- **Edición**: 2026.
- **Licencia**: Apache 2.0. Libre para fines educativos y desarrollo de software.
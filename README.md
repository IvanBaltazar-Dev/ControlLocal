# Sistema de Gestión de Alquiler de Locales Comerciales

Sistema backend para gestionar el proceso de alquiler de propiedades comerciales.

Este proyecto modela el flujo de trabajo de una inmobiliaria enfocada en alquileres comerciales, incluyendo la gestión de propiedades, interacción con clientes y solicitudes de alquiler.

---

## 🚀 Tecnologías

* Java
* Maven
* (Planeado) Spring Boot
* Programación Orientada a Objetos

---

## 📦 Funcionalidades

* Modelo de dominio para una inmobiliaria comercial
* Entidades como:

  * Broker
  * Agente Inmobiliario
  * Local Comercial
  * Propietario
  * Solicitud de Alquiler
  * Cliente Interesado
* Modelado de procesos de negocio:

  * Captación de propiedades
  * Visitas
  * Evaluación de solicitudes
  * Gestión de documentos

---

## 📁 Estructura del Proyecto

```
commercial-rental-brokerage-system/
│
├── src/main/java/com/commercialbrokerage/model
│   └── Entidades del dominio
│
├── docs/diagrams
│   └── Diagramas del sistema (si existen)
│
├── pom.xml
└── .gitignore
```

---

## 🛠️ Cómo empezar

### 1. Clonar el repositorio

```bash
git clone https://github.com/IvanBaltazar-Dev/commercial-rental-brokerage-system.git
cd commercial-rental-brokerage-system
```

---

### 2. Compilar el proyecto

```bash
mvn clean install
```

---

### 3. Abrir en un IDE

Puedes usar:

* IntelliJ IDEA
* NetBeans
* Eclipse

---

## 🤝 Cómo contribuir
### Pasos:

1. Haz un fork del repositorio
2. Crea una nueva rama:

```bash
git checkout -b feature/nombre-de-tu-feature
```

3. Realiza tus cambios
4. Haz commit usando convenciones:

```bash
git commit -m "feat: agregar nueva funcionalidad"
```

5. Sube tu rama:

```bash
git push origin feature/nombre-de-tu-feature
```

6. Abre un Pull Request

---

## 🧠 Convención de Commits

Este proyecto usa **Conventional Commits**:

* `feat:` Nueva funcionalidad
* `fix:` Corrección de errores
* `refactor:` Mejora de código
* `chore:` Configuración/mantenimiento
* `docs:` Documentación

---

## 🔐 Seguridad

* NO subir datos sensibles (contraseñas, API keys, etc.)
* Usar variables de entorno o archivos de ejemplo

---

## ⭐ Apoyo

Si te gusta este proyecto, dale una ⭐ en GitHub

# 🏢 ControlLocal

Sistema backend para la **gestión, trazabilidad y control del proceso comercial de alquiler de locales comerciales** en una corredora inmobiliaria.

---

## 📌 Descripción

**ControlLocal** centraliza la información del proceso de corretaje, permitiendo registrar, consultar y dar seguimiento a captaciones, interesados, visitas, solicitudes y evaluaciones.

El sistema resuelve problemas como:

- Información dispersa (Excel, llamadas, mensajes)
- Falta de trazabilidad de decisiones
- Dificultad de supervisión del proceso comercial

👉 Enfoque principal: **control + trazabilidad operativa**

---

## 🎯 Objetivo

Gestionar de forma estructurada el proceso comercial de alquiler, desde la captación del local hasta la evaluación de la solicitud.

---

## 🧩 Alcance

Incluye la gestión de:

- Usuarios internos (Broker / Agente)
- Propietarios
- Locales comerciales
- Captaciones
- Clientes interesados
- Interacciones
- Visitas
- Solicitudes de alquiler
- Documentos
- Evaluaciones
- Reportes

---

## 🔄 Flujo del sistema

1. Registro de locales y propietarios  
2. Captación de propiedades  
3. Asignación de agente  
4. Registro de interesados  
5. Interacciones comerciales  
6. Visitas  
7. Solicitudes  
8. Evaluación final  

---

## 👥 Roles

- **Broker:** administración, supervisión y control  
- **Agente:** ejecución operativa del proceso comercial  

---

## 🧠 Modelo de dominio

Principales entidades:

- UsuarioInterno
- Broker
- AgenteInmobiliario
- Propietario
- LocalComercial
- Captacion (eje del sistema)
- ClienteInteresado
- ConsultaInteres
- Visita
- SolicitudAlquiler
- DocumentoSolicitud
- EvaluacionSolicitud
- ReasignacionCaptacion

---

## ⚙️ Tecnologías

- Java  
- Maven  
- Programación Orientada a Objetos  
- JDBC (próximo)  
- Spring Boot (planeado)  

---

## 📁 Estructura

```text
ControlLocal/
│
├── src/main/java/com/commercialbrokerage/model
│   └── Entidades del dominio
│
├── docs/
│   └── Diagramas
│
├── pom.xml
└── README.md

## 🛠️ Cómo empezar

### 1. Clonar el repositorio

```bash
git clone https://github.com/IvanBaltazar-Dev/ControlLocal.git
cd ControlLocal
```

---

### 2. Compilar el proyecto

```bash
mvn clean install
```

> Maven gestionará automáticamente las dependencias y generará los artefactos del proyecto en la carpeta `target/`.

---

### 3. Abrir en un IDE

Se recomienda utilizar un IDE con soporte para proyectos Maven:

- IntelliJ IDEA (recomendado)
- NetBeans
- Eclipse

> Al abrir el proyecto, el IDE detectará automáticamente el `pom.xml` y configurará las dependencias.

---

## 🤝 Cómo contribuir

### Flujo de trabajo

1. Realiza un fork del repositorio  
2. Crea una nueva rama:

```bash
git checkout -b feature/nombre-de-tu-feature
```

3. Implementa tus cambios  
4. Realiza commits siguiendo la convención:

```bash
git commit -m "feat: descripción clara de la funcionalidad"
```

5. Sube tu rama:

```bash
git push origin feature/nombre-de-tu-feature
```

6. Abre un Pull Request hacia la rama principal  

---

## 🧠 Convención de Commits

Este proyecto utiliza **Conventional Commits**:

- `feat:` nueva funcionalidad  
- `fix:` corrección de errores  
- `refactor:` mejora interna del código  
- `docs:` documentación  
- `chore:` mantenimiento o configuración  

---

## 🔐 Seguridad

- No subir credenciales, contraseñas ni información sensible  
- Usar variables de entorno para configuraciones críticas  
- Mantener archivos sensibles fuera del control de versiones  

---

# 👑 Reino Pixel: MMORPG Cloud-Distributed Ecosystem

<p align="center">
  <img src="https://img.shields.io/badge/Status-Production%20Ready-brightgreen?style=for-the-badge&logo=rocket" alt="Status">
  <img src="https://img.shields.io/badge/Architecture-Cloud%20Native-blue?style=for-the-badge&logo=cloud-foundry" alt="Architecture">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge&logo=opensourceinitiative" alt="License">
  <img src="https://img.shields.io/badge/Security-Firebase%20Secured-orange?style=for-the-badge&logo=firebase" alt="Security">
</p>

> **Reino Pixel** es un motor y ecosistema MMORPG de alto rendimiento diseñado desde cero para soportar concurrencia masiva en tiempo real. Combina la potencia gráfica y fluidez de un cliente moderno con un núcleo de servidor backend desacoplado y blindado en la nube.

---

## ⚡ ¿De qué va este proyecto?

Este repositorio alberga la arquitectura completa de un juego multijugador masivo en línea (MMORPG) llamado **Reino Pixel**. Su diseño separa de manera estricta la **Capa Cliente (Godot Engine)** de la **Capa Servidor (Java WebSockets)** y los **Servicios de Identidad en la Nube (Firebase Auth)**, garantizando una escalabilidad infinita, seguridad de nivel bancario y una experiencia de usuario ultra fluida con pantallas de carga dinámicas y bloqueo de hilos de interfaz.

---

## 🛠️ Stack Tecnológico y Versiones

| Componente | Tecnología | Versión | Propósito |
| :--- | :--- | :--- | :--- |
| **Cliente** | Godot Engine | `4.x Stable` | Interfaz gráfica, control de nodos, físicas 3D y renderizado. |
| **Servidor Core** | Java / Maven | `JDK 17+` | Servidor de red asíncrono basado en WebSockets. |
| **Librería de Red** | Java-WebSocket | `1.5.4` | Protocolo de comunicación bidireccional en tiempo real. |
| **Autenticación** | Firebase Admin SDK | `9.2.0` | Gestión de usuarios, cifrado y validación en la nube 24/7. |
| **Base de Datos** | Firebase Cloud / SQLite | `Cloud / Local` | Persistencia segura de credenciales y perfiles. |

---

## 🚀 Características Principales

* **🔐 Autenticación Cloud Dual:** Sistema de inicio de sesión (`AUTH:`) y registro (`REG:`) integrado directamente con Firebase Auth.
* **🛡️ Seguridad de Hilos y UI:** Bloqueo inteligente de inputs de texto y botones durante las peticiones asíncronas para evitar estados de carrera o múltiples envíos.
* **📊 Barra de Progreso Dinámica:** Feedback visual en tiempo real con avance fluido hasta el 100% al completarse la sincronización de red.
* **🌐 Arquitectura Desacoplada:** Esquemas de base de datos SQL totalmente independientes del código de red en Java.
* **⚡ Baja Latencia:** Sincronización de coordenadas (`POS:`) optimizada mediante paquetes ligeros por WebSockets.

---

## 🔮 Datos Curiosos del Desarrollo

* 🧠 **Cero pérdidas de memoria:** El servidor gestiona las sesiones activas mediante un `HashMap` optimizado que limpia automáticamente las conexiones huérfanas al cerrar el socket.
* ☁️ **Independencia de hardware:** Gracias a la abstracción del Admin SDK, el servidor Java puede migrarse de un entorno local a un VPS en la nube (como Oracle Cloud o AWS) cambiando únicamente las variables de entorno de las credenciales privadas.
* 🎨 **UX resiliente:** Si un inicio de sesión falla por credenciales inválidas, el cliente reactiva automáticamente todos los controles de la interfaz en menos de un milisegundo para permitir un nuevo intento.

---

<p align="center">
  <i>Desarrollado con arquitectura limpia, pasión por el código comentado y visión de futuro. 🚀</i>
</p>
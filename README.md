# ProjectAmaterasu

**ProjectAmaterasu** is a modular project designed to integrate several components for seamless functionality across different platforms. The project consists of three main components:

## Components

### 1. **amaterasu-pg**

- **Description**: A `docker-compose.yml` setup for a Postgres database that supports the application.

### 2. **amaterasu-rest**

- **Description**: This service is the RESTful API layer that provides endpoints for communication between the backend and frontend or external services.
- **Key Features**:
  - CRUD operations for managing resources.
  - Authentication and authorization mechanisms.
  - JSON-based data exchange.

### 3. **amaterasu-web**

- **Description**: The Angular-based user interface, offering a front-facing application for end users.
- **Key Features**:
  - Responsive design for various devices.
  - Integration with `amaterasu-rest` to fetch dynamic content.

## Installation

To get started with **ProjectAmaterasu**, follow these steps:

1. **Download the docker-compose.yml**:

```bash
wget https://raw.githubusercontent.com/infernokun/ProjectAmaterasu/refs/heads/main/docker-compose.yml
```

2. **Set up environment variables**:

   - You can create `.env` files in the respective component directories or define environment variables directly in the `docker-compose.yml` file.

3. **Run the application**:  
   **Ensure you have Docker and Docker Compose installed.**  
   Then, run the following command to start all the services:

```bash
docker-compose up -d
```

This will bring up the following services:

- **amaterasu-web**: The front-end Angular application.
- **amaterasu-db**: The PostgreSQL database.
- **amaterasu-rest**: The RESTful API layer.

## Usage

**Work In Progress**  
Details on using the application and accessing the services will be added soon.

## Docker Compose Setup

The `docker-compose.yml` file is configured to set up all services. Here is a breakdown of the services included in the file:

### **amaterasu-web** (Frontend)

- **Description**: The Angular application that serves as the front-end UI for end users.
- **Ports**:
  - Exposes port 80 on the container, mapped to port 8764 on the host.
- **Environment Variables**:
  - `BASE_URL`: The base URL for the web application.
  - `REST_URL`: The URL to the API layer (for integration).
- **Dependencies**:
  - Depends on `amaterasu-db` being healthy before starting.

### **amaterasu-db** (Database)

- **Description**: A PostgreSQL database to store application data.
- **Ports**:
  - Exposes the default PostgreSQL port `5432`.
- **Environment Variables**:
  - `POSTGRES_USER`: The PostgreSQL user (default: `amaterasu`).
  - `POSTGRES_PASSWORD`: The PostgreSQL password (default: `amaterasu`).
  - `POSTGRES_DB`: The PostgreSQL database name (default: `amaterasu`).
- **Volumes**:
  - Persist data using the volume `./amaterasu-db:/var/lib/postgresql/data`.
- **Healthcheck**:
  - The healthcheck ensures that PostgreSQL is up and ready before other services attempt to connect.

### **amaterasu-rest** (Backend)

- **Description**: The RESTful API layer that interacts with the database and the front-end.
- **Ports**:
  - Exposes port `8080` on the container, mapped to port `8765` on the host.
- **Environment Variables**:
  - `DOCKER_COMPOSE_PATH`: Path to the Docker Compose directory (default: `/var/tmp/amaterasu`).
  - `DB_IP`: The IP address or hostname for the PostgreSQL database.
  - `DB_NAME`: The PostgreSQL database name.
  - `DB_USER`: The PostgreSQL user.
  - `DB_PASS`: The PostgreSQL password.
  - `DB_PORT`: The PostgreSQL port (default: `5432`).
  - `ENCRYPTION_KEY`: A randomly generated encryption key (using OpenSSL if not set).
- **Dependencies**:
  - Depends on `amaterasu-db` being ready before starting.

## Troubleshooting

- Ensure that Docker and Docker Compose are properly installed on your machine.
- Check the logs of any service if it doesn't start correctly:

```bash
docker-compose logs [service_name]
```

This will display logs for a specific service, such as `amaterasu-web`, `amaterasu-db`, or `amaterasu-rest`.

# RSA stuff
openssl genpkey -algorithm RSA -out private.key -pkeyopt rsa_keygen_bits:409
openssl rsa -pubout -in private.key -out public.key
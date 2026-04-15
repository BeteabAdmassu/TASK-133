# [Project Name]

Brief description of the application, its primary purpose, and the specific domain it covers.

## Architecture & Tech Stack

* **Frontend:** [e.g., React 18, TailwindCSS]
* **Backend:** [e.g., Node.js, Express, Python FastAPI]
* **Database:** [e.g., PostgreSQL 15, MongoDB]
* **Containerization:** Docker & Docker Compose

## Project Structure

```text
.
├── backend/                # Backend source code and Dockerfile
├── frontend/               # Frontend source code and Dockerfile
├── db/                     # Database initialization scripts/migrations
├── tests/                  # Integration and E2E tests
├── docker-compose.yml      # Multi-container orchestration
├── run_tests.sh            # Standardized test execution script
└── README.md               # Project documentation
```

## Prerequisites

This project runs entirely within containers. You need:
* [Docker](https://docs.docker.com/get-docker/)
* [Docker Compose](https://docs.docker.com/compose/install/)

## Running the Application

1. **Build and Start Containers:**
   ```bash
   docker-compose up --build -d
   ```

2. **Access the App:**
   * Frontend: `http://localhost:[PORT]`
   * Backend API: `http://localhost:[PORT]/api`
   * API Documentation (if applicable): `http://localhost:[PORT]/docs`

3. **Stop the Application:**
   ```bash
   docker-compose down -v
   ```

## Environment Variables

All environment variables have defaults in `docker-compose.yml`.
No `.env` file is needed — Docker injects defaults at container start.

## Testing

All unit, integration, and E2E tests are executed via a single standardized shell script. This script automatically handles container orchestration for the test environment.

```bash
chmod +x run_tests.sh
./run_tests.sh
```

The `run_tests.sh` script outputs a standard exit code (`0` for success, non-zero for failure).

## Seeded Credentials

The database is pre-seeded with the following test users on startup:

| Role | Email | Password | Notes |
| :--- | :--- | :--- | :--- |
| **Admin** | `admin@example.com` | `AdminTest123!` | Full access to all system modules |
| **User** | `user@example.com` | `UserTest123!` | Standard user with default permissions |

## API Documentation

See `../docs/api-spec.md` for full API specification.

## Architecture

See `../docs/design.md` for architecture decisions.

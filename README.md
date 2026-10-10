# Employee Leave Management System

A Spring Boot REST backend for managing employee leave. It has three roles: **Admin**, **Manager** and **Employee**.

**Stack:** Java 21 · Spring Boot 4.1 · Spring Data JPA / Hibernate · PostgreSQL · Spring Security (HTTP Basic) · Bean Validation · Maven · JUnit 5 / Mockito / H2 (tests)

---

## 1. Prerequisites

| Tool | Notes |
|---|---|
| **JDK 21** | e.g. [Eclipse Temurin 21](https://adoptium.net/). Set `JAVA_HOME` to it (`java -version` should print 21). |
| **PostgreSQL server** (14+) | e.g. the [EDB Windows installer](https://www.postgresql.org/download/windows/). Remember the `postgres` password and keep port `5432`. |
| **DB viewer** (DBeaver or similar) | Used to create the database and inspect the tables. It is a client only, so it still needs the PostgreSQL server above. |

You don't need to install Maven, because the project ships with the Maven Wrapper (`mvnw` / `mvnw.cmd`).

## 2. Create the database (using the DB viewer)

1. **New Database Connection → PostgreSQL**. Use host `localhost`, port `5432`, database `postgres`, user `postgres`, and your password. Click *Test Connection*, then *Finish*.
2. Open an SQL editor on that connection and run:
   ```sql
   CREATE DATABASE leave_management;
   ```
3. Refresh the connection. Once the app has started (step 3), the tables appear under `leave_management → Schemas → public → Tables`.

You don't need to create any tables. Hibernate creates them on startup (`ddl-auto: update`).

## 3. Run the application

Configuration comes from environment variables. Each one has a default:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/leave_management` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `SERVER_PORT` | `8081` (8080 is used by the local Oracle listener) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@company.com` / `admin123` |

**Windows (PowerShell)**
```powershell
$env:DB_PASSWORD = "your-postgres-password"
.\mvnw.cmd spring-boot:run
```

**Linux / macOS / Git Bash**
```bash
DB_PASSWORD=your-postgres-password ./mvnw spring-boot:run
```

On first start, a default **admin** account is created (`admin@company.com` / `admin123`). Change it in real deployments.

## 4. Run the tests

```bash
./mvnw test        # Windows: .\mvnw.cmd test
```

Tests use an in-memory **H2** database in PostgreSQL mode, so PostgreSQL doesn't need to be running.

| Test class | What it covers |
|---|---|
| `LeaveRequestServiceTest` | All leave business rules, using Mockito and a fixed clock |
| `EmployeeServiceTest`, `LeaveTypeServiceTest`, `LeaveBalanceServiceTest` | Admin operations: duplicates, manager assignment, reporting cycles, soft delete |
| `ControllerSecurityAndValidationTest` | `@WebMvcTest`: role restrictions (401/403), DTO validation (400), error mapping (404/409) |
| `LeaveRequestRepositoryTest` | `@DataJpaTest`: overlap query edge cases, pending-days sum, team query |
| `LeaveFlowIntegrationTest` | End-to-end over HTTP Basic: setup → apply → approve → cancel → reject |

---

## 5. API

Every endpoint uses **HTTP Basic** authentication. The username is the employee's email.

### Admin: `/api/admin` (role `ADMIN`)

| Method | Path | Description |
|---|---|---|
| POST | `/employees` | Create an employee `{name, email, password, role, managerId?}` |
| GET | `/employees` | List employees |
| GET | `/employees/{id}` | Get an employee |
| PUT | `/employees/{id}` | Update `{name, email, password?, role, active}` |
| DELETE | `/employees/{id}` | Deactivate (soft delete) |
| PUT | `/employees/{id}/manager` | Assign a manager `{managerId}` |
| PUT | `/employees/{id}/leave-balances` | Create or update a balance `{leaveTypeId, year, totalDays}` |
| GET | `/employees/{id}/leave-balances?year=` | View an employee's balances |
| POST | `/leave-types` | Create a leave type `{name, description?, defaultDays, active?}` |
| GET | `/leave-types`, `/leave-types/{id}` | List or get leave types |
| PUT | `/leave-types/{id}` | Update a leave type |
| DELETE | `/leave-types/{id}` | Deactivate a leave type |

### Employee: `/api/employees/me` (roles `EMPLOYEE`, `MANAGER`)

| Method | Path | Description |
|---|---|---|
| GET | `/leave-balances?year=` | My balances (defaults to the current year) |
| POST | `/leave-requests` | Apply `{leaveTypeId, startDate, endDate, reason?}` (dates are `yyyy-MM-dd`) |
| GET | `/leave-requests` | My leave requests |
| PUT | `/leave-requests/{id}/cancel` | Cancel one of my requests |

### Manager: `/api/manager/leave-requests` (role `MANAGER`)

| Method | Path | Description |
|---|---|---|
| GET | `?status=PENDING` | My direct reports' requests (`status` is optional) |
| PUT | `/{id}/approve` | Approve a request |
| PUT | `/{id}/reject` | Reject a request `{reason}` (a reason is required) |

### Example walkthrough (curl)

```bash
A="-u admin@company.com:admin123 -H Content-Type:application/json"

curl $A -X POST localhost:8081/api/admin/leave-types -d '{"name":"Annual","defaultDays":20}'
curl $A -X POST localhost:8081/api/admin/employees -d '{"name":"Mona","email":"mona@company.com","password":"secret1","role":"MANAGER"}'
curl $A -X POST localhost:8081/api/admin/employees -d '{"name":"Eve","email":"eve@company.com","password":"secret1","role":"EMPLOYEE"}'
curl $A -X PUT  localhost:8081/api/admin/employees/3/manager -d '{"managerId":2}'
curl $A -X PUT  localhost:8081/api/admin/employees/3/leave-balances -d '{"leaveTypeId":1,"year":2026,"totalDays":10}'

# Employee applies (pick future weekdays)
curl -u eve@company.com:secret1 -H Content-Type:application/json -X POST \
  localhost:8081/api/employees/me/leave-requests -d '{"leaveTypeId":1,"startDate":"2026-11-02","endDate":"2026-11-06","reason":"Vacation"}'

# Manager approves (balance drops by 5)
curl -u mona@company.com:secret1 -X PUT localhost:8081/api/manager/leave-requests/1/approve
curl -u eve@company.com:secret1 localhost:8081/api/employees/me/leave-balances
```

### Error format

Every error returns the same JSON shape:

```json
{
  "timestamp": "2026-10-09T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient leave balance: requested 5 day(s), available 2",
  "path": "/api/employees/me/leave-requests",
  "fieldErrors": null
}
```

| Status | When |
|---|---|
| 400 | DTO validation failure (`fieldErrors` is filled in), malformed JSON or dates, or a business rule violation (past date, overlap, insufficient balance, invalid status change) |
| 401 | Missing or wrong credentials |
| 403 | Wrong role, or not the employee's manager / not the owner of the request |
| 404 | Unknown employee, leave type or request |
| 409 | Duplicate email or leave type name, or a concurrent modification (optimistic locking) |

---

## 6. Business rules

| Rule | Implementation |
|---|---|
| No leave for past dates | `startDate` must be today or later (today is allowed). `endDate` must be on or after `startDate`. |
| Insufficient balance | Requested days must be ≤ `total − used − days held by pending requests`. Pending requests reserve days, so an employee can't queue more leave than they have. Balance is checked again at approval. |
| No overlapping requests | Rejected if the dates intersect an existing **PENDING** or **APPROVED** request (inclusive range check). |
| Approval reduces the balance | `usedDays += days`, in the same transaction as the status change. |
| Cancelling approved leave restores the balance | `usedDays -= days`. |
| Rejection doesn't touch the balance | Only the status and reason change. |
| Only the employee's manager decides | `request.employee.manager == current user`, otherwise 403. |

## 7. Assumptions and design decisions

- **Authentication:** HTTP Basic with BCrypt-hashed passwords, stateless, no sessions. This keeps things simple for an assessment. A JWT/OAuth2 resource server could replace it without changing the controllers.
- **Roles:** each user has exactly one role. A **manager is also an employee**: they can apply for their own leave through `/api/employees/me/**`, and *their* manager approves it. Admins only administer and don't apply for leave.
- **Hierarchy:** an employee has at most one direct manager, and that manager must have the `MANAGER` role and be active. Self-assignment and reporting cycles are rejected. Employees must have a manager before they can apply, otherwise nobody could approve.
- **Day counting:** leave is counted in **working days (Mon–Fri)** and includes both start and end dates. Public holidays and half days aren't modelled. A range with no working days (e.g. only a weekend) is rejected.
- **Balances are per employee, leave type and calendar year.** The year comes from the start date, and a request can't span two years (split it into two). Admins configure `totalDays`, and it can't be set below the days already used. `defaultDays` on a leave type is informational only.
- **When the balance changes:** days are deducted on **approval**, not on application. Pending requests still reserve days during the balance check at application time.
- **Cancellation:** pending requests can always be cancelled. Approved requests can only be cancelled **before their start date**, because leave that has started has been (partly) taken. Rejected or cancelled requests can't be cancelled.
- **Deleting is a soft delete** for employees and leave types (`active = false`) to keep leave history intact. Inactive employees can't log in, and inactive leave types can't be used for new requests or balances. A manager with active reports can't be deactivated or demoted until their reports are reassigned.
- **Concurrency:** `LeaveRequest` and `LeaveBalance` use `@Version` optimistic locking. Two concurrent approvals or cancellations can't corrupt a balance; the loser gets a 409.
- **Schema management:** `ddl-auto: update` for convenience. A production setup would use Flyway or Liquibase migrations.
- **Time:** a `java.time.Clock` bean decides what "today" is, so the tests can fix the date.

## 8. Project structure

```
src/main/java/com/wexl/comapny/management
├── config/       AppConfig (Clock), DataSeeder (default admin)
├── controller/   AdminController, EmployeeController, ManagerController
├── dto/          Request/response records with validation annotations
├── entity/       Employee, LeaveType, LeaveBalance, LeaveRequest, Role, LeaveStatus
├── exception/    Domain exceptions, ErrorResponse, GlobalExceptionHandler
├── mapper/       Entity → DTO mapping
├── repository/   Spring Data JPA repositories
├── security/     SecurityConfig, CustomUserDetailsService, CurrentUserService
└── service/      EmployeeService, LeaveTypeService, LeaveBalanceService, LeaveRequestService
```

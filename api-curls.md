# Leave Management API – curl commands

Import any command into Postman: **Import → Raw text** → paste → **Continue → Import**
(`-u user:password` becomes Postman Basic Auth automatically).

- Base URL: `http://localhost:8081` (start the app first)
- Existing data: admin = 1, Mona (manager) = 2, Eve (employee) = 3, leave type "Annual" = 1
- Leave request ids: change `2` in cancel/approve/reject URLs to the id returned when you apply
- Approve, reject and cancel only work on a PENDING request; use non-overlapping dates to test each
- Only working days (Mon–Fri) count against the balance

---

## Admin (`admin@company.com` / `admin123`)

### Employees

Create employee
```bash
curl --location 'http://localhost:8081/api/admin/employees' -u 'admin@company.com:admin123' --header 'Content-Type: application/json' --data-raw '{"name":"Raj Kumar","email":"raj@company.com","password":"secret1","role":"EMPLOYEE","managerId":2}'
```

List employees
```bash
curl --location 'http://localhost:8081/api/admin/employees' -u 'admin@company.com:admin123'
```

Get employee
```bash
curl --location 'http://localhost:8081/api/admin/employees/3' -u 'admin@company.com:admin123'
```

Update employee (password optional)
```bash
curl --location --request PUT 'http://localhost:8081/api/admin/employees/3' -u 'admin@company.com:admin123' --header 'Content-Type: application/json' --data-raw '{"name":"Eve Employee","email":"eve@company.com","role":"EMPLOYEE","active":true}'
```

Assign manager
```bash
curl --location --request PUT 'http://localhost:8081/api/admin/employees/3/manager' -u 'admin@company.com:admin123' --header 'Content-Type: application/json' --data-raw '{"managerId":2}'
```

Deactivate employee (soft delete)
```bash
curl --location --request DELETE 'http://localhost:8081/api/admin/employees/4' -u 'admin@company.com:admin123'
```

### Leave types

Create leave type
```bash
curl --location 'http://localhost:8081/api/admin/leave-types' -u 'admin@company.com:admin123' --header 'Content-Type: application/json' --data-raw '{"name":"Sick","description":"Sick leave","defaultDays":12}'
```

List leave types
```bash
curl --location 'http://localhost:8081/api/admin/leave-types' -u 'admin@company.com:admin123'
```

Get leave type
```bash
curl --location 'http://localhost:8081/api/admin/leave-types/1' -u 'admin@company.com:admin123'
```

Update leave type
```bash
curl --location --request PUT 'http://localhost:8081/api/admin/leave-types/1' -u 'admin@company.com:admin123' --header 'Content-Type: application/json' --data-raw '{"name":"Annual","description":"Paid annual leave","defaultDays":24,"active":true}'
```

Deactivate leave type (soft delete)
```bash
curl --location --request DELETE 'http://localhost:8081/api/admin/leave-types/2' -u 'admin@company.com:admin123'
```

### Leave balances

Configure balance (create or update)
```bash
curl --location --request PUT 'http://localhost:8081/api/admin/employees/3/leave-balances' -u 'admin@company.com:admin123' --header 'Content-Type: application/json' --data-raw '{"leaveTypeId":1,"year":2026,"totalDays":10}'
```

View employee balances
```bash
curl --location 'http://localhost:8081/api/admin/employees/3/leave-balances?year=2026' -u 'admin@company.com:admin123'
```

---

## Employee (`eve@company.com` / `secret1`)

My leave balances
```bash
curl --location 'http://localhost:8081/api/employees/me/leave-balances?year=2026' -u 'eve@company.com:secret1'
```

Apply for leave
```bash
curl --location 'http://localhost:8081/api/employees/me/leave-requests' -u 'eve@company.com:secret1' --header 'Content-Type: application/json' --data-raw '{"leaveTypeId":1,"startDate":"2026-11-09","endDate":"2026-11-13","reason":"Family trip"}'
```

My leave requests
```bash
curl --location 'http://localhost:8081/api/employees/me/leave-requests' -u 'eve@company.com:secret1'
```

Cancel leave request
```bash
curl --location --request PUT 'http://localhost:8081/api/employees/me/leave-requests/2/cancel' -u 'eve@company.com:secret1'
```

---

## Manager (`mona@company.com` / `secret1`)

Team leave requests (status optional: PENDING, APPROVED, REJECTED, CANCELLED)
```bash
curl --location 'http://localhost:8081/api/manager/leave-requests?status=PENDING' -u 'mona@company.com:secret1'
```

Approve leave
```bash
curl --location --request PUT 'http://localhost:8081/api/manager/leave-requests/2/approve' -u 'mona@company.com:secret1'
```

Reject leave (reason required)
```bash
curl --location --request PUT 'http://localhost:8081/api/manager/leave-requests/2/reject' -u 'mona@company.com:secret1' --header 'Content-Type: application/json' --data-raw '{"reason":"Release week, please pick other dates"}'
```

---

## Error cases

Past date → 400
```bash
curl --location 'http://localhost:8081/api/employees/me/leave-requests' -u 'eve@company.com:secret1' --header 'Content-Type: application/json' --data-raw '{"leaveTypeId":1,"startDate":"2026-10-01","endDate":"2026-10-02"}'
```

Insufficient balance (15 working days requested, 10 available) → 400
```bash
curl --location 'http://localhost:8081/api/employees/me/leave-requests' -u 'eve@company.com:secret1' --header 'Content-Type: application/json' --data-raw '{"leaveTypeId":1,"startDate":"2026-11-16","endDate":"2026-12-04"}'
```

Overlapping request (send after applying for 9–13 Nov) → 400
```bash
curl --location 'http://localhost:8081/api/employees/me/leave-requests' -u 'eve@company.com:secret1' --header 'Content-Type: application/json' --data-raw '{"leaveTypeId":1,"startDate":"2026-11-12","endDate":"2026-11-16"}'
```

Reject without reason → 400
```bash
curl --location --request PUT 'http://localhost:8081/api/manager/leave-requests/2/reject' -u 'mona@company.com:secret1' --header 'Content-Type: application/json' --data-raw '{}'
```

Validation errors (missing fields) → 400
```bash
curl --location 'http://localhost:8081/api/admin/employees' -u 'admin@company.com:admin123' --header 'Content-Type: application/json' --data-raw '{"name":"","email":"not-an-email","password":"123"}'
```

Duplicate email → 409
```bash
curl --location 'http://localhost:8081/api/admin/employees' -u 'admin@company.com:admin123' --header 'Content-Type: application/json' --data-raw '{"name":"Eve Again","email":"eve@company.com","password":"secret1","role":"EMPLOYEE"}'
```

Unknown employee → 404
```bash
curl --location 'http://localhost:8081/api/admin/employees/999' -u 'admin@company.com:admin123'
```

Employee calling admin endpoint → 403
```bash
curl --location 'http://localhost:8081/api/admin/employees' -u 'eve@company.com:secret1'
```

Wrong password → 401
```bash
curl --location 'http://localhost:8081/api/employees/me/leave-requests' -u 'eve@company.com:wrong'
```

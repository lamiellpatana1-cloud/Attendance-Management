# AttendEase - Attendance Management System

AttendEase is a Java + MySQL attendance management system with mobile-friendly admin, teacher, and student views. It supports attendance tracking, QR and geo-location attendance, marks, schedules, reports, notifications, profile management, and OTP-based verification.

## Features

- Role-based login for admin, teachers, and students
- Student, teacher, subject, and timetable management
- Manual, QR, and geo-location attendance
- Marks and grade tracking
- Dashboard charts and CSV/print reports
- Mobile bottom navigation and installable PWA metadata
- Docker and Railway deployment support

## Requirements

- Java 17 recommended
- MySQL 8.0.x
- MySQL Connector/J in `lib/`

## Local Setup

1. Create a MySQL database named `attendance_db`.
2. Run `database/schema.sql`.
3. Run `database/seed.sql`.
4. Copy your real local credentials into `config.local.properties`.

Example:

```properties
db.host=localhost
db.port=3306
db.name=attendance_db
db.user=root
db.password=your_mysql_password_here
db.ssl=false
db.timezone=UTC

mail.enabled=false
mail.host=smtp.gmail.com
mail.port=587
mail.from_name=AttendEase
mail.from=your_sender@gmail.com
mail.password=your_16_char_app_password
```

Then compile and run:

```powershell
javac -cp "lib/*" src\App.java src\ValidationUtils.java -d out
java -cp "out;lib/*" App
```

Open `http://localhost:8080`.

## Default Seed Accounts

- `admin / admin123`
- `teacher1 / admin123`
- `student1 / admin123`

Change these passwords after deployment.

## GitHub Notes

Safe files to commit include `src`, `web`, `database`, `docs`, `lib`, `Dockerfile`, `railway.json`, `.env.example`, `.dockerignore`, `.gitignore`, and `config.properties`.

Do not commit `config.local.properties`, `.env`, build outputs, logs, or local IDE files. These are already covered by `.gitignore`.

## Railway Deploy

This project includes:

- `Dockerfile` for Java build and runtime
- `railway.json` using the Dockerfile builder and `/api/health`
- `.env.example` for Railway variables
- App support for Railway's `PORT` variable

Set these variables on the Railway app service:

```env
DB_HOST=${{MySQL.MYSQLHOST}}
DB_PORT=${{MySQL.MYSQLPORT}}
DB_NAME=${{MySQL.MYSQLDATABASE}}
DB_USER=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
DB_SSL=false
DB_TIMEZONE=UTC

MAIL_ENABLED=false
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_FROM_NAME=AttendEase
MAIL_FROM=
MAIL_PASSWORD=
```

Use MySQL 8.0.x for the database service. After deploy, generate a Railway public domain and open it on mobile. The app includes a web app manifest and service worker so users can add it to their home screen.

More details are in `docs/RAILWAY-MOBILE-DEPLOY.md`.

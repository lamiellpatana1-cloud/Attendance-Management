# Railway Mobile Deploy Guide

Goal: mapatakbo ang AttendEase online para mabuksan ng mobile users sa browser gamit ang Railway URL.

## Ano ang Na-ready sa Project

- `Dockerfile` para automatic ma-build ang Java app sa Railway.
- `railway.json` para gamitin ang Dockerfile builder at `/api/health` healthcheck.
- `.dockerignore` para hindi maisama ang local secrets at build outputs.
- `.env.example` para mabilis ma-copy ang Railway variables.
- `App.java` reads Railway `PORT` and MySQL environment variables.
- Mobile/PWA files: `web/manifest.webmanifest` at `web/sw.js` para mas maayos ang Add to Home Screen.
- Kapag empty ang Railway MySQL database, auto-import ang `database/schema.sql` at `database/seed.sql`.

## Steps sa Railway

1. I-upload ang project sa GitHub.
   - Siguraduhin na kasama sa repo ang `Dockerfile`, `.dockerignore`, `.env.example`, `src`, `web`, `lib`, `database`, at `config.properties`.
   - Isama rin ang `railway.json`, `web/manifest.webmanifest`, at `web/sw.js`.
   - Huwag i-upload ang `config.local.properties`.

2. Gumawa ng Railway project.
   - Go to Railway.
   - Click `New Project`.
   - Piliin `Deploy from GitHub repo`.
   - Piliin ang AttendanceManagement repository.

3. Magdagdag ng MySQL 8.0 sa same Railway project.
   - Huwag gamitin ang default Railway `MySQL` template kung lumalabas itong `mysql:9.4`.
   - Gamitin ang Railway template na `Deploy MySQL 8 or Any Version`, then set:

```env
MYSQL_VERSION=8.0
MYSQL_DATABASE=attendance_db
MYSQL_ROOT_PASSWORD=your_strong_password_here
```

   - Kung gagawa ka via Docker image service, use image `mysql:8.0`, add a volume mounted to `/var/lib/mysql`, then set:

```env
MYSQL_ROOT_PASSWORD=your_strong_password_here
MYSQL_DATABASE=attendance_db
MYSQL_USER=railway
MYSQL_PASSWORD=your_app_db_password_here
```

   - Pangalanan ang database service as `MySQL` para tumugma agad sa variables below.

4. I-set ang variables sa app service.
   - Buksan ang app service, hindi yung MySQL service.
   - Ang variables sa ibaba ay para sa app service. Ang database service mismo dapat MySQL 8.0.x.
   - Go to `Variables`.
   - Open `RAW Editor`.
   - Paste ito:

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

Kung iba ang pangalan ng MySQL service mo, palitan ang `MySQL` sa variables. Example: kung service name ay `attendance-db`, gamitin `${{attendance-db.MYSQLHOST}}`.

5. Deploy.
   - Click `Deploy` or `Redeploy`.
   - Sa logs, dapat makita:

```text
[DB] Connected successfully!
[SERVER] Running on 0.0.0.0:<port>
```

6. Generate public domain.
   - Buksan ang app service.
   - Go to `Settings`.
   - Hanapin `Networking` / `Public Networking`.
   - Click `Generate Domain`.
   - Railway will give a URL like:

```text
https://your-app-name.up.railway.app
```

7. Buksan sa phone.
   - Open Chrome/Safari sa mobile.
   - Punta sa Railway URL.
   - Login gamit default seed account:

```text
admin / admin123
teacher1 / admin123
student1 / admin123
```

8. Optional: gawing parang mobile app.
   - Sa mobile browser, open menu.
   - Piliin `Add to Home Screen`.
   - Lalabas siya parang app icon.

## Important Notes

- Target database version: MySQL 8.0.x. Avoid the default Railway MySQL template if it shows `mysql:9.4`.
- Railway public URL uses HTTPS, kaya mas malaki ang chance na gumana ang camera QR scanner at geo-location kaysa sa local `http://192.168.x.x:8080`.
- Kung gagamit ng OTP email, set `MAIL_ENABLED=true`, `MAIL_FROM`, at Gmail App Password sa `MAIL_PASSWORD`.
- Default accounts are for testing. Palitan ang passwords pagkatapos ma-deploy.

## References

- Railway Dockerfile docs: https://docs.railway.com/deploy/dockerfiles
- Railway MySQL variables: https://docs.railway.com/guides/mysql
- Railway public networking and `PORT`: https://docs.railway.com/public-networking
- Railway variables: https://docs.railway.com/variables

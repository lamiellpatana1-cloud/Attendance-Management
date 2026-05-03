# GitHub and Railway Deployment

## GitHub

1. Create a new GitHub repository.
2. Open this project folder in a terminal.
3. Run:

```powershell
git init
git add .
git commit -m "Prepare AttendEase for Railway deployment"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

Do not commit `config.local.properties`; it is already ignored.

## Railway

1. Create a new Railway project from the GitHub repository.
2. Add a MySQL database service in Railway.
3. In the app service, set these variables from the Railway MySQL service:

```properties
MYSQLHOST=${{MySQL.MYSQLHOST}}
MYSQLPORT=${{MySQL.MYSQLPORT}}
MYSQLDATABASE=${{MySQL.MYSQLDATABASE}}
MYSQLUSER=${{MySQL.MYSQLUSER}}
MYSQLPASSWORD=${{MySQL.MYSQLPASSWORD}}
DB_SSL=false
DB_TIMEZONE=UTC
```

4. Optional mail variables:

```properties
MAIL_ENABLED=false
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_FROM=your_sender@gmail.com
MAIL_PASSWORD=your_app_password
MAIL_FROM_NAME=AttendEase
```

5. Import `database/schema.sql` and `database/seed.sql` into the Railway MySQL database.
6. Deploy. Railway will use `nixpacks.toml` to compile and start the Java app.

The app reads Railway's `PORT` automatically, so no manual port setup is needed.

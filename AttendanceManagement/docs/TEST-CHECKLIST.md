# Test Checklist

## Authentication
- Admin login works
- Teacher login works
- Student login works
- Logout works
- Session still works after server restart

## Registration / OTP
- Registration blocks invalid email
- Registration blocks duplicate email
- Registration blocks duplicate username
- OTP is required before account creation
- DOB computes age automatically
- DOB rejects ages below 15 and above 80

## Admin / Teacher Modules
- Students CRUD works
- Teachers CRUD works
- Subjects CRUD works
- Timetable CRUD works
- Attendance save works
- QR attendance save works
- Geo-location attendance works for student account
- Marks CRUD works

## Student Modules
- Student profile loads DOB and address
- Student can see only student pages in navigation
- Student QR loads
- Student attendance records load
- Student marks load
- Student schedule loads

## Reports / Dashboard
- Dashboard statistics load
- Attendance trend charts load
- Reports print correctly
- Reports CSV export works

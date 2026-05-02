# Professor Checklist

## Implemented
- Login / logout with role-based access
- Password hashing for stored user credentials
- OTP verification for registration, profile updates, and password reset
- Student management
- Teacher management
- Subject management
- Timetable management
- Manual attendance
- QR attendance scanning and QR generation
- Geo-location attendance check-in
- Marks management
- Dashboard analytics
- Reports with print and CSV export
- Student profile with DOB and Laguna address

## Practical Notes
- Subjects now include course, year level, and section targeting.
- Student schedule visibility depends on subject targeting.
- Teacher views are filtered to their own assigned subjects and schedules.
- Sessions are persisted in the database through the `sessions` table.

## Files to Review
- Backend: [App.java](C:/Users/ADMIN/Downloads/AttendanceManagement-20260407T143929Z-3-001/AttendanceManagement/src/App.java)
- Validation helpers: [ValidationUtils.java](C:/Users/ADMIN/Downloads/AttendanceManagement-20260407T143929Z-3-001/AttendanceManagement/src/ValidationUtils.java)
- Database schema: [schema.sql](C:/Users/ADMIN/Downloads/AttendanceManagement-20260407T143929Z-3-001/AttendanceManagement/database/schema.sql)
- Main UI pages: [dashboard.html](C:/Users/ADMIN/Downloads/AttendanceManagement-20260407T143929Z-3-001/AttendanceManagement/web/dashboard.html), [subjects.html](C:/Users/ADMIN/Downloads/AttendanceManagement-20260407T143929Z-3-001/AttendanceManagement/web/subjects.html), [attendance.html](C:/Users/ADMIN/Downloads/AttendanceManagement-20260407T143929Z-3-001/AttendanceManagement/web/attendance.html), [marks.html](C:/Users/ADMIN/Downloads/AttendanceManagement-20260407T143929Z-3-001/AttendanceManagement/web/marks.html), [timetable.html](C:/Users/ADMIN/Downloads/AttendanceManagement-20260407T143929Z-3-001/AttendanceManagement/web/timetable.html)

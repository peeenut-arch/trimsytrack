Firebase Email Service (Prep)

This project is prepared to use Firebase Authentication email flows (verification + password reset).

What it supports
- Send password reset email
- Send verification email
- Email/password sign-in + create account

What it does NOT support
- Sending arbitrary emails to any address (for that you typically use a backend, Firebase Extensions (Trigger Email), or Cloud Functions).

Setup steps
1) Create a Firebase project.
2) Add an Android app with applicationId: com.trimsytrack
3) Download google-services.json and place it at:
   app/google-services.json
4) Enable "Email/Password" provider in Firebase Auth.

Code
- com.trimsytrack.auth.FirebaseEmailService

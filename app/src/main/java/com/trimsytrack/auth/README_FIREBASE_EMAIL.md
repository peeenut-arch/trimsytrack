Firebase Email Service (Prep)

This project is prepared to use Firebase Authentication email flows (verification + password reset).

What it supports
- Send password reset email
- Send verification email
- Email/password sign-in + create account

Passwordless email-link ("magic link") sign-in

This is the required checklist for passwordless sign-in links to work reliably on Android.

1) Android deep links: handle BOTH Firebase link paths
- The app must register intent-filters that match BOTH of these URL paths:
   - https://<project>.firebaseapp.com/emailSignIn
   - https://<project>.firebaseapp.com/__/auth/action
   Reason: Firebase can generate either path depending on the flow.

2) Continue URL: must match what the app deep-links
- The continue URL you embed in ActionCodeSettings must match the deep link you registered.
- Example: https://<project>.firebaseapp.com/emailSignIn

3) ActionCodeSettings: must be "handleCodeInApp"
- Build ActionCodeSettings with:
   - setUrl(<continueUrl>)
   - setHandleCodeInApp(true)

4) App link handling: pass the incoming intent URL into the auth UI
- Read the incoming link from the launch intent (intent.dataString) and pass it into the auth screen.

5) Auth UI: detect + complete sign-in
- Use FirebaseAuth.isSignInWithEmailLink(link)
- Complete via FirebaseAuth.signInWithEmailLink(email, link)
- Provide a "paste link" fallback for devices/email clients that don't open the app link cleanly.

Firebase Console requirements
- Authentication → Sign-in method: enable Email/Password AND enable Email link (passwordless sign-in)
- Authentication → Settings → Authorized domains: include <project>.firebaseapp.com

TrimsyTrack implementation pointers
- Android deep links: app/src/main/AndroidManifest.xml
   - Intent-filters should include both /emailSignIn and /__/auth/action for the firebaseapp.com host.
- Continue URL constant: app/src/main/res/values/strings.xml
   - email_link_continue_url should match the /emailSignIn URL.
- ActionCodeSettings builder: com.trimsytrack.auth.FirebaseEmailService
- Pass incoming link to UI: com.trimsytrack.ui.AppNavHost
   - Pass intent?.dataString into the Auth screen.
- Complete sign-in: com.trimsytrack.ui.screens.AuthScreen
   - isSignInWithEmailLink(link) then signInWithEmailLink(email, link)

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

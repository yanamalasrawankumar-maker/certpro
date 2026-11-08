# CertChain - Blockchain Certificate Verifier (Java, JavaFX, SQLite)

## Files (keep them in ONE folder, e.g. C:\certchain)
- Core.java          exceptions, utilities, data models
- Engine.java        database (JDBC), blockchain + mining threads, service
- Screens.java       login, Authority / Issuer / Verifier / Ledger pages, animations
- CertChainApp.java  main window and menus
- style.css          colours and styling (Ink & Verdigris)

## Run (easiest)
1. Put run.bat, Core.java, Engine.java, Screens.java, CertChainApp.java and style.css in ONE folder (replace the old files).
2. Double-click run.bat. The first time it downloads the JavaFX / SQLite jars (needs internet and JDK 17+), compiles and starts the app.
   Later runs just compile and start. Or from Command Prompt:
       cd /d C:\certchain
       run.bat

## E-mail OTP (sign-up, forgot password, password change)
- Companies must give a company e-mail (mobile number optional) and enter a 6-digit OTP sent to that e-mail to create the account.
- "Forgot password?" on the sign-in screen e-mails an OTP to the registered address; Account settings also needs an OTP to change the password.
- OTP is valid 5 minutes, 5 wrong tries cancel it, a new one can be requested after 30 seconds.
- To send REAL e-mails, open run.bat and remove "rem" from the two CERT_SMTP lines (Gmail app password, see the comments there).
  Without them the app is in DEMO MODE: the OTP is shown on the screen instead of being e-mailed.
- SMS to a mobile number needs a paid SMS provider, so the number is only stored; OTPs go to e-mail.
- Authority now enters an official e-mail when creating an issuer account (needed for the issuer's forgot-password).
  If an issuer resets the password, a new signing key is created and its records are signed again.
- Old accounts have no e-mail: they can add one in Account settings (the OTP goes to the address typed there).

## Holder photo
In the Issuer portal, "Holder photo" accepts a link (https://...jpg or .png) or a file chosen from the computer.
The photo is printed at the top right of the generated certificate. It is part of the picture, so any change to
the photo changes the file fingerprint and verification reports MODIFIED. The link is only read once, when the certificate is generated.

## New in this version
- Digital signatures: every certificate is signed by its issuer with ECDSA (P-256, SHA-256). The signature and key
  fingerprint are printed on the certificate picture and checked on every verification (VALID / INVALID / Not signed).
  The issuer's private key is stored encrypted (AES-GCM, key from the account password), so only a signed-in issuer can sign.
  Editing or deleting a signature record makes verification fail.
- Account settings (sidebar button, or Ctrl+K): change username and/or password. Needs the current password.
  Issuer keys are re-protected automatically when the password changes.
- Old data keeps working: certificates made before this version show "Not signed - registered before digital signatures".
  Old issuers get a key pair the first time they sign in.

## Logins
- Authority: authority / admin123  (creates issuer accounts: institution + username + password)
- Issuer: the account the Authority created
- Verifier: a company creates its own account on the sign-in screen

## Demo
1. Authority creates issuer board.ssc / secret1.  2. Issuer signs in, generates a certificate.
3. Verifier signs up, checks the certificate ID or uploads the file -> AUTHENTIC.
4. Edit the PNG in Paint, keep the CV-ID in its file name, upload it -> MODIFIED (both hashes shown).

## Limits
Images are previewed; PDFs are checked by fingerprint only. Generated certificates are PNG. Single machine, one SQLite file.

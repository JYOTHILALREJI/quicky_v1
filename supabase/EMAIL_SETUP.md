# Quicky — Email OTP Verification Setup

The app verifies freshly created accounts with a **6-digit OTP** (one-time
password) instead of a confirmation link. This page contains the two
one-time dashboard settings that make it work and the exact email template
to paste.

## How the flow works in the app

1. **Sign Up** — the user enters email + password. Supabase creates the
   account (no session yet — "Confirm email" is ON) and sends the
   **"Quicky account creation"** email containing a 6-digit code.
2. **Log In** — the sign-in form now also shows a **"Email verification
   code"** field. The user enters **email + password + the 6-digit code**.
3. The app validates the credentials first, then redeems the code via
   `POST /auth/v1/verify` (type `signup`). On success the account is
   verified AND signed in — a fresh account cannot sign in without the
   emailed code.
4. "Didn't get the code? Resend email" re-sends a fresh code via
   `POST /auth/v1/resend`.

## Dashboard setup (one time)

### 1. Keep "Confirm email" ON

Dashboard → **Authentication → Sign In / Providers** →
**Enable email provider → Confirm email** stays **enabled**.

### 2. Replace the "Confirm signup" template

Dashboard → **Authentication → Email Templates → Confirm signup**

**Subject:**

```
Your Quicky verification code
```

**Content (paste as-is — uses `{{ .Token }}`, NOT `{{ .ConfirmationURL }}`):**

```html
<div style="margin:0;padding:32px 16px;background:#FAFAFC;font-family:Helvetica,Arial,sans-serif;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;margin:0 auto;background:#FFFFFF;border-radius:18px;overflow:hidden;">
    <tr>
      <td style="background:#8B27B0;padding:26px 32px;text-align:center;">
        <span style="font-size:24px;font-weight:800;color:#FFFFFF;letter-spacing:1px;">quicky</span>
      </td>
    </tr>
    <tr>
      <td style="padding:28px 32px 8px;">
        <h1 style="margin:0 0 10px;font-size:20px;color:#0F172A;">Your Quicky account is almost ready</h1>
        <p style="margin:0 0 6px;font-size:15px;line-height:22px;color:#475569;">
          Welcome to Quicky! Use the 6-digit code below to verify your email
          and finish creating your account.
        </p>
        <p style="margin:0;font-size:13px;line-height:20px;color:#475569;">
          Enter it in the app on the Log In screen, together with your email
          and password.
        </p>
      </td>
    </tr>
    <tr>
      <td style="padding:18px 32px;">
        <div style="text-align:center;background:#F3E8FF;border-radius:14px;padding:16px;">
          <span style="font-size:34px;font-weight:800;letter-spacing:10px;color:#8B27B0;">{{ .Token }}</span>
        </div>
      </td>
    </tr>
    <tr>
      <td style="padding:0 32px 28px;">
        <p style="margin:0;font-size:12px;line-height:18px;color:#94A3B8;">
          This code expires in 60 minutes and can be used only once. If you
          didn't create a Quicky account you can safely ignore this email.
        </p>
      </td>
    </tr>
  </table>
  <p style="text-align:center;font-size:11px;color:#94A3B8;margin-top:16px;">
    Quicky · Fast, fun dating with interactive games
  </p>
</div>
```

> **Important:** the template intentionally has **no**
> `{{ .ConfirmationURL }}` link — verification only happens in the app with
> the OTP. `{{ .Token }}` is always the 6-digit numeric code.

### 3. Make the sender say "Quicky" (not "Supabase Auth")

**Option A — hosted email sender (quickest):**
Dashboard → **Authentication → Emails** → set the hosted sender
name/display to **`Quicky`**.

**Option B — custom SMTP (production-grade, better deliverability):**
Dashboard → **Authentication → Emails → Custom SMTP**:

| Setting   | Example value                        |
|-----------|--------------------------------------|
| Host      | your provider's SMTP host            |
| Port      | 587                                  |
| Username  | your SMTP username                    |
| Password  | your SMTP password                    |
| Sender    | `Quicky <no-reply@yourdomain.com>`   |

Minimum allowed interval between emails is **60 seconds** per email.
Verification codes expire after **60 minutes** and wrong attempts are
rate-limited, so the "Resend email" button can't be spammed.

## What was wired in code

| Piece | Where |
|-------|-------|
| `verifyOtp()` → `POST /auth/v1/verify` (type `signup`) | `SupabaseAuth.kt` |
| `resendOtp()` → `POST /auth/v1/resend` | `SupabaseAuth.kt` |
| "Email not confirmed" detection on login | `SupabaseAuth.kt` |
| `authNeedsOtp` state, OTP-aware `signIn()` | `SparkViewModel.kt` |
| OTP field + "Verify & Sign In" + resend button, light theme | `AuthScreen.kt` |
| Light theme for the whole onboarding flow | `OnboardingScreen.kt` |

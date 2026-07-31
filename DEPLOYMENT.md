# Gym Management SaaS — Deployment Guide

---

## Part 1 — Firebase & Google Sign-In Setup

### 1.1 Create a Firebase Project
1. Go to https://console.firebase.google.com → **Add project**
2. Name it (e.g. `gym-management`) → Continue through the wizard

### 1.2 Add your Android App
1. **Project settings** (gear icon) → **Your apps** → ➕ Add app → **Android**
2. **Android package name**: `com.gymmanager` (must match `applicationId` in `app/build.gradle`)
3. **App nickname**: Gym Management
4. **Debug signing certificate SHA-1** — run this from your project root:
   ```bash
   cd android && ./gradlew signingReport
   ```
   Copy the `SHA1` from the **debug** variant and paste it in Firebase.
5. Click **Register app** → **Download `google-services.json`**
6. Place it at: `android/app/google-services.json`

> ⚠️ Add `android/app/google-services.json` to `.gitignore` — never commit it.

### 1.3 Enable Google Sign-In
1. Firebase console → **Authentication** → **Sign-in method** tab
2. Click **Google** → toggle **Enable** → set a support email → **Save**

### 1.4 Get the Web Client ID (for backend token verification)
1. Go to https://console.cloud.google.com → **APIs & Services** → **Credentials**
2. Find the OAuth 2.0 Client named **"Web client (auto created by Google Service)"**
3. Copy its **Client ID** — this is your `GOOGLE_CLIENT_ID` env var

### 1.5 Add release SHA-1 before publishing
When you generate a release keystore, add its SHA-1 to Firebase too:
- Firebase console → Project settings → Your apps → Add fingerprint

---

## Part 2 — Neon PostgreSQL Setup

1. Sign up at https://neon.tech → **New Project** (free tier available)
2. Choose a region near your Vercel deployment
3. Copy the **Connection string**:
   ```
   postgresql://user:password@ep-xxxx.us-east-2.aws.neon.tech/gymdb?sslmode=require
   ```
4. This is your `DATABASE_URL`

---

## Part 3 — Backend: Run Migrations & Deploy to Vercel

### 3.1 Install & migrate locally (test connection)
```bash
cd backend
npm install
# Create .env with DATABASE_URL and JWT_SECRET
npx prisma migrate dev --name init   # applies schema, creates tables
npx prisma generate                  # generates Prisma Client
```

Generate a strong JWT secret:
```bash
node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
```

### 3.2 Deploy to Vercel
```bash
npm install -g vercel
vercel login
vercel --prod
```

When prompted: no existing project, name it `gym-management-api`, root dir `./`.

### 3.3 Set environment variables in Vercel dashboard
Project → **Settings** → **Environment Variables** → add:

| Variable | Value | Notes |
|---|---|---|
| `DATABASE_URL` | `postgresql://...` | Neon connection string |
| `JWT_SECRET` | 64-char random hex | `openssl rand -hex 32` |
| `GOOGLE_CLIENT_ID` | `xxx.apps.googleusercontent.com` | Web Client ID from step 1.4 |
| `NODE_ENV` | `production` | |

Then redeploy: `vercel --prod`

### 3.4 Verify backend
```bash
curl https://your-api.vercel.app/api/health
# → {"status":"ok"}

curl -X POST https://your-api.vercel.app/api/auth/google \
  -H "Content-Type: application/json" \
  -d '{"idToken":"<a valid Firebase idToken>"}'
# → {"token":"eyJ...","user":{"id":1,"tenantId":null,...}}
```

---

## Part 4 — Android App Configuration

### 4.1 Update BASE_URL
In `android/app/build.gradle`:
```groovy
buildTypes {
    debug {
        buildConfigField "String", "BASE_URL", '"http://10.0.2.2:3000/api/"'
    }
    release {
        buildConfigField "String", "BASE_URL", '"https://your-api.vercel.app/api/"'
        minifyEnabled true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```

Replace `your-api.vercel.app` with your actual Vercel URL.

### 4.2 Local development (emulator)
```bash
cd backend && npm run dev    # starts on localhost:3000
```
The emulator reaches host machine `localhost` via `10.0.2.2`. Press **Run ▶** in Android Studio.

### 4.3 Release signing
1. Generate keystore (one-time):
   ```bash
   keytool -genkey -v -keystore android/app/gym-release.jks -alias gym -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add to `app/build.gradle` signingConfigs:
   ```groovy
   signingConfigs {
       release {
           storeFile file('gym-release.jks')
           storePassword System.getenv("KEYSTORE_PASSWORD")
           keyAlias 'gym'
           keyPassword System.getenv("KEY_PASSWORD")
       }
   }
   ```
3. Add release SHA-1 to Firebase console (step 1.5)

### 4.4 Build
```bash
cd android
./gradlew bundleRelease    # Play Store (.aab)
./gradlew assembleRelease  # Sideload (.apk)
```

---

## Part 5 — First-Run Flow (New Gym Owner)

1. User taps **Continue with Google** → Firebase validates → app sends `idToken` to backend
2. Backend upserts User (tenantId=null) → returns JWT
3. App detects `!hasCompletedOnboarding()` → opens **OnboardingActivity**
4. User fills gym name → `POST /api/onboarding/create-gym`
5. Backend creates Tenant + seeds default Plans/PaymentMethods/ExpenseCategories
6. Returns new JWT with tenantId → app stores it → opens **MainActivity**

---

## Part 6 — Files to Add to `.gitignore`

```
backend/.env
android/app/google-services.json
android/app/gym-release.jks
```

---

## Summary

| Step | Action |
|------|--------|
| 1 | Firebase project → add Android app → download `google-services.json` |
| 2 | Enable Google Sign-In in Firebase Authentication |
| 3 | Get Web Client ID from Google Cloud Console (`GOOGLE_CLIENT_ID`) |
| 4 | Create Neon database → copy `DATABASE_URL` |
| 5 | `cd backend && npm install && npx prisma migrate dev` |
| 6 | Set env vars in Vercel → `vercel --prod` |
| 7 | Place `google-services.json` in `android/app/` |
| 8 | Update `BASE_URL` in `app/build.gradle` to Vercel URL |
| 9 | `./gradlew bundleRelease` |
| 10 | Add release SHA-1 fingerprint to Firebase |

---

## Production Checklist

- [ ] `google-services.json` placed in `android/app/` and gitignored
- [ ] Release SHA-1 added to Firebase console
- [ ] `GOOGLE_CLIENT_ID` matches the Web client ID exactly
- [ ] `JWT_SECRET` is a random 64-char string
- [ ] Prisma migrations applied on production Neon DB
- [ ] `BASE_URL` in Android points to production Vercel URL
- [ ] `minifyEnabled true` enabled for release builds
- [ ] Google Sign-In tested end-to-end on physical device
- [ ] Onboarding flow tested with fresh Google account
- [ ] Two separate gym accounts cannot see each other's data (tenant isolation check)

# Gym Management System — Deployment Guide

---

## Part 1 — Set Up Neon Database

### 1. Create a Neon project
1. Go to [https://neon.tech](https://neon.tech) and sign up (free tier available).
2. Click **New Project** → give it a name (e.g. `gym-management`).
3. Choose a region closest to your users.
4. Click **Create Project**.

### 2. Get the connection string
1. In the Neon dashboard, open your project.
2. Click **Connection Details** (or the **Connect** button).
3. Select **Connection string** format and copy it — it looks like:
   ```
   postgresql://alex:password@ep-cool-surf-12345.us-east-2.aws.neon.tech/gymdb?sslmode=require
   ```
4. Save this string — you will use it as `DATABASE_URL` in the next steps.

---

## Part 2 — Deploy the Backend API to Vercel

### Prerequisites
```bash
npm install -g vercel          # Install Vercel CLI
vercel login                   # Authenticate with your account
```

### 2.1 Clone / enter the backend directory
```bash
cd gym-backend
```

### 2.2 Install dependencies and generate the Prisma client
```bash
npm install
cp .env.example .env           # Create local .env
# Edit .env and fill in DATABASE_URL and JWT_SECRET
```

Generate a strong JWT secret:
```bash
node -e "console.log(require('crypto').randomBytes(64).toString('hex'))"
```

### 2.3 Run database migrations locally (test connection first)
```bash
npx prisma migrate dev --name init
node src/seed.js               # Creates default owner: admin / password123
```

### 2.4 Deploy to Vercel
```bash
vercel --prod
```

When prompted:
- **Link to existing project?** → No (first deploy)
- **Project name** → `gym-management-api`
- **Directory** → `./` (current)
- **Override settings?** → No

### 2.5 Set environment variables in the Vercel dashboard
1. Go to [https://vercel.com/dashboard](https://vercel.com/dashboard).
2. Open your `gym-management-api` project → **Settings** → **Environment Variables**.
3. Add:

   | Name            | Value                                              | Environment       |
   |-----------------|----------------------------------------------------|--------------------|
   | `DATABASE_URL`  | `postgresql://...` (your Neon connection string)   | Production         |
   | `JWT_SECRET`    | (the long random hex string you generated)         | Production         |

4. Redeploy: `vercel --prod`

### 2.6 Run migrations on production
The `package.json` build script handles this automatically on each Vercel deployment:
```json
"build": "npx prisma generate && npx prisma migrate deploy"
```

Vercel runs this before starting the function. If you need to seed production:
```bash
DATABASE_URL="<production-url>" node src/seed.js
```

### 2.7 Verify deployment
```bash
curl https://your-api.vercel.app/api/health
# → {"status":"ok","ts":"2026-..."}

curl -X POST https://your-api.vercel.app/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'
# → {"token":"eyJ...","username":"admin"}
```

---

## Part 3 — Configure the Android App

### 3.1 Switch BASE_URL for production
Open `android/app/build.gradle`. You will find two `buildConfigField` lines:

```groovy
defaultConfig {
    // LOCAL DEV (emulator → host machine)
    buildConfigField "String", "BASE_URL", "\"http://10.0.2.2:3000/api/\""
}

buildTypes {
    release {
        // PRODUCTION — replace with your actual Vercel URL
        buildConfigField "String", "BASE_URL", "\"https://your-api.vercel.app/api/\""
    }
}
```

**Replace** `your-api.vercel.app` with your actual Vercel deployment URL (shown after `vercel --prod` finishes, e.g. `gym-management-api.vercel.app`).

Debug builds (`./gradlew assembleDebug`) point to the emulator host.
Release builds (`./gradlew assembleRelease`) point to Vercel.

### 3.2 Local development with the Android emulator

#### Start the backend locally
```bash
cd gym-backend
npm run dev              # Starts on http://localhost:3000
```

#### Run on the Android emulator
- In Android Studio, launch any AVD (e.g. Pixel 7 API 34).
- The special IP `10.0.2.2` inside the emulator routes to your host machine's `localhost`.
- The `defaultConfig` already sets `BASE_URL` to `http://10.0.2.2:3000/api/`.
- The `network_security_config.xml` already permits cleartext to `10.0.2.2`.

Just press **Run ▶** in Android Studio — the app connects to your local backend.

#### Run on a physical device (USB debugging)
1. Find your machine's local IP: `ipconfig` (Windows) / `ifconfig` (Mac/Linux).
2. Temporarily change `BASE_URL` in `defaultConfig` to `http://192.168.x.x:3000/api/`.
3. Make sure your phone and computer are on the same WiFi network.

---

## Part 4 — Changing the Admin Password

After first login, change the default password by running this script once:

```bash
# Run from the backend directory with DATABASE_URL set in .env
node -e "
const { PrismaClient } = require('@prisma/client');
const bcrypt = require('bcryptjs');
const prisma = new PrismaClient();
bcrypt.hash('YOUR_NEW_PASSWORD', 12).then(hash =>
  prisma.owner.update({ where: { username: 'admin' }, data: { passwordHash: hash } })
).then(() => { console.log('Password updated'); prisma.\$disconnect(); });
"
```

---

## Summary

| Step | Command / Action                                |
|------|-------------------------------------------------|
| 1    | Create Neon project, copy connection string     |
| 2    | `npm install && npx prisma migrate dev`         |
| 3    | `node src/seed.js` (creates admin account)      |
| 4    | Add `DATABASE_URL` + `JWT_SECRET` to Vercel env |
| 5    | `vercel --prod`                                 |
| 6    | Update `BASE_URL` in `app/build.gradle`         |
| 7    | Build APK: `./gradlew assembleRelease`          |

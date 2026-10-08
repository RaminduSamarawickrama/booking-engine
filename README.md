# Booking Engine — zero-cost development setup

Airport transfer booking platform: Spring Boot microservices, React web apps, React Native mobile apps.
This repository is set up for the **development/demo phase at $0/month**: everything runs on your
machine, free hosted services are optional, and nothing here creates a billable resource.

| | |
|---|---|
| Customer web (live) | https://booking-customer-web.vercel.app |
| Admin web (live) | https://booking-admin-web.vercel.app |
| Backend | your machine, exposed on demand through a free Cloudflare quick tunnel |

---

## 1. Current architecture

```text
 Vercel Hobby (free)                 Your machine (Docker Compose)
 ┌──────────────────┐               ┌──────────────────────────────────────────┐
 │ Customer web     │──┐            │ gateway :8080  ◄── only exposed service   │
 │ Admin web        │  │  HTTPS/WSS │   ├─ auth        ├─ payment               │
 └──────────────────┘  ├──────────► │   ├─ catalog     ├─ fleet                 │
 React Native apps ────┘  Cloudflare│   ├─ pricing     ├─ dispatch              │
 (Expo, on your phone)    quick     │   ├─ booking     ├─ tracking  (WebSocket) │
                          tunnel    │   └─ notification                         │
                                    │ PostgreSQL+PostGIS · Redis · RabbitMQ ·    │
                                    │ Mailpit   (127.0.0.1 only, never tunnelled)│
                                    └──────────────────────────────────────────┘
 Optional free remote replacements:  Postgres → Supabase/Neon · Redis → Upstash ·
 RabbitMQ → CloudAMQP · email → Brevo · push → Firebase Cloud Messaging
```

Service map (logical microservices, physically one Compose project):

| Service | Owns | Covers the names in the brief |
|---|---|---|
| gateway | routing, JWT check, rate limiting, CORS, WebSocket upgrade | API Gateway |
| auth | users, passwords (Argon2id), JWT issuing, refresh tokens, roles CUSTOMER/DRIVER/ADMIN | Authentication |
| catalog | vehicle categories, extras, airports/terminals, journey types, places lookup | Vehicle (catalog) |
| pricing | pricing rules, quotes | Pricing |
| booking | bookings, journey legs, customer profiles | Booking, Customer |
| payment | payment intents, webhooks, refunds | Payment |
| fleet | drivers, driver vehicles, availability | Driver, Vehicle (fleet) |
| dispatch | matching, offers, assignments, ride states | Driver Matching |
| tracking | live driver locations, WebSocket fan-out | Location |
| notification | email, push, SMS | Notification |

Each service has its own PostgreSQL **schema and login role** in a single database, so the same
layout works locally and on a free hosted Postgres. A service's role cannot read another schema
(tested: `payment_svc` gets `permission denied for schema booking`).

## 2. Free services being used

| Service | Used for | Free terms that matter | Card needed | Can it bill? |
|---|---|---|---|---|
| Docker (local) | all backend infrastructure | free | no | no |
| Vercel Hobby | customer and admin web | free, no expiry; personal/non-commercial use only | no | no — Hobby has hard limits, overage billing is off on this account |
| Cloudflare quick tunnel | public HTTPS URL to your gateway | free, no account, random URL per run, for testing only | no | no |
| GitHub + Actions | source control, CI | public repos unlimited; private repos 2,000 min/month | no | no, unless you add a payment method and raise the spending limit |
| Stripe test mode | payment sandbox | free; test cards only, no real money | no | no |
| Mailpit (local) | catches all email | free | no | no |
| Firebase Cloud Messaging | push (optional) | free, Spark plan | no | no on Spark |
| Supabase Free / Neon Free | remote demo Postgres (optional) | small storage; Supabase pauses after a week idle, Neon sleeps after minutes idle | no | no on free plans |
| Upstash Free | remote Redis (optional) | monthly command cap | no | no on free plan |
| CloudAMQP Little Lemur | remote RabbitMQ (optional) | monthly message cap, few connections | no | no |
| Brevo Free | real email delivery (optional) | daily send cap | no | no |
| OpenStreetMap services, postcodes.io | geocoding, routing, UK postcodes (optional) | fair-use policies, about 1 request/second | no | no |

Free-tier limits change; check each provider's pricing page before relying on a number.

## 3. Local development setup

Requirements: Docker Desktop (give it 6 GB+ memory for the full stack), Node 20+, Java 21, Git.

```bash
cp .env.example .env
docker compose up -d          # Postgres/PostGIS, Redis, RabbitMQ, Mailpit
```

- Postgres `localhost:5432` (database `booking`, one schema per service, created on first start)
- Redis `localhost:6379`, RabbitMQ `localhost:5672` (UI http://localhost:15672, `booking` / `booking-local`)
- Mailpit inbox http://localhost:8025

Two ways to run the services:
- **In your IDE** (lighter on RAM): run any service with profile `local`; it reaches the infra on localhost.
- **In Docker**: set `COMPOSE_PROFILES=infra,apps` in `.env`, then `docker compose up --build`.

All ports bind to `127.0.0.1`, so nothing is reachable from your network unless you choose to.

## 4. Remote/demo setup

Use this when the demo must survive your laptop being off, or several people test at once.
Services still run locally (or anywhere later); data moves to free hosted services.

1. Create the free resources (Supabase or Neon, Upstash, CloudAMQP). None needs a card.
2. Create the schemas and roles on the hosted Postgres:
   ```bash
   DATABASE_ADMIN_URL='postgresql://postgres:<pw>@<host>:5432/postgres?sslmode=require' \
   AUTH_DB_PASSWORD=... CATALOG_DB_PASSWORD=... (one per service) \
   ./infra/postgres/init/20-create-service-schemas.sh
   ```
3. `cp .env.demo.example .env`, fill in the URLs and passwords, then `docker compose up --build`.

## 5. Environment configuration

Spring profiles (shared defaults in `services/libs/platform/src/main/resources/booking-platform.yml`):

| Profile | Where services run | Infrastructure | Providers |
|---|---|---|---|
| `local` | IDE | Docker on localhost | mocks |
| `dev` | Docker Compose | Docker containers | mocks (Stripe test optional) |
| `demo` | Docker Compose or anywhere | Supabase/Neon, Upstash, CloudAMQP | Stripe test mode, OSM, FCM, Brevo |

Every setting is an environment variable, so switching profile never needs a code change.
Each service imports the shared file from its own `application.yml`
(`spring.config.import: classpath:booking-platform.yml`) and may add `application-local.yml`,
`application-dev.yml`, `application-demo.yml` for service-specific values.

## 6. Connector integrations

| Connector | Status | What was done |
|---|---|---|
| Vercel | connected | Created projects `booking-customer-web` and `booking-admin-web`, set build env vars, deployed both apps to production. |
| GitHub | can push to existing repos only | No repository exists for this project yet, and new repos cannot be created from here. |
| Supabase | connection not finished | Finish connecting it in claude.ai to let me create the free project and run the schema script. |
| Neon, Cloudflare, Stripe, Firebase, Upstash | not connected | Optional. Everything works locally with mocks without them. |

## 7. Docker Compose setup

`docker-compose.yml` profiles:

| Profile | Starts |
|---|---|
| `infra` | postgres (PostGIS 17), redis 7.4, rabbitmq 4.1, mailpit |
| `apps` | the 10 Spring Boot services from `./services` (one shared `Dockerfile`, `SERVICE` build arg) |
| `tunnel` | `cloudflared` quick tunnel pointed at the gateway only |
| `stripe` | Stripe CLI forwarding test webhooks to the gateway; refuses non-test keys |

Services are memory-capped (384 MB each, serial GC, small thread stacks), read-only filesystems,
non-root user, health checks on `/actuator/health/readiness`.

## 8. CI/CD setup

`.github/workflows/ci.yml` (GitHub-hosted runners, no secrets needed):
- **web**: install, unit tests, build both apps
- **backend**: Gradle tests with Testcontainers (activates once `services/settings.gradle.kts` exists)
- **infra**: validates every Compose profile, ShellCheck, and fails if an example env file contains a secret

Web deploys: connect the two Vercel projects to the GitHub repo once it exists; Vercel then builds
every push for free (production from `main`, previews from branches). No deploy token in CI.

## 9. Required environment variables

None are required for local mode — `.env.example` works as is. Optional or demo-only:

| Variable | Used by | Purpose |
|---|---|---|
| `COMPOSE_PROFILES` | Compose | `infra`, `infra,apps`, add `tunnel`, `stripe` |
| `SPRING_PROFILE` | services | `local`, `dev`, `demo` |
| `GATEWAY_BIND` | gateway | `127.0.0.1` (default) or `0.0.0.0` for phones on Wi-Fi |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | gateway | allowed browser origins (localhost, the Vercel apps) |
| `DB_URL`, `<SERVICE>_DB_PASSWORD`, `DB_POOL_SIZE` | services | Postgres location and per-service credentials |
| `REDIS_URL` | gateway, catalog, pricing, dispatch, tracking | `redis://` locally, `rediss://` for Upstash |
| `RABBITMQ_URI` | all | `amqp://` locally, `amqps://` for CloudAMQP |
| `PAYMENT_PROVIDER`, `STRIPE_PAYMENTS_API_KEY`, `STRIPE_WEBHOOK_SECRET`, `PLATFORM_COMMISSION_BPS`, `MOCK_PAYMENT_WEBHOOK_SECRET` | payment | mock or Stripe **test** mode; restricted key; 25% commission |
| `STRIPE_CONNECT_API_KEY` | fleet | restricted key for driver connected accounts |
| `STRIPE_CLI_API_KEY` | stripe-cli | test key used only to forward webhooks locally |
| `MAPS_PROVIDER`, `OSM_USER_AGENT` | catalog, pricing, dispatch | mock or free OSM services |
| `FLIGHT_PROVIDER` | booking | mock |
| `PUSH_PROVIDER`, `FCM_SERVICE_ACCOUNT_B64` | notification | log or FCM |
| `SMTP_*`, `EMAIL_FROM` | notification | Mailpit locally, Brevo for demo |
| `AUTH_JWT_PRIVATE_KEY_B64`, `AUTH_SEED_ADMIN_*` | auth | signing key (required in demo), first admin |
| `VITE_API_BASE_URL`, `VITE_WEBSOCKET_URL`, `VITE_ALLOW_API_OVERRIDE` | web apps | build-time default backend (switchable at runtime) |
| `EXPO_PUBLIC_API_BASE_URL` | mobile apps | build-time default backend (switchable at runtime) |

Real values live only in your git-ignored `.env`, Vercel project settings, or GitHub secrets.

## 10. How to run everything locally

```bash
cp .env.example .env
docker compose up -d                 # infrastructure
npm install && npm test              # web apps and shared packages
npm run dev -w @booking/customer-web # http://localhost:5173
npm run dev -w @booking/admin-web -- --port 5174
```
Once services exist: set `COMPOSE_PROFILES=infra,apps` and `docker compose up --build`.

## 11. How to deploy the free frontend

Already deployed (see top). Updates:
- **After the GitHub repo exists**: connect each Vercel project to it (root directories
  `apps/customer-web` and `apps/admin-web`); every push deploys automatically.
- **Until then**: ask Claude to redeploy through the Vercel connector, or run
  `npx vercel deploy --prod` inside an app folder.

The web apps do not bake in a tunnel URL. Each has a **Backend** panel that switches between
localhost, a LAN IP, a tunnel or a demo API at runtime. A `?api=` link is only applied after you
confirm it, so a link cannot silently send an admin's login to someone else's server.

## 12. How to expose the backend for mobile testing

| Option | When | How |
|---|---|---|
| Emulator | Android emulator / iOS simulator | `http://10.0.2.2:8080` (Android) or `http://localhost:8080` (iOS) |
| LAN | phone on the same Wi-Fi | `GATEWAY_BIND=0.0.0.0` in `.env`, then `./scripts/lan-url.sh` |
| Tunnel | phone anywhere, or the Vercel web apps | `./scripts/tunnel.sh` (or `--ide` if the gateway runs in your IDE) |

The tunnel forwards to the gateway only. Postgres, Redis and RabbitMQ are never reachable through it.

## 13. How to run the Customer Mobile app

The React Native apps are built in a later increment (see the architecture plan). They will use Expo:
```bash
cd apps/customer-mobile
EXPO_PUBLIC_API_BASE_URL=<tunnel or LAN URL> npx expo start
```
They reuse `@booking/runtime-config`, so the backend can also be changed in-app under Settings.

## 14. How to run the Driver Mobile app

Same as above from `apps/driver-mobile`. Location sharing needs a development build
(`npx expo run:android`, free) because background location does not work in Expo Go.

## 15. How to test the complete booking flow

Available as the services land; the target script for the demo is:
1. `docker compose up` with `COMPOSE_PROFILES=infra,apps`, then `./scripts/tunnel.sh`.
2. Customer web: get a quote, choose vehicle and extras, pay with the **mock** provider
   (or Stripe test card `4242 4242 4242 4242` with `PAYMENT_PROVIDER=stripe` and the `stripe` profile).
3. The payment webhook (signed, verified, idempotent) confirms the booking; Mailpit shows the email.
4. Driver app (signed in as a seeded driver) receives the offer, accepts, goes en route; the
   customer and admin see the driver move over WebSocket.
5. Admin web: watch the booking, reassign the driver, cancel and refund.

## 16. Limitations caused by free tiers

- **Quick tunnel URL changes on every restart** and has no uptime guarantee; fine for testing, not for sharing a long-lived demo.
- **Vercel Hobby is for non-commercial use.** If this becomes a paid client project, move the web apps to Cloudflare Pages (free, commercial use allowed) or a paid Vercel plan.
- **iOS push notifications need an Apple Developer account** (paid). Test push on Android; iOS still gets in-app and WebSocket updates.
- **Supabase pauses** inactive free projects; **Neon** sleeps when idle (first request is slow). Keep `DB_POOL_SIZE` at 2 — free tiers allow few connections.
- **Upstash** counts every Redis command; driver location updates every few seconds will use the free quota quickly with many drivers. Use local Redis for tracking tests.
- **OSM public services** allow about one request per second and forbid heavy use; all lookups are cached in Redis and mocks are the default.
- **No free flight-tracking API** offers webhooks; flights are mocked.
- **No free SMS** worth relying on; SMS is logged only.
- Running all ten services in Docker needs roughly 4–5 GB of RAM.

## 17. What must be replaced for production

| Dev/demo choice | Production replacement |
|---|---|
| Laptop + quick tunnel | a real host (VPS or managed containers) with a named tunnel or load balancer on your own domain |
| Vercel Hobby | Cloudflare Pages (free, commercial) or Vercel Pro |
| Free Supabase/Neon | a paid Postgres plan or self-hosted Postgres with automated off-site backups and point-in-time recovery |
| Upstash/CloudAMQP free | paid tiers or self-hosted Redis/RabbitMQ next to the services |
| Mock maps / OSM public servers | Google Maps Platform or a paid OSM provider, with caching |
| Mock flights | a flight data provider (e.g. FlightAware AeroAPI) |
| Mailpit / Brevo free | a transactional email plan with your domain's SPF/DKIM |
| Stripe test mode | Stripe live keys, with webhook signing secrets in a secrets manager |
| Log-only SMS, Android-only push | an SMS provider and an Apple Developer account for iOS push |
| `.env` files | a secrets manager and per-environment configuration |

## Stripe (Payments, Connect, Invoicing)

The design is in [`connect-recommend-plan.md`](connect-recommend-plan.md): customers pay the platform at
booking (separate charges and transfers), drivers are Accounts v2 recipients with the Express dashboard and
receive 75% of each completed leg by transfer, and corporate customers get monthly invoices on terms.

`services/libs/stripe-integration` implements it without any web framework, on stripe-java 33.4.0
(API version `2026-08-26.dahlia`):

| Class | Does |
|---|---|
| `StripePaymentsGateway` | Checkout Session (`ui_mode: elements`) for web, PaymentIntent for the mobile PaymentSheet, refunds |
| `DriverAccountGateway` | v2 driver accounts, onboarding links, Express login links, payout readiness from v2 capability status |
| `DriverTransferGateway` + `CommissionPolicy` | per-ride transfers from the booking charge (`source_transaction`), reversals |
| `CorporateInvoiceGateway` | corporate customers, monthly `send_invoice` invoices with one line per ride |
| `StripeEventTranslator` | verifies webhook signatures and turns events into domain signals |

Every write carries an idempotency key, nothing passes `payment_method_types`, and bookings are confirmed only
from signed webhooks. Keys: one **restricted** key per service (`rk_test_...`), never a key in source files.
Enable the key-blocking hook once per clone:

```bash
git config core.hooksPath .githooks
```

Run its tests: `cd services && gradle :libs:stripe-integration:test` (CI runs them on every push).

## Repository layout

```text
docker-compose.yml           local stack (profiles: infra, apps, tunnel, stripe)
.env.example                 local defaults (no secrets)
.env.demo.example            free remote services template
infra/postgres/init/         schema-per-service setup (local and remote)
services/                    Spring Boot services (Gradle multi-project), shared Dockerfile
services/libs/platform/      shared Spring config: profiles local / dev / demo
services/libs/stripe-integration/  Stripe Payments, Connect, Invoicing, webhooks (tested)
connect-recommend-plan.md    Stripe Connect design decisions
.claude/skills/frontend-design/  UI design guidance used when building the apps
.githooks/pre-commit         blocks commits containing Stripe keys
apps/customer-web, admin-web React + Vite apps (deployed on Vercel)
packages/runtime-config      backend URL switching, shared by web and mobile (unit tested)
packages/ui                  shared React components and styles
scripts/tunnel.sh, lan-url.sh  expose the gateway for phones and the web apps
.github/workflows/ci.yml     free CI
```

# huds-frontend

Web client (Desktop + Mobile PWA) of HADS, the diagnostic system of Hugo's Appliance Repair.
It talks to `huds-backend`. This is **not** the public website; that one is a separate static project.

## Stack

React, TypeScript and Vite. Offline storage with Dexie (IndexedDB). See the HADS Phase 1 engineering package
(`docs/03_Technology_Stack_Versions.md`, `docs/09_SDK_Framework_Version_Register_2026-10-08.md`) for the chosen versions.

## Run locally

```bash
npm install
npm run dev        # http://localhost:5173, /api is proxied to http://localhost:8080
npm run build      # type check + production build into dist/
npm run lint
```

Start `huds-backend` first (it listens on port 8080).

## Layout

```
src/
  api/          HTTP client, CSRF handling, login, shared types
  db/           Dexie database (one per user): local copy of server data and the outbox; device id
  sync/         Outbox, sync engine (push in batches, handle results), download of the customer list
  features/     One folder per business area (customers, appliances, ...)
  components/   Shared UI components
  routes/       Route definitions
  prototypes/   Throw-away UI experiments, not part of the app
public/         Copied as-is to the build output (favicon, robots.txt, _headers)
```

## Rules

- All business writes go through `POST /api/v1/sync/push` (see `sync-api-contract.md` in the backend docs).
- The device id (a UUID made once per browser, see `src/db/device.ts`) is sent at login. The server keeps it in the session and records it with every synced change.
- Never log request or response bodies: they contain personal data.

## Pages

The app uses hash addresses, so no server settings are needed.

| Address | Page |
|---|---|
| `#/` | Public home page of Hugo's Appliance Repair, with a "Staff login" link |
| `#/login` | Staff login |
| `#/my/customers` | Customers: list, add |
| `#/my/customers/<id>` | One customer on top, the customer's service addresses below (add, edit, archive) |
| `#/my/appliances` | Appliance models: list, filter by type, add |
| `#/my/appliances/models/<id>` | One model on top, the appliances of this model below |
| `#/my/appliances/items/<id>` | One appliance with its model |
| `#/my/cases` | Placeholder (no case module on the server yet) |

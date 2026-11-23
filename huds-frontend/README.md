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
- A case is opened on the device and given its number by the server (`YYYY-NNNN`); until then the list shows "Number pending". Every step of a case (`TRANSITION`) is its own change in the outbox, is never merged with another, and goes out one per request. The history of a case (and later of a visit), the `STATE_EVENT` type shared by all entities, is only downloaded.
- Visits (`SERVICE_VISIT`) are planned by an administrator with a technician; a technician starts, pauses, resumes and completes his own visits (an administrator may do it for him). A case has at most one visit that is not completed or cancelled (planned, on site or paused): a new visit cannot be planned before that, on the screen and on the server. Every visit step is a `TRANSITION` like a case step. The reason of a pause is sent as `notes` and shown in the history. While a visit is on site or paused the case can only move between "In repair" and "Verifying".
- The list of users (name and role) is downloaded as `USER` (read only); the visit forms offer only the users with the role `TECHNICIAN`. The download of `USER` and `SERVICE_VISIT` is allowed to fail until the backend of M3 is deployed (`OPTIONAL_TYPES` in `src/sync/pull.ts`); remove that list afterwards.
- Buttons follow the permissions of the logged-in user (`src/api/permissions.ts`): `VISIT_ASSIGN`, `VISIT_WRITE`, `CASE_WRITE`, `CASE_TRANSITION_FIELD`. The server checks them again. Permissions are read at login, so sign in again after they change.
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
| `#/my/appliances/models/<id>` | One model on top, the appliances of this model below (with their customer); "+ New appliance" asks for the customer |
| `#/my/appliances/items/<id>` | One appliance with its customer and model (the owner cannot be changed) |
| `#/my/cases` | Case list: filter by state, search by number, customer, phone, appliance |
| `#/my/cases/new` | Open a new case (customer, address, one of the customer's appliances, problem); anything missing can be created on the spot. Refused while the appliance has an unfinished case |
| `#/my/cases/<id>` | Case detail: summary, the visits of the case (administrators plan a visit here), next-step buttons (case state machine; limited by role and by an open visit), history from the server plus steps still waiting to be sent |
| `#/my/visits` | My visits: on site now, planned, recently finished. Administrators can switch to everybody's visits |
| `#/my/visits/<id>` | Visit detail: case, customer, address, appliance; next-step buttons (start, pause, resume, complete, cancel); history |

## Fields added with the new database design (Dexie version 10)

- Customer: `customerType`. Appliance: asset number, purchase order number, production date, estimated age, wiring diagram location (the two document ids are kept and sent back, documents cannot be uploaded yet).
- Case: `primarySymptom`, `secondarySymptoms`, `failureTiming` (`{phase, afterMinutes}`, the frontend decides the content). The old `errorCodes` text is gone.
- Error codes are the sync entity `CASE_ERROR_CODE` (table `caseErrorCodes`, pulled after `CASE`). They are never deleted; the status moves to `STORED_HISTORY`.
- Visit: `visitKind` (empty = the server decides FIRST / FOLLOW_UP; RECALL must be chosen).
- After the server database was rebuilt, clear the site data (IndexedDB) of the app once: old local rows and cursors belong to the old database.

## Safety and baseline checks of a visit (Dexie version 11)

- Sync entity `VISIT_SAFETY_CHECK`, table `visitSafetyChecks`, pulled after `SERVICE_VISIT`. The id of a record is the id of its visit.
- Section 2 of the diagnostic form (a card on the visit page): `safetyChecks` (the 8 ticks), `utilityBaseline` (supply type, L1-N / L2-N / L1-L2 voltage), `environmentBaseline` (water temperature, static / dynamic pressure, gas, notes). The front end decides the content; the server only checks that each one is a JSON object.
- Written only while the visit is on site (in progress or paused), by the technician of the visit or an administrator. A check that is not ticked is simply not recorded; only the existence of the record gates the completion.
- The first save is a CREATE that depends on the newest unsent change of the visit (its start), so the server never sees the checks before the visit is in progress.
- Because this record has the same id as the visit, the outbox is keyed by entity type and id together (`outboxOfRecord`, `recordKey` in `syncEngine.ts`).

## Visit flow and the safety checks

- `PLANNED` -> `IN_PROGRESS` ("Start visit": `startedAt` is set, an `OPEN` case moves to `DIAGNOSING`) -> `COMPLETED`. There is no separate safety-check status.
- After the start, the safety checks form is shown flat on the visit page. The technician fills it in and presses "Save checks" (offline: the first save is a CREATE, later saves are UPDATEs). A finished visit shows it read only.
- "Complete visit" is not offered until the visit has its safety checks; the server refuses the completion as well. Pause and cancel are never blocked, so a technician can leave an unsafe site.

## Records of a visit and of a case (Dexie version 12)

New sync entity types, each with its own table (all pulled after the visits and cases, and treated as optional while the backend may not have them yet):

| Entity type | Table | Where it is shown |
|---|---|---|
| `DIAGNOSTIC_STEP` | `diagnosticSteps` | visit page, "Diagnostic steps" (add / edit in a dialog) |
| `VISIT_COMPLETION` | `visitCompletions` | visit page, "Completion record" (inline form, Save button) |
| `CASE_SUMMARY` | `caseSummaries` | case page, "Case summary" (inline form, Save button) |
| `CASE_INVOICE` | `caseInvoices` | case page, "Bill" |
| `CASE_INVOICE_PAYMENT` | `caseInvoicePayments` | case page, payments of the bill |

- Steps and the completion record are written only while the visit is in progress or paused, by the technician of the visit or an administrator (`visitRecordGuard.ts`). Their first save waits behind the newest unsent change of the visit.
- Steps are numbered from 1 within the visit; the number and the visit never change. Nothing is ever deleted: a wrong record is corrected by editing it.
- The completion record is filled in before the visit is completed. "Complete visit" still only needs the safety checks.
- The case summary can be written until the case is closed. A saved list of failure categories cannot be cleared (the server reads an empty list as "unchanged"); the main category must be one of the ticked ones.
- The bill: one per case, created as a draft. Fees can be changed while it is a draft; "Issue bill" and "Void bill" ask once more and cannot be undone. A new bill must reach the server before it can be issued or voided. Subtotal, tax and total are worked out on this device with the same rounding as the server (`invoiceTotals`).
- Payments can be added and corrected until the bill is void. Amounts are CAD with at most two decimals.

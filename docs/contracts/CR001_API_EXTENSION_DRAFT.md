# CR-001 API extension — design draft (not executable OpenAPI)
- `POST /api/v1/cases/{caseId}/pre-service-invitations` (staff; create token and queue SMS; no consent inferred)
- `GET /public/pre-service/{token}` (public; fee/policy snapshot, appointment context, expiry; do not expose private case details)
- `POST /public/pre-service/{token}/acknowledgments` (public; explicit checkbox + consent statement version + idempotency key; creates immutable ACCEPTED/DECLINED event)
- `POST /api/v1/cases/{caseId}/appointments` (staff; proposed/confirmed time window, timezone, method, actor)
- `GET /api/v1/cases/{caseId}/pre-service-status` (staff; policy, appointment, readiness and timeline)
- `POST /api/v1/cases/{caseId}/pre-service-attestations` (staff; phone attestation; cannot impersonate customer web action)
Server rules: token hash+expiry; replay-safe; price change invalidates acceptance; appointment alone cannot authorize diagnosis; repair always requires separate estimate approval. Formal request/response and authorization schemas to be frozen during Sprint 0.

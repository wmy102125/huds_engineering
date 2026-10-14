# CR-002 API contract — proposed endpoints (not yet implemented)
Base `/api/v1`. All financial/authorization writes require authenticated actor, idempotency key, server-side permission and version checks; customer approval via separately scoped, expiring token and explicit action.

| Method | Path | Intent |
|---|---|---|
| GET | `/cases/{caseId}/visits` | Timeline of all visits |
| POST | `/cases/{caseId}/visits` | Schedule next visit; include `expectedCaseVersion` |
| POST | `/cases/{caseId}/parts-orders` | Create order draft against estimate revision |
| GET | `/cases/{caseId}/parts-orders` | Order history and statuses |
| POST | `/parts-orders/{orderId}/submit` | Server validates authorization/deposit policy |
| POST | `/parts-orders/{orderId}/events` | Backorder/receive/return/cancel events |
| POST | `/cases/{caseId}/deposit-requests` | Generate deposit request against approved estimate |
| POST | `/cases/{caseId}/payments` | Record payment or gateway-confirmed payment, idempotent |
| POST | `/payments/{paymentId}/refunds` | Refund with reason, limits and approval |
| POST | `/cases/{caseId}/invoices` | Create draft invoice from actual charge items |
| POST | `/invoices/{invoiceId}/issue` | Freeze invoice and produce immutable snapshot |
| GET | `/cases/{caseId}/financial-summary` | Total, net receipts, allocations, balance |
| GET | `/cases/{caseId}/receipts` | Receipt history |

Example deposit command (illustrative only):
```json
{"caseId":"uuid","approvedEstimateRevisionId":"uuid","partsOrderId":"uuid","amount":"145.00","currency":"CAD","idempotencyKey":"uuid"}
```

**HTTP responses:** 201 created; 409 optimistic-lock or state conflict; 422 approval/deposit preconditions; 403 permission; 400 invalid amount; no payment success response until funds are verified/recorded. Webhooks must verify signatures, replay/idempotency and provider event ordering. No raw payment card data stored.

**OpenAPI:** existing `openapi_phase1_skeleton.yaml` remains a draft. Above endpoints must be converted to typed OpenAPI schemas before development acceptance; do not claim these are implemented.

# Developer handoff — Claude Code / CR-002
Read README, AI_TEAM_RULES, CR-001, CR-002, baseline SQL, and form spec first. **Do not implement V004 design draft directly.**
1. Inspect existing code/migrations and create an ADR on authoritative financial ledger vs legacy `payment_records`, invoice numbering, visit numbering/backfill, and permission model.
2. Propose actual reviewed Flyway migration with DB constraints, indexes and transactional allocation protections.
3. Build backend domain/services, explicit transitions, idempotency and audit; add unit + Testcontainers PostgreSQL integration tests.
4. Build responsive desktop/mobile visits/parts/estimate/deposit/receipt/invoice flows; online-only financial commits.
5. Provide printable form generation matching approved v1.1 design; do not replace source legal terms without approval.
6. Run CR002 acceptance tests and provide commands, logs, screenshots, coverage and unresolved risks.
**Definition of done:** exact approval revision preserved, multi-visit repair works, receipts/allocations reconcile, cancellation/refunds auditable, offline never falsely marks money received, CR001 still works, code review by Gemini and PO acceptance.

# mt-api

Monétique Tunisie gateway used by the Payment Hub connector. Transactions are stored in PostgreSQL. Outbound messages are forwarded to the external SMT host (`mt.external.base-url`). In the sandbox that host is the simulator, which only acknowledges the call and posts a callback.

## Gateway (`/mt/v1`)

| Method | Path | ISO 8583 |
|--------|------|----------|
| POST | `/authorizations` | 0100 authorization |
| POST | `/captures` | 0220 financial presentment |
| POST | `/refunds` | 0200 credit |
| POST | `/voids` | 0400 reversal |
| GET | `/transactions/{id}` | host inquiry |
| POST | `/callbacks` | network callback |

Response code `00` is approval. `05` is do not honor. A repeated `orderId` is rejected as a duplicate.

## Console (`/api`)

Paged transaction search (`direction`, `status`, `operation`, `q`, `from`, `to`, `page`, `size`) and capture, refund, and void actions.

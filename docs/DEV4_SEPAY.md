# Dev 4: SePay Payment & Enrollment

Backend supports one paid, active course per order, VND integer amounts, permanent active enrollment.
No package/combo/cart, automatic refund, email sending or SePay transaction polling is included.
SePay bank webhook + QR is used, not PayOS checkout or SePay hosted Payment Gateway/IPN.

## Configuration

Keep these in the backend `.env` (already ignored by Git); see `.env.example`:

```dotenv
SEPAY_WEBHOOK_SECRET=<exact HMAC secret saved in SePay>
SEPAY_BANK_CODE=MBBank
SEPAY_ACCOUNT_NUMBER=<receiving account, keep leading zeros>
SEPAY_ACCOUNT_NAME="TEN CHU TAI KHOAN"
SEPAY_PAYMENT_PREFIX=EC
SEPAY_ORDER_EXPIRY_MINUTES=15
```

Docker Compose loads `.env` into the app container. An IDE does not automatically load this file;
import these environment variables into its Run Configuration. The app still starts without SePay
settings, but checkout/webhook fail closed with HTTP 503 until configured. No API token is needed
for the current push-only flow. Account configuration is snapshotted on each order.

In SePay Test mode configure code recognition: `EC` + exactly 10 numeric characters.
Webhook: incoming only, specific receiving account, JSON, EC code filter, HMAC-SHA256, retries enabled.
URL: `https://<current-ngrok-domain>/api/v1/payments/sepay/webhook`, with ngrok forwarding to port 8080.
Ordinary bank accounts are supported in this version; VA/subAccount settlement is held for review.
Test mode uses simulated transactions; do not transfer real money to generated test accounts or QR codes.

## Run

```powershell
docker compose up -d --build app
docker compose logs -f app
```

Flyway runs V4 on startup. V1-V3 remain unchanged. V4 adds order and webhook tables, transaction
provider references and a unique active enrollment constraint. If legacy data already has duplicate
active enrollments, resolve those records deliberately before migrating; no data is deleted automatically.
Do not run `docker compose down -v` to fix migration issues.

## API contract

All application APIs use the existing `{code:1000,message:"Success",result:...}` envelope.
Paginated results use `{currentPage,pageSize,totalPages,totalElements,data}`.
JWT is passed with `Authorization: Bearer <accessToken>`, except the HMAC webhook.

| Method | Endpoint | Access / result |
|---|---|---|
| POST | `/api/v1/payments/orders` | USER or STUDENT; body `{"courseId":"UUID"}` |
| GET | `/api/v1/payments/orders` | Own orders; `page`, `size`, `sort=createdAt,desc` |
| GET | `/api/v1/payments/orders/{orderCode}` | Owner only; current order status |
| POST | `/api/v1/payments/orders/{orderCode}/cancel` | Owner only; pending orders |
| POST | `/api/v1/payments/sepay/webhook` | SePay HMAC; raw JSON body |
| GET | `/api/v1/me/payments` | Own successful settlement history |
| GET | `/api/v1/me/enrollments` | Own active courses; sort `enrolledDate,desc` |
| GET | `/api/v1/me/enrollments/{courseId}` | Own `{courseId,enrolled}` |
| GET | `/api/v1/courses/{courseId}/enrollment-status` | Authenticated; own `{courseId,enrolled}` |
| GET | `/api/v1/admin/payments` | ADMIN; orders, optional `status=PENDING/PAID/...` |
| GET | `/api/v1/admin/payments/{orderId}` | ADMIN; order details |
| GET | `/api/v1/admin/payments/{orderId}/webhook-events` | ADMIN; up to 100 order events, newest first |
| GET | `/api/v1/admin/payments/webhook-events` | ADMIN; up to 100 events including unknown orders |

Create order response includes `orderId`, `orderCode`, `studentId`, `courseId`, `courseTitle`, `amount`,
`currency`, `status`, `createdAt`, `expiresAt`, `paidAt`, `bankCode`, `accountNumber`, `accountName`,
`transferContent` and `qrUrl`. Frontend must use the returned amount and transferContent.
Reuse an unexpired pending order after repeated clicks; the price snapshot does not change if the course price changes.
Amounts sent by the client are never used. Already-enrolled users cannot create another purchase.
Zero/null/fractional prices are not purchasable in this paid-course flow.

Poll the order endpoint every few seconds while pending; stop at a terminal state or timeout.
Display `qrUrl` only while pending. Cancelling locally cannot prevent a bank transfer to an already-copied QR.

## Settlement and access

Signature: `X-SePay-Signature: sha256=<hex HMAC-SHA256>` over exact UTF-8
`X-SePay-Timestamp + "." + raw_request_body`, using the shared secret as bytes.
Timestamp must be within 300 seconds of server time. Do not reserialize JSON before verification.
The controller permits only this POST without JWT; HMAC is verified by the service.
SePay success acknowledgement is HTTP 200 with `{"success":true}` instead of ApiResponse.

Processing is one database transaction: reserve unique SePay event ID, lock order, check incoming direction,
receiving account, exact order code, exact amount and unexpired pending state, record successful transaction,
create enrollment and mark order PAID. PostgreSQL constraints and row locks protect concurrent deliveries.
New registrations have USER role. Successful settlement promotes USER to STUDENT and creates a student profile
in that same transaction; creating, cancelling or failing an order does not promote the account.
Failed database writes roll back the event receipt too so provider retries can safely reprocess it.
Duplicate events are acknowledged without duplicate settlements/enrollments.

| Situation | Outcome |
|---|---|
| Exact payment for pending order | PAID + successful transaction + active enrollment |
| Duplicate SePay ID | Acknowledge; no second mutation |
| Wrong amount / payment after cancel or expiry | REVIEW_REQUIRED; retain raw event; no entitlement |
| Another transfer for an already paid order | Preserve PAID; log extra transfer for manual review |
| Unknown code / wrong account / outgoing / VA | Retain event with reason; no entitlement |
| Bad signature / timestamp | HTTP 401; no event stored |
| Missing SePay config | HTTP 503 |
| Invalid JSON / missing transaction fields | HTTP 400 |

Pending orders expire every minute and immediately when fetched/cancelled. A REVIEW_REQUIRED order needs
manual investigation; there is intentionally no API to arbitrarily set PAID or grant a refund.
Signed unmatched events are acknowledged to avoid endless retries of payments that cannot auto-settle.
Operators can inspect raw events in the admin API before reconciling received funds.
Payment confirmation code is generated by the database; email is not marked sent without a mail sender.

`EnrollmentService.isEnrolled(studentId, courseId)` is the integration contract for Dev 3/5.
`SessionEnrollmentChecker` now delegates to it. Course content also permits ADMIN and its assigned mentor.
Other users receive outline metadata only for locked content: no `contentData`, `description` or `videoUrl`.
Explicit `isPreview=true` content stays public. Homework submission requires active enrollment.
This protects API responses, not files already accessible through public media hosting; private video hosting
or signed media URLs are a separate deployment concern.

## Tests

Unit verification (no infrastructure):

```powershell
mvn '-Dtest=SePayWebhookVerifierTest' test
```

Integration tests require a separate disposable PostgreSQL database. They write fixtures and successful
payments and must never target the normal app DB. For example:

```powershell
docker run --name englishcenter-dev4-test-db -e POSTGRES_PASSWORD=dev4-test -e POSTGRES_DB=englishcenter_dev4_test -p 127.0.0.1:55439:5432 -d postgres:16-alpine
$env:PAYMENT_TEST_DB_URL='jdbc:postgresql://localhost:55439/englishcenter_dev4_test'
mvn '-Dtest=SePayWebhookVerifierTest,PaymentIntegrationTest' test
docker stop englishcenter-dev4-test-db
docker rm englishcenter-dev4-test-db
```

Tests cover HMAC raw Unicode payload, tampering, stale/future headers, checkout price/reuse/concurrency,
settlement duplicates/concurrency, wrong amount/account, cancellation/expiry, ownership, locked JSON,
HTTP authentication/authorization, history and atomic rollback/retry.

For dashboard E2E: create a real pending order through Swagger as STUDENT, then simulate incoming money
in SePay Test mode using its exact amount and orderCode. Confirm webhook success, order PAID and My Courses.
Use SePay's resend action to confirm there is still one enrollment. Keep the tunnel and Docker app running.

## Remaining before production

- Complete dashboard-to-backend E2E with the actual Test mode secret, then configure/verify a Live bank account.
- Decide refund, late/partial/excess payment and free-course policy; no automatic bank refund is implemented.
- Add API-based reconciliation if required to recover notifications missed beyond SePay's retry window;
  that needs an additional API token. Current implementation records only received authenticated webhooks.
- Connect frontend checkout/My Courses to these APIs. No frontend code is changed here.

## Provider references

- HMAC and timestamp: https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc
- QR + bank webhook flow: https://developer.sepay.vn/vi/sepay-webhooks/tao-qr-va-form-thanh-toan
- Test mode: https://developer.sepay.vn/vi/tien-ich-khac/test-mode

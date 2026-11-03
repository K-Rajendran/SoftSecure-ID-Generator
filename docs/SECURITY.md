# Security implementation and limits

All claims below refer to inspected source code. No build, cryptographic test,
dependency vulnerability scan, penetration test or deployment verification was run.

## Implemented controls

| Control | Source | Exact scope |
| --- | --- | --- |
| Password hashing | `security/PasswordHasher.java` | PBKDF2WithHmacSHA256, 600,000 iterations, random 16-byte salt, 256-bit output, constant-time comparison; bounded stored hash parameters |
| New password policy | `service/Validation.java` | 12–128 characters; no arbitrary composition rule; legacy import can retain shorter existing passwords |
| Durable login lockout | `security/AuthService.java` | Five wrong passwords on an existing enabled account lock it for 15 minutes; DB row locks/counters; DB clock; counter updates commit on denial |
| Unknown account handling | `security/AuthService.java` | Dummy password hash work and generic authentication failure; one attempt/second per application instance; no central unknown-user limit |
| Roles / ownership | `service/IdService.java`, `persistence/UserRepository.java` | Database role is authoritative; admin-only provisioning/review; member ownership; disabled accounts and session version rechecked for service operations |
| Session expiry | `security/Session.java`, `ui/AppFrame.java` | Monotonic idle and absolute limits; sign-out; expired UI reset; stale background results suppressed |
| Session revocation | `AuthService`, `maintenance/Maintenance.java` | Password changes/resets and enable/disable operations increment DB session version; existing sessions rejected on their next service operation |
| Personal data encryption | `security/Crypto.java`, `persistence/*` | AES-256-GCM, random 96-bit nonce per encryption, 128-bit authentication tag, version byte, purpose/record identifier as AAD |
| Searchable identifiers | `security/Crypto.java` | Separate 256-bit HMAC key, exact case-sensitive username tag; actual username stored encrypted |
| SQL injection protection | `persistence/*`, `service/*`, `maintenance/*` | Values bound through PreparedStatement; interpolated identifiers/status clauses are internal constants only |
| Transactions | `persistence/Database.java` | Submission/review/account changes and relevant audit rows commit together; rollback on exception; version checks reject stale review/edit |
| QR lifecycle | `service/IdService.java`, `export/QrService.java` | 32 random bytes, Base64url, `SSID1.` prefix; SHA-256 lookup; recoverable token encrypted; issued on approval; cleared on rejection/revocation/resubmission |
| Verification | `service/IdService.java` | Authenticated DB lookup, current approved state and enabled account; returns member name/identifier/role, not phone/address/medical details |
| PDF protection | `export/ExportService.java` | PDFBox 256-bit encryption, independent entered password and random owner password; restricted modification/copy permissions, printing permitted |
| Sensitive image handling | `export/ImageFiles.java` | PNG/JPEG only, compressed size and decoded dimension limits, normalized RGB PNG without source metadata |
| Safer output | `export/ExportService.java` | User-selected destination, temporary staging, no replacement of existing file, cleanup on failure; no password-derived filenames |
| Safe error reporting | `ui/Errors.java` | Authored user messages; otherwise event reference/type/SQLState only; no JDBC message, stack trace, raw credential or profile logging |
| Database transport | `persistence/Database.java` | verify-full TLS unless explicit localhost development exception; connect/socket/query timeouts; URL query options disallowed |

## Key and plaintext handling

The two new keys must be independently generated and supplied externally.
Configuration is not a vault: protect environment/property-file access with OS
permissions and your secret-management process. No key is automatically generated
or persisted in this source tree. A malformed/missing key prevents startup.
An incorrect valid-length key fails decryption; the application does not silently
replace it. An incorrect lookup key can make accounts appear missing; verify
configuration before provisioning anything against an existing database.

GCM contexts are `user:<account UUID>`, `profile:<request UUID>`,
`reason:<request UUID>` and `token:<request UUID>`. Database role/status/version,
timestamps, audit identifiers and salted password hashes are not encrypted.
HMAC tags allow exact account lookup without deterministic encryption of profiles.
The application has a single active key pair, not a key ring or automatic rotation.

Password character arrays and intermediate cryptographic byte arrays are cleared
where practical. Immutable strings are unavoidable in the legacy importer,
configuration, display text and PDFBox API; the JVM does not guarantee heap
erasure. Profiles and portraits are necessarily decrypted in memory while used.
Do not claim that all data is encrypted at every point in its lifecycle.

## Approval, verification and export boundaries

The UI cannot select an administrative role at login. Authorization is checked
again by services. The review transaction prevents stale approvals using a row
lock and version. A reviewer cannot approve their own submission. Rejection and
revocation need a reason. Revocation uses REJECTED so the owner can correct and
resubmit; the audit action distinguishes REVOKE from REJECT.

QR verification is **online database-backed validation**, not a digital signature,
identity proof, or anti-cloning technology. A copied token can still verify while
its associated card is approved. Compare the member's name/ID/portrait with the
holder. Codes are not public web URLs and cannot be verified offline. Camera
integration is not implemented; read a QR image or paste decoded text.

Export authorization checks current approval/account/session immediately before
rendering. A concurrent revocation after that check cannot retract a rendered
image or queued print job. Verify live status when accepting a card. Audit action
AUTHORIZE_EXPORT records authorization, not proof that a file was successfully
written or a printer produced paper.

PDF permission flags depend on the reader honoring them. Password protection
does not prevent screenshots or an authorized reader from disclosing content.
PNG, printed cards and printer spools are unencrypted. The app uses inherited
filesystem permissions and does not claim to set private Windows ACLs. Store
exports in a protected folder; crash recovery can leave `.softsecure-*.part`
files requiring retention-policy cleanup. PDF staging contains encrypted PDF;
PNG staging contains plaintext pixels.

## Direct JDBC trust limitation

The GUI and maintenance utilities run with supplied database credentials. A user
who controls the host can modify the client or use those credentials directly.
Client-side RBAC is a correctness boundary, not sufficient isolation against that
user. Deployment on student-owned devices requires a backend that owns credentials,
validates authorization, rate-limits requests and performs protected operations.
Database and OS administrators can access data/keys; plan separation of duties.

The audit table is append-only from the recommended runtime privilege set, but is
not cryptographically chained or tamper-proof. Shared DB credentials cannot
reliably prove which human ran an operator command. Configure independent operator
and database audit records. Request updates overwrite the current encrypted
profile; old profile versions are not retained for forensic reconstruction.

## Missing or deferred functionality

- OTP/MFA and delivery-provider integration. The old SMS code was not an OTP flow.
- Server-side sessions/authorization, centralized unknown-user throttling and fraud monitoring.
- First-login password-change enforcement, breached-password checks and self-service recovery.
- Hardware-backed keys, vault integration, automated rotation and per-tenant isolation.
- Digitally signed PDFs/QR payloads, card expiry dates and offline verification.
- Independent immutable audit storage, data-retention automation and complete history.
- A verified dependency/CVE inventory, security tests, accessibility certification
  and deployment-specific threat/penetration review.

## Operational response

For exposed credentials, rotate them with the service/database administrator and
invalidate affected sessions using account reset/disable. For compromised card
tokens, revoke and reissue the request. For key compromise, preserve recoverable
backups and plan a full re-encryption/reindexing migration; merely changing an
environment variable is not rotation. Report security issues privately to the
repository owner using their established channel; no contact address is invented.

## Design references

- [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): PBKDF2 parameters.
- [OWASP Cryptographic Storage](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): authenticated encryption and key handling.
- [MariaDB Connector/J documentation](https://mariadb.com/docs/connectors/mariadb-connector-j/about-mariadb-connector-j): driver/TLS configuration.
- [PDFBox encryption example](https://pdfbox.apache.org/2.0/cookbook/encryption.html) and [3.0 migration guide](https://pdfbox.apache.org/3.0/migration.html): API behavior and password/owner distinction.
- [PDFBox downloads](https://pdfbox.apache.org/download.html), [MariaDB releases](https://mariadb.org/connector-java/all-releases/), [ZXing releases](https://github.com/zxing/zxing/releases): dependency provenance. These references are not a vulnerability-scan result.

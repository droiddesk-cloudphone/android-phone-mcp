# Security model and known limitations

The app can read screen content and act on the phone. That is highly sensitive access. Use a dedicated test device without personal accounts, on a trusted network; do not leave the session active when not testing.

## Current design

- The phone connects outbound to an HTTPS server on the developer's machine. The Android app pins the SHA-256 fingerprint of the ephemeral server certificate supplied by the user and does not follow HTTP redirects for these requests.
- The server generates a 256-bit random bearer token for each process. The phone presents that token when polling and returning results. Token and fingerprint appear in server stderr and, if enabled, the owner-only `DD_SESSION_FILE`.
- Pairing requires the user to enable accessibility, enter the connection data and start the session. A persistent notification indicates the active service; the app has a Stop session button.
- The HTTPS server binds all interfaces by default and has no rate limiting or multi-user authorization. Limit access at the network/firewall level and never expose it to the public internet.
- Any party that obtains the session token can impersonate a phone or read pending commands; anyone controlling the local MCP client can control the connected phone. The current server does not distinguish multiple phone connections using the same token.
- Screen data and screenshots can contain sensitive information and are supplied to the MCP client. Whether they are then sent to a model provider depends on the client's behavior. Password nodes are redacted in the accessibility tree, but this is not comprehensive secret detection. Screenshots may still contain sensitive information.
- `screenshot` and interaction capabilities depend on Android and app restrictions; do not attempt to bypass secure windows or platform safeguards.
- A command can complete on the phone even if its result is lost. Do not automatically repeat actions with an unknown outcome.

## Before public production use

Independent security review, authenticated device pairing, device-specific identity and revocation, permission scopes, rate limits, reliable local session lifecycle, robust reconnect behavior, logging policy, privacy review, dependency review and a broader device test matrix are required. Distribution policies and licensing also require review. Do not describe the prototype as audited, zero-knowledge, end-to-end encrypted, or safe for personal accounts.

# Architecture

```text
MCP client / AI agent
       | stdio MCP
       v
Local Node.js server  <-- outbound HTTPS polling -- Android app
       |                                             |
  one pending job                           AccessibilityService
       |                                             |
  random bearer token + pinned ephemeral certificate |
                                                     v
                                        Android screen and apps
```

The server generates an ephemeral TLS certificate and bearer token per process. The app compares the certificate fingerprint entered by the user, then polls `/poll` and posts command results to `/result`. The phone initiates the connection. Only one job is in flight; it has a timeout. If a delivered job times out, the result is unknown and must not be blindly retried. The Android service executes gestures and screen operations, and explicitly activated foreground operation is visible to the user.

The MCP server currently has no device registry, per-tool authorization, queue persistence, reconnect recovery, durable delivery, snapshot IDs, or per-client rate limits. The app does not persist pairing across app restarts. A server restart generates new session credentials. It is intentionally not connected to DroidDesk's production infrastructure or rental logic.

Android's app lifecycle, protected windows, application UI, notification permissions and battery restrictions may limit behavior. The exact Android SDK/build script is documented in the README; see the real-device test scope there.

# Contributing

Thanks for helping improve Android Phone MCP by DroidDesk. This project is experimental.

- Discuss major changes in an issue before beginning substantial work.
- Never include credentials, session files, private screenshots, device identifiers, logs containing personal data, or debug signing keys in a contribution.
- Test phone behavior only on physical devices you own or are authorized to use. Do not use emulators for this project.
- Do not use `am force-stop`: it can disable accessibility services. Use the in-app Stop session button and normal app lifecycle.
- Run `cd server && npm ci && npm test`. For Android changes, build the app and describe real-device testing separately from automated tests.
- Preserve the owner's explicit consent and ability to stop control. Do not weaken the certificate check, token handling, or authentication to simplify setup.
- Document device model, Android version, exact steps, expected result and observed result. Redact all personal data.

By submitting a contribution, you agree to license your contribution under the project license.

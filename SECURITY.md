# Security Policy

## Supported versions

Consensus Weather is a small project; only the latest version receives fixes.

| Version | Supported |
| ------- | --------- |
| Latest release / `main` | ✅ |
| Older builds | ❌ |

## Reporting a vulnerability

**Please don't open a public issue for security problems.**

Report it privately through GitHub's private advisory flow:
**Security → Report a vulnerability**
(or https://github.com/Szebastian31/Consensus_Weather/security/advisories/new).

Prefer email? Contact **[szymon.szygula@gmail.com]**.

Include what you found, how to reproduce it, and the potential impact. I'll
acknowledge within a few days and keep you updated on a fix.

## A note on API keys & secrets

Consensus Weather deliberately uses **keyless** public APIs (Open-Meteo for
forecasts, air quality and geocoding; BigDataCloud for reverse geocoding), so
**there are no credentials in this repo — and there shouldn't be.**

Because the app is fully client-side, anything shipped in `index.html` is visible
to anyone who installs it. That means:

- **Never commit API keys, tokens, or secrets** to the repo or embed them in
  `index.html`. They can't be hidden in a client-side app and would be exposed
  immediately.
- If you add a provider that **requires** a key, it can't be used safely from the
  client directly — it needs a small server-side proxy that holds the key. Please
  raise this in an issue first.

If a secret is ever committed by accident, treat it as compromised: rotate/revoke
it immediately and report it privately using the process above.

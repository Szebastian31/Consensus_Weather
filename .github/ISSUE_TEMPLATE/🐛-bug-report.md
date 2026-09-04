---
name: "\U0001F41B Bug report"
about: Something in Consensus Weather looks wrong or doesn't work
title: ''
labels: ''
assignees: ''

---

title: "[Bug]: "
labels: ["bug"]
body:
  - type: textarea
    id: what-happened
    attributes:
      label: What happened?
      description: A clear description of the bug.
    validations:
      required: true
  - type: textarea
    id: steps
    attributes:
      label: Steps to reproduce
      placeholder: |
        1. Open the app
        2. Add city '...'
        3. See error
    validations:
      required: true
  - type: textarea
    id: expected
    attributes:
      label: Expected vs. actual
      description: What you expected, and what actually happened.
    validations:
      required: true
  - type: input
    id: location
    attributes:
      label: Location queried
      placeholder: "Gliwice, Poland (50.30, 18.68)"
  - type: input
    id: datetime
    attributes:
      label: Date & time (with timezone)
      placeholder: "2026-09-04 14:30 CEST"
  - type: input
    id: sources
    attributes:
      label: Which sources/models were active
      placeholder: "Consensus (all 7 models)"
  - type: dropdown
    id: platform
    attributes:
      label: Platform
      options:
        - Android (APK)
        - iPhone (home-screen web app)
        - Desktop browser
        - Other
    validations:
      required: true
  - type: input
    id: version
    attributes:
      label: App version
      placeholder: "1.2.0 (see build.gradle / footer)"
  - type: textarea
    id: logs
    attributes:
      label: Logs / screenshots
      description: Browser console errors (DevTools) and/or screenshots.
      render: shell

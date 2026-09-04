---
name: "\U0001F4A1 Feature request"
about: Suggest an idea or improvement
title: ''
labels: ''
assignees: ''

---

title: "[Feature]: "
labels: ["enhancement"]
body:
  - type: textarea
    id: problem
    attributes:
      label: Problem / motivation
      description: What problem does this solve? What's frustrating today?
    validations:
      required: true
  - type: textarea
    id: solution
    attributes:
      label: Proposed solution
      description: What you'd like to see happen.
    validations:
      required: true
  - type: textarea
    id: alternatives
    attributes:
      label: Alternatives considered
  - type: textarea
    id: context
    attributes:
      label: Additional context
      description: Mockups, screenshots, links.

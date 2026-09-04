---
name: "\U0001F6F0️ New data source"
about: Propose adding a weather model / provider to the consensus
title: ''
labels: ''
assignees: ''

---

title: "[Source]: "
labels: ["data-source"]
body:
  - type: input
    id: provider
    attributes:
      label: Provider / model name
      placeholder: "UK Met Office"
    validations:
      required: true
  - type: input
    id: modelid
    attributes:
      label: Open-Meteo model id (if applicable)
      description: e.g. ukmo_seamless. Leave blank for a non-Open-Meteo API.
      placeholder: "ukmo_seamless"
  - type: input
    id: coverage
    attributes:
      label: Region / coverage
      placeholder: "Global, or e.g. Europe only"
  - type: textarea
    id: why
    attributes:
      label: Why add it?
      description: What it improves over the current consensus.
    validations:
      required: true
  - type: input
    id: license
    attributes:
      label: Licensing / attribution
      description: Any usage terms or attribution required.

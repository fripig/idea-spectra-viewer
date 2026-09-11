## MODIFIED Requirements

### Requirement: Indicate loading and empty states

While a scan is running, the tool window SHALL indicate that loading is in progress. When the project's resolved spec directory does not exist, the tool window SHALL replace the tree with an empty-state message stating that the project is not initialised for Spectra. The message SHALL NOT name a directory, because the spec directory is resolved from the project's configuration and differs between projects.

#### Scenario: Loading indicator

- **WHEN** a scan is in progress
- **THEN** the tool window indicates that it is loading

#### Scenario: Project without Spectra

- **GIVEN** a project root whose resolved spec directory does not exist
- **WHEN** the tool window is opened
- **THEN** an empty-state message is shown instead of the tree

#### Scenario: A project using the configured spec directory shows the tree

- **GIVEN** a project root holding a `.spectra.yaml` containing `spec_dir: docs/spectra`, and a `docs/spectra/` directory
- **WHEN** the tool window is opened
- **THEN** the tree is shown and the empty-state message is not shown

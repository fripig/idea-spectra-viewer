## MODIFIED Requirements

### Requirement: Report per-change metadata

For each discovered change, the system SHALL report its name, its group, the absolute path of its directory, the list of its Markdown artifact files, its task progress, its derived status, its creation date, its modification date, and its proposer.

The name SHALL be the change directory name. The artifact file list SHALL contain every file with the `.md` extension found beneath the change directory, expressed as a path relative to that change directory, sorted as strings.

The creation date SHALL be parsed from the `created` field of `.openspec.yaml` in the change directory. When that file is absent or unreadable, when it has no `created` field, or when the field's value cannot be parsed as an ISO date, the creation date SHALL be reported as unknown and the change SHALL still be reported.

The proposer SHALL be derived from the `created_by` field of the same `.openspec.yaml`. The reported proposer SHALL be a display name, derived from the field's raw value as follows: when the value contains a `<` character, the display name SHALL be the substring before the first `<`, trimmed of surrounding whitespace; otherwise the display name SHALL be the whole value trimmed of surrounding whitespace. When the resulting display name is empty, when the file has no `created_by` field, or when the file is absent or unreadable, the proposer SHALL be reported as unknown and the change SHALL still be reported. The proposer SHALL NOT include the email address, and SHALL NOT be derived from the `created_with`, `archived_by`, or `archived_at` fields.

Both the creation date and the proposer SHALL be obtained from a single read of `.openspec.yaml`, so that reporting the proposer costs no additional file access.

The modification date SHALL be the most recent modification time among the change's Markdown files. When a file's modification time cannot be read, that file SHALL be excluded from the comparison. When no modification time can be obtained, the modification date SHALL be reported as unknown.

#### Scenario: Artifact files are listed relative to the change directory

- **GIVEN** a change directory containing `proposal.md`, `tasks.md`, and `specs/theme-engine/spec.md`
- **WHEN** a scan reports that change
- **THEN** the artifact list contains exactly `proposal.md`, `specs/theme-engine/spec.md`, and `tasks.md`, in that string order

#### Scenario: Non-Markdown files are excluded

- **GIVEN** a change directory containing `.openspec.yaml` and `proposal.md`
- **WHEN** a scan reports that change
- **THEN** the artifact list contains `proposal.md` and does not contain `.openspec.yaml`

#### Scenario: Creation date is read from the change metadata file

- **GIVEN** a change directory whose `.openspec.yaml` contains the line `created: 2026-08-10`
- **WHEN** a scan reports that change
- **THEN** the creation date is 2026-08-10

#### Scenario: Unusable creation metadata never removes a change

- **WHEN** a change's `.openspec.yaml` is missing, has no `created` field, or has a `created` value that is not an ISO date
- **THEN** the change is reported with an unknown creation date

##### Example: creation metadata cases

| `.openspec.yaml` content        | Creation date | Change reported |
| ------------------------------- | ------------- | --------------- |
| `created: 2026-08-10`           | 2026-08-10    | yes             |
| `schema: spec-driven` only      | unknown       | yes             |
| `created: last Tuesday`         | unknown       | yes             |
| `created:`                      | unknown       | yes             |
| file absent                     | unknown       | yes             |

#### Scenario: Proposer is read from the change metadata file

- **GIVEN** a change directory whose `.openspec.yaml` contains the line `created_by: fripig <fripig@gmail.com>`
- **WHEN** a scan reports that change
- **THEN** the proposer is `fripig`, without the email address

#### Scenario: Unusable proposer metadata never removes a change

- **WHEN** a change's `.openspec.yaml` is missing, has no `created_by` field, or has a `created_by` value whose display name is empty
- **THEN** the change is reported with an unknown proposer

##### Example: proposer display name cases

| `created_by` value            | Proposer      | Change reported |
| ----------------------------- | ------------- | --------------- |
| `fripig <fripig@gmail.com>`   | `fripig`      | yes             |
| `Alice Chen <a@example.com>`  | `Alice Chen`  | yes             |
| `fripig`                      | `fripig`      | yes             |
| `<fripig@gmail.com>`          | unknown       | yes             |
| `created_by:` with no value   | unknown       | yes             |
| field absent                  | unknown       | yes             |
| file absent                   | unknown       | yes             |

#### Scenario: Creation date and proposer are reported together

- **GIVEN** a change directory whose `.openspec.yaml` contains both `created: 2026-08-10` and `created_by: fripig <fripig@gmail.com>`
- **WHEN** a scan reports that change
- **THEN** the creation date is 2026-08-10 and the proposer is `fripig`

#### Scenario: One metadata field being unusable does not affect the other

- **GIVEN** a change directory whose `.openspec.yaml` contains `created: last Tuesday` and `created_by: fripig <fripig@gmail.com>`
- **WHEN** a scan reports that change
- **THEN** the creation date is unknown and the proposer is `fripig`

#### Scenario: Modification date reflects edits to artifact content

- **GIVEN** a change whose Markdown files were last modified at a known time
- **WHEN** one of those files is edited and a new scan runs
- **THEN** the reported modification date is the edited file's new modification time

##### Example: newest Markdown file wins

- **GIVEN** a change containing `proposal.md` modified at 2026-08-11 17:00 and `tasks.md` modified at 2026-08-12 09:00
- **WHEN** a scan reports that change
- **THEN** the modification date is 2026-08-12 09:00

#### Scenario: A change with no Markdown files has no modification date

- **GIVEN** a change directory containing only `.openspec.yaml`
- **WHEN** a scan reports that change
- **THEN** the modification date is unknown

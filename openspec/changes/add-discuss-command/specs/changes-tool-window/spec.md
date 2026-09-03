## MODIFIED Requirements

### Requirement: Send a Spectra command for the selected change

The tool window's context menu SHALL offer a submenu that carries five Spectra commands, each already applied to the selected change: `/spectra-discuss`, `/spectra-apply`, `/spectra-ingest`, `/spectra-archive` and `/spectra-commit`, listed in that order. The order follows the project's workflow: discuss opens a change, apply and ingest alternate while it is being built, archive ends it, and commit lands files after any step, so it is last. The submenu SHALL NOT offer `/spectra-propose`, because that command creates a change rather than acting on an existing one. Each item SHALL display the full command text it produces, so what the user reads is exactly what the item delivers.

The command text SHALL be a single line consisting of the slash command name, one space, and the change name, with no trailing newline. It SHALL NOT include the group the change belongs to, and it SHALL NOT include the task progress counts or the proposer shown on the node.

A terminal target SHALL be available only when all of the following hold: the IDE's terminal plugin is installed and enabled, the Terminal tool window has a selected tab, and that tab's terminal connection is ready. When any of the three does not hold, no terminal target is available.

When a terminal target is available, the submenu title SHALL read `Send to Terminal`, and invoking an item SHALL write that item's command text into the selected terminal tab's input position without appending a newline and without executing it, then activate the Terminal tool window and move focus to that tab. The clipboard contents SHALL remain unchanged.

When no terminal target is available, the submenu title SHALL read `Copy Command`, and invoking an item SHALL write that item's command text to the system clipboard. Focus SHALL NOT move, and no notification SHALL be shown.

The submenu SHALL be enabled only when the selection contains exactly one change node. Group nodes and artifact nodes in the selection SHALL be ignored rather than blocking that count, matching how the Copy action treats them. When the selection contains no change node or more than one, the submenu SHALL be disabled.

Invoking any item SHALL NOT run a file system scan, and the tree SHALL keep its current expansion state, sort order, and filter text.

The existing Copy Change Name item SHALL remain first in the context menu, and its behaviour SHALL be unchanged.

#### Scenario: Send a command to the selected terminal tab

- **GIVEN** the Active group contains a change named `add-search` and the Terminal tool window has a selected tab whose terminal connection is ready
- **WHEN** the user selects that change node and invokes `/spectra-apply add-search` from the submenu
- **THEN** the text `/spectra-apply add-search` appears at that tab's input position, no newline is sent, the command is not executed, the Terminal tool window is activated with focus on that tab, and the clipboard contents remain unchanged

#### Scenario: The user decides whether the command runs

- **GIVEN** a command has been written into the selected terminal tab
- **WHEN** the user presses Enter in that tab
- **THEN** the command is submitted, and until that moment nothing has been executed

#### Scenario: The submenu title names the action that will happen

- **WHEN** the user opens the context menu on a single change node
- **THEN** the submenu title reads `Send to Terminal` if a terminal target is available and `Copy Command` if it is not, while the item texts stay the same in both states

#### Scenario: Fall back to the clipboard when the Terminal tool window has no tab

- **GIVEN** the Terminal tool window is closed or holds no tab
- **WHEN** the user invokes `/spectra-ingest add-search` from the submenu
- **THEN** the clipboard contains exactly `/spectra-ingest add-search`, focus does not move, and no notification is shown

#### Scenario: The plugin works when the terminal plugin is disabled

- **GIVEN** the IDE's terminal plugin is disabled
- **WHEN** the user opens the Spectra tool window and opens the context menu on a change node
- **THEN** the tool window works as before, the submenu title reads `Copy Command`, the submenu is enabled, and invoking an item writes the command text to the clipboard

#### Scenario: Writing to the terminal fails

- **GIVEN** a terminal target is available
- **WHEN** invoking an item raises an error while writing to that terminal
- **THEN** the error is recorded in the IDE log, the command text is written to the clipboard instead, and no dialog is shown

#### Scenario: The submenu is disabled for a multi-node selection

- **WHEN** the user selects two change nodes and opens the context menu
- **THEN** the submenu is shown disabled, nothing is sent, and the clipboard contents remain unchanged

#### Scenario: The submenu is disabled when no change node is selected

- **WHEN** the user selects only a group node, only an artifact node, or nothing at all, and opens the context menu
- **THEN** the submenu is shown disabled, nothing is sent, and the clipboard contents remain unchanged

#### Scenario: Group and artifact nodes do not block a single change selection

- **GIVEN** the selection holds the group Active, the change `add-search`, and the artifact `design.md`
- **WHEN** the user opens the context menu
- **THEN** the submenu is enabled and its items carry the change name `add-search`

#### Scenario: Sending or copying a command does not rescan or rebuild the tree

- **WHEN** the user invokes any item in the submenu
- **THEN** no file system scan runs, and the tree keeps its current expansion state, sort order, and filter text

#### Scenario: The submenu lists the five commands in workflow order

- **WHEN** the user opens the context menu on a single change node named `add-search`
- **THEN** the submenu items read, top to bottom, `/spectra-discuss add-search`, `/spectra-apply add-search`, `/spectra-ingest add-search`, `/spectra-archive add-search`, `/spectra-commit add-search`, and no item for `/spectra-propose` is present

#### Scenario: Copy Change Name is unaffected

- **GIVEN** a single change node named `add-search` is selected
- **WHEN** the user invokes the Copy action or the `Copy Change Name` menu item
- **THEN** the clipboard contains exactly `add-search`, with no slash command and no space-separated prefix

##### Example: command text for each submenu item

- **GIVEN** the selected change is named `add-search`

| Submenu item | Command text produced |
| ------------ | --------------------- |
| `/spectra-discuss add-search` | `/spectra-discuss add-search` |
| `/spectra-apply add-search` | `/spectra-apply add-search` |
| `/spectra-ingest add-search` | `/spectra-ingest add-search` |
| `/spectra-archive add-search` | `/spectra-archive add-search` |
| `/spectra-commit add-search` | `/spectra-commit add-search` |

##### Example: submenu state and destination

| Selection | Terminal target available | Submenu title | Invoking an item |
| --------- | ------------------------- | ------------- | ---------------- |
| one change `add-search` | yes | `Send to Terminal` | text written to the selected tab, focus moves there |
| one change `add-search` | no | `Copy Command` | text written to the clipboard, focus stays |
| changes `add-search` and `zebra-fix` | yes | `Send to Terminal` | submenu disabled, nothing happens |
| group Active only | no | `Copy Command` | submenu disabled, nothing happens |
| group Active, change `add-search`, artifact `design.md` | yes | `Send to Terminal` | text for `add-search` written to the selected tab |
| nothing selected | yes | `Send to Terminal` | submenu disabled, nothing happens |

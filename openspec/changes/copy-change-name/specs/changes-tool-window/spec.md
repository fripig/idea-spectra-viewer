## ADDED Requirements

### Requirement: Copy change names to the clipboard

The tool window SHALL respond to the IDE's standard Copy action by writing the names of the selected changes to the system clipboard as plain text.

The copied text SHALL be the change name alone. It SHALL NOT include the group the change belongs to, and it SHALL NOT include the task progress counts shown on the node.

Only change nodes SHALL be copyable. When the selection contains no change node, the Copy action SHALL be disabled and the clipboard contents SHALL remain unchanged.

When the selection contains several change nodes, the copied text SHALL contain every selected change name, separated by a single newline, in the order the nodes appear in the tree from top to bottom. Group nodes and artifact nodes in the selection SHALL be ignored rather than blocking the copy.

A successful copy SHALL NOT produce a notification or any other visible feedback.

#### Scenario: Copy a single change name

- **GIVEN** the Active group contains a change named `sort-and-filter-changes` whose task progress is 3 of 7
- **WHEN** the user selects that change node and invokes the Copy action
- **THEN** the clipboard contains exactly `sort-and-filter-changes`, without the group name and without the progress counts

#### Scenario: Copy is unavailable on a group node

- **WHEN** the user selects a group node and invokes the Copy action
- **THEN** the clipboard contents remain unchanged and no notification is shown

#### Scenario: Copy is unavailable on an artifact node

- **WHEN** the user selects an artifact node and invokes the Copy action
- **THEN** the clipboard contents remain unchanged and no notification is shown

#### Scenario: Copy several changes at once

- **WHEN** the user selects more than one change node and invokes the Copy action
- **THEN** the clipboard contains every selected change name, one per line, ordered as the nodes appear in the tree

##### Example: selections and their clipboard contents

| Selection (top to bottom) | Clipboard contents |
| ------------------------- | ------------------ |
| change `add-search` | `add-search` |
| changes `add-search`, `zebra-fix` | `add-search` newline `zebra-fix` |
| group Active | unchanged |
| artifact `design.md` | unchanged |
| group Active, change `add-search`, artifact `design.md` | `add-search` |
| nothing selected | unchanged |

#### Scenario: Copying does not rescan or rebuild the tree

- **WHEN** the user invokes the Copy action
- **THEN** no file system scan runs, and the tree keeps its current expansion state, sort order, and filter text

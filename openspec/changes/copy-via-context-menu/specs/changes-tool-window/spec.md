## MODIFIED Requirements

### Requirement: Copy change names to the clipboard

The tool window SHALL respond to the IDE's standard Copy action by writing the names of the selected changes to the system clipboard as plain text.

The tree SHALL also offer a context menu whose only item is labelled `Copy Change Name`, so the same copy is reachable with the mouse alone. The menu item SHALL display the same shortcut as the Copy action, SHALL copy exactly what the Copy action copies, and SHALL be disabled under exactly the same conditions.

When the user opens the context menu on a node that is not part of the current selection, that node SHALL become the selection before the menu appears, so the copy applies to the node under the pointer. When the user opens the context menu on a node that is already part of a multi-node selection, the selection SHALL remain unchanged.

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

#### Scenario: The context menu offers Copy Change Name and nothing else

- **GIVEN** a change node is selected
- **WHEN** the user opens the context menu on that node
- **THEN** the menu contains `Copy Change Name` as its only item, showing the Copy shortcut beside it, and invoking it copies the change name

#### Scenario: The context menu item is disabled when nothing is copyable

- **GIVEN** a group node is selected
- **WHEN** the user opens the context menu on that node
- **THEN** the `Copy Change Name` item is shown disabled and the clipboard contents remain unchanged

#### Scenario: The context menu retargets the selection

- **GIVEN** the change `add-search` is selected
- **WHEN** the user opens the context menu on the unselected change `zebra-fix` and invokes `Copy Change Name`
- **THEN** `zebra-fix` is the selected node and the clipboard contains exactly `zebra-fix`

#### Scenario: The context menu keeps an existing multi-node selection

- **GIVEN** the changes `add-search` and `zebra-fix` are both selected
- **WHEN** the user opens the context menu on `zebra-fix` and invokes `Copy Change Name`
- **THEN** both nodes remain selected and the clipboard contains `add-search` and `zebra-fix`, one per line

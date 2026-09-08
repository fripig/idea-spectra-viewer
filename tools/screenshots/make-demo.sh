#!/bin/zsh
# Builds the demo project the README screenshots are taken from.
# usage: make-demo.sh [target-dir]   (default: tools/screenshots/out/demo-project)
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
D=${1:-$HERE/out/demo-project}
rm -rf "$D" && mkdir -p "$D" && cd "$D"
git init -q -b main
git config user.email demo@example.com
git config user.name Demo

# mk <dir> <author> <created> <done> <total>
mk() {
  local dir=$1 author=$2 date=$3 done=$4 total=$5 name=$(basename "$1")
  mkdir -p "$dir"
  printf 'created: %s\ncreated_by: %s\n' "$date" "$author" > "$dir/.openspec.yaml"
  printf '# Proposal: %s\n\nWhy this change matters and what it delivers.\n' "$name" > "$dir/proposal.md"
  printf '# Design: %s\n\nHow the change is put together.\n' "$name" > "$dir/design.md"
  {
    echo "# Tasks"; echo
    for i in $(seq 1 "$total"); do
      if [ "$i" -le "$done" ]; then echo "- [x] $i. Task $i"; else echo "- [ ] $i. Task $i"; fi
    done
  } > "$dir/tasks.md"
}

mk openspec/changes/add-user-authentication            "Alice Chen <alice@example.com>" 2026-09-01 3 8
mk openspec/changes/improve-search-ranking             "Alice Chen <alice@example.com>" 2026-08-30 0 5
mk openspec/changes/export-report-to-pdf               "Bob Lin <bob@example.com>"      2026-08-27 6 6
mk openspec/changes/archive/2026-08-15-setup-ci-pipeline "Bob Lin <bob@example.com>"    2026-08-10 3 3
mk openspec/changes/archive/2026-08-22-add-audit-log     "Carol Wu <carol@example.com>" 2026-08-18 5 5
mk openspec/changes/archive/2026-08-28-fix-login-timeout "Alice Chen <alice@example.com>" 2026-08-25 4 4
# Parked changes live inside the git directory, which is why this is a generator and not a checked-in tree.
mk .git/spectra-app/changes/dark-mode-theme            "Bob Lin <bob@example.com>"      2026-08-20 4 7
mk .git/spectra-app/changes/migrate-to-postgres        "Carol Wu <carol@example.com>"   2026-08-12 2 9

mkdir -p openspec/changes/add-user-authentication/specs/feature
printf '# Feature spec\n\nUser authentication requirements.\n' > openspec/changes/add-user-authentication/specs/feature/spec.md
cat > openspec/changes/add-user-authentication/tasks.md <<'EOF'
# Tasks

## 1. Data model

- [x] 1.1 Add `users` table migration with email, password hash and status
- [x] 1.2 Create `User` model with password hashing helper
- [x] 1.3 Unit tests for password hashing and verification

## 2. Login flow

- [ ] 2.1 Login form with email and password fields
- [ ] 2.2 Session cookie issued on successful login
- [ ] 2.3 Rate limit failed attempts per IP

## 3. Wrap up

- [ ] 3.1 Update API documentation
- [ ] 3.2 Add end-to-end test for the login flow
EOF

mkdir -p openspec/specs src
printf '# demo-project\n\nA demo project for Spectra Viewer screenshots.\n' > README.md
printf 'fun main() = println("hi")\n' > src/Main.kt
git add -A && git commit -qm "init"

# The tool window sorts by Modified (newest first) by default, so the mtimes fix the row order.
t() { find "$1" -type f -exec touch -t "$2" {} +; touch -t "$2" "$1"; }
t openspec/changes/add-user-authentication 202609011200
t openspec/changes/improve-search-ranking  202608301200
t openspec/changes/export-report-to-pdf    202608271200
t .git/spectra-app/changes/dark-mode-theme     202608201200
t .git/spectra-app/changes/migrate-to-postgres 202608121200
t openspec/changes/archive/2026-08-15-setup-ci-pipeline 202608281200
t openspec/changes/archive/2026-08-22-add-audit-log     202608261200
t openspec/changes/archive/2026-08-28-fix-login-timeout 202608251200

echo "demo project at $D"

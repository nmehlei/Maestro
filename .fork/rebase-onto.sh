#!/usr/bin/env bash
# Moves this fork's commits onto a newer upstream release tag.
#
# usage: .fork/rebase-onto.sh <new-upstream-tag>
#
# Rebases the current branch's commits since the tag in .fork/upstream-tag onto
# <new-upstream-tag> and records the new base. Upstream's own workflows stay deleted:
# conflicts on them are resolved by deleting them again, and workflows upstream added
# are removed in a follow-up commit.
#
# Exit codes: 0 rebased, 2 conflict (rebase aborted, working tree clean), 3 tag not newer.
set -euo pipefail

new=${1:?usage: rebase-onto.sh <new-upstream-tag>}
root=$(git rev-parse --show-toplevel)
marker="$root/.fork/upstream-tag"
old=$(tr -d '[:space:]' < "$marker")

old_version=${old#cli-}
new_version=${new#cli-}
newest=$(printf '%s\n%s\n' "$old_version" "$new_version" | sort -V | tail -1)
if [ "$new_version" = "$old_version" ] || [ "$newest" != "$new_version" ]; then
  echo "$new is not newer than $old"
  exit 3
fi

is_upstream_workflow() {
  case "$1" in
    .github/workflows/fork-*) return 1 ;;
    .github/workflows/*) return 0 ;;
    *) return 1 ;;
  esac
}

export GIT_EDITOR=true
if ! git rebase --onto "$new" "$old" >/dev/null 2>&1; then
  while true; do
    conflicts=$(git diff --name-only --diff-filter=U)
    [ -n "$conflicts" ] || conflicts=$(git status --porcelain | awk '$1 ~ /^(DU|UD|AU|UA|DD|AA|UU)$/ {print $2}')
    other=""
    for path in $conflicts; do
      if is_upstream_workflow "$path"; then git rm -q --ignore-unmatch -- "$path"; else other="$other $path"; fi
    done
    if [ -n "$other" ]; then
      echo "conflict in:$other"
      git rebase --abort
      exit 2
    fi
    git rebase --continue >/dev/null 2>&1 && break
    [ -d "$(git rev-parse --git-path rebase-merge)" ] || [ -d "$(git rev-parse --git-path rebase-apply)" ] || break
  done
fi

added=$(git ls-files .github/workflows | while read -r path; do is_upstream_workflow "$path" && echo "$path"; done || true)
if [ -n "$added" ]; then
  echo "$added" | xargs git rm -q --
  git commit -q -m "Remove upstream workflows added in $new"
fi

printf '%s\n' "$new" > "$marker"
git add "$marker"
git commit -q -m "Rebase onto upstream $new"
echo "rebased onto $new"

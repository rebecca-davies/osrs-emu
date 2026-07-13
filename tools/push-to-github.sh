#!/usr/bin/env bash
# Create a PRIVATE GitHub repo and push everything. Run once you're authenticated.
#
# Authenticate first (either one):
#   gh auth login                      # interactive
#   export GITHUB_TOKEN=ghp_xxx        # a PAT with 'repo' scope
#
# Then:  ./tools/push-to-github.sh [repo-name]
set -euo pipefail

REPO_NAME="${1:-osrsemu}"
cd "$(git rev-parse --show-toplevel)"

echo "Repo name: $REPO_NAME (private)"

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    echo "Using gh CLI…"
    gh repo create "$REPO_NAME" --private --source=. --remote=origin --push
    # push all local branches too
    git push origin --all
    echo "Done. URL: $(gh repo view "$REPO_NAME" --json url -q .url)"
elif [ -n "${GITHUB_TOKEN:-}" ]; then
    echo "Using GitHub API with GITHUB_TOKEN…"
    USER_LOGIN="$(curl -fsSL -H "Authorization: token $GITHUB_TOKEN" https://api.github.com/user | grep -oP '"login":\s*"\K[^"]+' | head -1)"
    echo "Authenticated as: $USER_LOGIN"
    curl -fsSL -X POST -H "Authorization: token $GITHUB_TOKEN" \
        https://api.github.com/user/repos \
        -d "{\"name\":\"$REPO_NAME\",\"private\":true}" >/dev/null
    git remote remove origin 2>/dev/null || true
    git remote add origin "https://${GITHUB_TOKEN}@github.com/${USER_LOGIN}/${REPO_NAME}.git"
    git push -u origin --all
    # scrub the token out of the stored remote URL afterwards
    git remote set-url origin "https://github.com/${USER_LOGIN}/${REPO_NAME}.git"
    echo "Done. URL: https://github.com/${USER_LOGIN}/${REPO_NAME}"
else
    echo "ERROR: not authenticated. Run 'gh auth login' or 'export GITHUB_TOKEN=...' first." >&2
    exit 1
fi

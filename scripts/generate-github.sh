#!/usr/bin/env bash
# Generates actions and `catalog.pkl` files

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

pkl eval --project-dir "$SCRIPT_DIR" "$SCRIPT_DIR"/generate-github-actions.pkl -m "$SCRIPT_DIR"/..
pkl eval --project-dir "$SCRIPT_DIR" "$SCRIPT_DIR"/generate-github-catalog.pkl -m "$SCRIPT_DIR"/..

cd "$SCRIPT_DIR/.."

./gradlew spotlessApply

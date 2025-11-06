#!/usr/bin/env bash -e

# test the files archive.*.pkl in this directory
# evaluate each and ask tar to list the result's contents

cd "$(dirname "$0")"

for file in archive.*.pkl; do
  echo "+ pkl eval $file | tar tv"
  pkl eval $file | tar tv -v
  echo
done

echo "PASS"

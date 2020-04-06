#!/bin/bash
set -e -x
[ -z "$(git status --porcelain)" ] # check if there are unstaged changes

perl -i -p -e 's/-SNAPSHOT$//' version
./gradlew build
git add version
git commit -m "Release $(cat version)"
git tag "v$(cat version)"

perl -i -p -e 's/\.([0-9]+)$/"." . ($1+1) . "-SNAPSHOT"/e' version
git add version
git commit -m "Bump to $(cat version)"
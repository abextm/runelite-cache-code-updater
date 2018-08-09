#!/bin/bash

set -e -x

pushd osrs-flatcache
mvn install
popd

pushd osrs-cache
[[ "$(git log -1 --pretty=%B)" =~ (Cache version )(.*) ]]
VERSION="${BASH_REMATCH[2]}"
popd

mvn install
mvn exec:java -Dexec.args="$VERSION"

pushd runelite
git push origin
popd
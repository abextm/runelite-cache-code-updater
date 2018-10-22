#!/bin/bash

set -e -x

pushd osrs-flatcache
mvn install
popd

pushd osrs-cache
[[ "$(git log -1 --pretty=%B)" =~ (Cache version )(.*) ]]
VERSION="${BASH_REMATCH[2]}"
popd

pushd runelite
START_SHA=$(git rev-parse HEAD)
popd

mvn install
mvn exec:java -Dexec.args="$VERSION"

pushd runelite
git format-patch --stdout "$START_SHA"
if [[ -n ${DO_RELEASE+x} ]]; then
	git push --set-upstream origin "$(git rev-parse --abbrev-ref HEAD)"
fi
popd

pushd static.runelite.net
if [[ -n ${DO_RELEASE+x} ]]; then
	git push --set-upstream origin "$(git rev-parse --abbrev-ref HEAD)"
fi
popd
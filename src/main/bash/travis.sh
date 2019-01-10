#!/bin/bash

set -e -x

if [[ -z ${DO_RELEASE+x} ]]; then
	export NO_PUSH=x
	export CACHE_NEXT=e88fc8fd26e42757d352f813b9ef9a8f10adeae3
	export CACHE_PREVIOUS=e88fc8fd26e42757d352f813b9ef9a8f10adeae3^
	export BRANCH_POINT_RUNELITE=417c19e20944336ba7a04dc4771ebfef41ec9f3f

	mvn install
	mvn exec:java
else
	eval $(ssh-agent)
	set +x
	ssh-add <(echo "$GITHUB_KEY" | base64 -d)
	set -x
	ssh-add -l
	export GIT_SSH=ssh

	mvn install -DskipTests
	mvn exec:java
fi



#!/bin/bash

set -e -x

git clone https://github.com/Abextm/osrs-flatcache.git --depth=1

git clone https://github.com/Abextm/osrs-cache.git ${DO_RELASE+--depth=2}
if [[ -z ${DO_RELEASE+x} ]]; then
pushd osrs-cache
	git checkout e88fc8fd26e42757d352f813b9ef9a8f10adeae3
popd
fi

git clone git@github.com:Abextm/runelite.git
pushd runelite
git remote add upstream https://github.com/runelite/runelite.git
git fetch upstream
git checkout upstream/master
if [[ -z ${DO_RELEASE+x} ]]; then
	git checkout 417c19e20944336ba7a04dc4771ebfef41ec9f3f
fi
popd
#!/bin/bash

set -e -x

git clone https://github.com/Abextm/osrs-flatcache.git --depth=1

git clone https://github.com/Abextm/osrs-cache.git --depth=2

git clone git@github.com:Abextm/runelite.git
pushd runelite
git remote add upstream https://github.com/runelite/runelite.git
git fetch upstream
git checkout upstream/master
popd
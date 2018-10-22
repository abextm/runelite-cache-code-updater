#!/bin/bash

set -e -x

git clone https://github.com/Abextm/osrs-flatcache.git --depth=1

git clone https://github.com/Abextm/osrs-cache.git ${DO_RELASE+--depth=2}
if [[ -z ${DO_RELEASE+x} ]]; then
pushd osrs-cache
	git checkout e88fc8fd26e42757d352f813b9ef9a8f10adeae3
popd
fi

if [[ -n ${DO_RELEASE+x} ]]; then
	if [[ -z ${NO_KEYS+x} ]]; then
		openssl aes-256-cbc -K $encrypted_e9a687e01cca_key -iv $encrypted_e9a687e01cca_iv -in abextm.runelite.pem.enc -out abextm.runelite.pem -d
		chmod 600 abextm.runelite.pem
		ssh-add abextm.runelite.pem
		openssl aes-256-cbc -K $encrypted_79e0d10388c8_key -iv $encrypted_79e0d10388c8_iv -in abextm.static.runelite.net.pem.enc -out abextm.static.runelite.net.pem -d
		chmod 600 abextm.static.runelite.net.pem
		ssh-add abextm.static.runelite.net.pem
	fi
	
	git clone git@github.com:Abextm/runelite.git
	git clone git@github.com:Abextm/static.runelite.net.git
else
	git clone https://github.com/Abextm/runelite.git
	git clone https://github.com/Abextm/static.runelite.net.git
fi

pushd runelite
git remote add upstream https://github.com/runelite/runelite.git
git fetch upstream
git checkout upstream/master
if [[ -z ${DO_RELEASE+x} ]]; then
	git checkout 417c19e20944336ba7a04dc4771ebfef41ec9f3f
fi
popd

pushd static.runelite.net
git remote add upstream https://github.com/runelite/static.runelite.net.git
git fetch upstream
git checkout upstream/gh-pages
popd
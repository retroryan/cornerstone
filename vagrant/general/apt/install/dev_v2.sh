#!/usr/bin/env bash
set -x # echo on

FILENAME=dev_v2.sh

PACKAGES='build-essential git-core tree vim'
CACHE=/cache/apt/$FILENAME

if [ ! -d ${CACHE} ]; then
    mkdir -p ${CACHE}
    apt-get --print-uris --yes install $PACKAGES | grep ^\' | cut -d\' -f2 > ${CACHE}.list
    wget -c -i ${CACHE}.list -P ${CACHE}
fi
sudo dpkg -i ${CACHE}/*

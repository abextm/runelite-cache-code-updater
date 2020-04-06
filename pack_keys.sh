#!/bin/bash

# generate keys with these
# ssh-keygen -t ed25519 -f NAME.key

tar -czvf - .ssh | base64 -w 0 > keys.out
#!/bin/bash

set -e

FILES=`ls regression/toAdd/*`
for filename in $FILES; do
  filename=${filename#regression/toAdd/*}
  aws s3 cp --acl public-read "regression/toAdd/$filename" "s3://files.milaboratory.com/test-data/regression/$filename"
done

echo ""

FILES=`ls regression/toAdd/*`
for filename in $FILES; do
  filename=${filename#regression/toAdd/*}
  echo "if [[ ! -f $filename ]]; then"
  echo "  curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/$filename"
  echo "fi"
done

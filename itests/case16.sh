#!/usr/bin/env bash

#
# Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
#
# Before downloading or accessing the software, please read carefully the
# License Agreement available at:
# https://github.com/milaboratory/mixcr/blob/develop/LICENSE
#
# By downloading or accessing the software, you accept and agree to be bound
# by the terms of the License Agreement. If you do not want to agree to the terms
# of the Licensing Agreement, you must not download or access the software.
#

mkdir -p pa_test
cd pa_test || exit 1

pip3 install numpy==1.22.3 pandas==1.4.2 scipy==1.7.3

for file in ../../src/test/resources/sequences/big/pa_test_data/* ; do
  ln -s ${file} $(basename ${file})
done

python3 ../../itests/case16.py


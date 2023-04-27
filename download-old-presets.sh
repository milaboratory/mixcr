#!/bin/bash
version="$1"

set -e pipefail

rm -rf "build/old_presets_temp"
mkdir "build/old_presets_temp"
cd "build/old_presets_temp"

wget https://github.com/milaboratory/mixcr/releases/download/v${version}/mixcr-${version}.zip
unzip mixcr-${version}.zip

presets=`./mixcr -Xmx256m listPresets | cut -f 1 -d ' ' | awk 'NF' | grep -v PresetName | grep -v LINFO`

mkdir exported_presets

for preset in $presets; do
  echo -e "Exporting ${preset}\033[K\r\c"
  mixcr -Xmx256m exportPreset --preset-name $preset --no-validation exported_presets/${preset}.yaml
done
echo

echo "Postprocessing"
mkdir ${version}

for preset in $presets; do
  echo "\"${preset}-legacy-v${version}\":" >> ${version}/${preset}.yaml
  echo "  deprecation: \"It is preset from old version of MiXCR\"" >> ${version}/${preset}.yaml
  sed -e 's/^/  /' exported_presets/${preset}.yaml >> ${version}/${preset}.yaml
done

tar -zcvf ${version}.tar.gz ${version}

cp ${version}.tar.gz ../../old_presets/${version}.tar.gz

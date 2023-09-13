#!/bin/bash

set -e
set -o pipefail

# Linux readlink -f alternative for Mac OS X
function readlinkUniversal() {
  targetFile=$1

  cd $(dirname $targetFile)
  targetFile=$(basename $targetFile)

  # iterate down a (possible) chain of symlinks
  while [ -L "$targetFile" ]; do
    targetFile=$(readlink $targetFile)
    cd $(dirname $targetFile)
    targetFile=$(basename $targetFile)
  done

  # compute the canonicalized name by finding the physical path
  # for the directory we're in and appending the target file.
  phys_dir=$(pwd -P)
  result=$phys_dir/$targetFile
  echo $result
}

os=$(uname)

dir=""

case $os in
Darwin)
  dir=$(dirname "$(readlinkUniversal "$0")")
  ;;
Linux)
  dir="$(dirname "$(readlink -f "$0")")"
  ;;
FreeBSD)
  dir=$(dirname "$(readlinkUniversal "$0")")
  ;;
*)
  echo "Unknown OS."
  exit 1
  ;;
esac

mkdir -p $dir/src/test/resources/sequences/big/
cd $dir/src/test/resources/sequences/big/

if [ "${1}" == "unit" ];then
  if [[ ! -d yf_sample_data ]]; then
    curl -sS https://s3.amazonaws.com/files.milaboratory.com/test-data/yf_sample_data.tar | tar -xv
  fi
elif [ "${1}" == "int" ] || [ "${1}" == "reg"  ]; then
  if [ "${1}" == "int" ]; then
    if [[ ! -f CD4M1_test_R1.fastq.gz ]]; then
        curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/CD4M1_test_R1.fastq.gz
    fi

    if [[ ! -f CD4M1_test_R2.fastq.gz ]]; then
        curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/CD4M1_test_R2.fastq.gz
    fi

    if [[ ! -f subset_B004-7_S247_L001_I1_001.fastq.gz ]]; then
        curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/subset_B004-7_S247_L001_I1_001.fastq.gz
    fi
    if [[ ! -f subset_B004-7_S247_L001_I2_001.fastq.gz ]]; then
        curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/subset_B004-7_S247_L001_I2_001.fastq.gz
    fi
    if [[ ! -f subset_B004-7_S247_L001_R1_001.fastq.gz ]]; then
        curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/subset_B004-7_S247_L001_R1_001.fastq.gz
    fi
    if [[ ! -f subset_B004-7_S247_L001_R2_001.fastq.gz ]]; then
        curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/subset_B004-7_S247_L001_R2_001.fastq.gz
    fi

    mkdir -p regression
    cd regression
    files=$(cat $dir/regression/list)

    for file in $files; do
      if [[ ! -f $file ]]; then
        curl -sS -O "https://s3.amazonaws.com/files.milaboratory.com/test-data/regression/$file"
      fi
    done

    cd ../
  fi

#  if [[ ! -d pa_test_data ]]; then
#    curl -sS https://s3.amazonaws.com/files.milaboratory.com/test-data/pa_test_data.tar | tar -xv
#  fi

  if [[ ! -f single_cell_vdj_t_subset_R1.fastq.gz ]]; then
      curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/single_cell_vdj_t_subset_R1.fastq.gz
  fi

  if [[ ! -f single_cell_vdj_t_subset_R2.fastq.gz ]]; then
      curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/single_cell_vdj_t_subset_R2.fastq.gz
  fi

  mkdir -p trees_samples
  cd trees_samples
  if [[ ! -f MRK_p02_Bmem_1_CGTACTAG-AAGGAGTA_L00M_R1.fastq.gz ]]; then
    curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/MRK_p02_Bmem_1_CGTACTAG-AAGGAGTA_L00M_R1.fastq.gz
  fi
  if [[ ! -f MRK_p02_Bmem_1_CGTACTAG-AAGGAGTA_L00M_R2.fastq.gz ]]; then
    curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/MRK_p02_Bmem_1_CGTACTAG-AAGGAGTA_L00M_R2.fastq.gz
  fi
  if [[ ! -f MRK_p03_Bmem_1_TCCTGAGC-GTAAGGAG_L00M_R1.fastq.gz ]]; then
    curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/MRK_p03_Bmem_1_TCCTGAGC-GTAAGGAG_L00M_R1.fastq.gz
  fi
  if [[ ! -f MRK_p03_Bmem_1_TCCTGAGC-GTAAGGAG_L00M_R2.fastq.gz ]]; then
    curl -sS -O https://s3.amazonaws.com/files.milaboratory.com/test-data/MRK_p03_Bmem_1_TCCTGAGC-GTAAGGAG_L00M_R2.fastq.gz
  fi
  cd ../
else
  echo "Data pre-processing skipped for: ${1}"
  exit 0
fi

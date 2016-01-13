#/bin/bash

mixcr="mixcr"

while [[ $# > 0 ]]
do
    key="$1"
    shift
    case $key in
        -mixcr)
            mixcr="$1"
            shift
            ;;
        *)
            echo "Unknown option: ${key}" >2
            exit 1
            ;;
    esac
done

type wget >/dev/null 2>&1 || { echo >&2 "This script requires \"wget\". Try \"brew install wget\" or \"apt-get install wget\"." ; exit 1; }
type pup >/dev/null 2>&1 || { echo >&2 "This script requires \"pup\". Try \"brew install https://raw.githubusercontent.com/EricChiang/pup/master/pup.rb\" or \"go get github.com/ericchiang/pup\"." ; exit 1; }
type xmllint >/dev/null 2>&1 || { echo >&2 "This script requires \"xmllint\". Try \"sudo apt-get install libxml2-utils\"." ; exit 1; }

echo "By using this script you agree to the terms of use of IMGT website. (see http://www.imgt.org/ for details)."
echo -n "Press ENTER to continue or other key to exit..."
read -n 1 c
if [[ "$c" != "" ]]; then
  echo ""
  exit 1;
fi

wg="wget --load-cookies imgt-cookies.txt --save-cookies imgt-cookies.txt -qO-"

speciesA=()

while read sp;
do
  speciesA+=("$sp")
done < <($wg 'http://imgt.org/genedb/' | pup '#Species option attr{value}' | grep -v any )

speciesCount="${#speciesA[@]}"

echo "Available species:"
for i in $(seq 0 $((speciesCount-1)));
do
  echo "($i) ${speciesA[$i]}"
done

read -p "Please select species (e.g. '5' for ${speciesA[5]}): " speciesId
species=${speciesA[$speciesId]}
echo "You selected: ${species}."
read -p "Please enter a list of common species names for ${species} delimited by ':' to be used in -s option in 'mixcr align ...' (e.g. 'hsa:hs:homosapiens:human'): " commonNames

echo -n "Getting taxonId for ${species} from NCBI... "

prefix='http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=taxonomy&term='
url=$(echo ${species} | sed 's/ /%20/g')
url="${prefix}${url}"
taxonId=$(wget -qO- "$url" | xmllint --xpath '/eSearchResult/IdList/Id/text()' -)
echo "OK. TaxonId=${taxonId}"

echo "Creating directory for downloaded files (./imgt_downloads/)"
mkdir -p ./imgt_downloads/

speciesNoSpaces=$(echo ${species} | sed 's/ /_/g')
filePrefix="./imgt_downloads/${speciesNoSpaces}_"

echo "Downloading files:"
genes=("IGHV" "IGHD" "IGHJ" "IGKV" "IGKJ" "IGLV" "IGLJ" "TRAV" "TRAJ" "TRBV" "TRBD" "TRBJ" "TRDV" "TRDD" "TRDJ" "TRGV" "TRGJ")
loci=("IGH" "IGK" "IGL" "TRA" "TRB" "TRG" "TRD")
for gene in ${genes[@]};
do
  url="http://www.imgt.org/IMGT_GENE-DB/GENElect?query=7.14+${gene}&species=${species}"
  file="${filePrefix}${gene}.fasta"
  $wg ${url} | pup -p 'pre:last-of-type' | sed "/^$/d" | sed "/<.*pre>/d" | sed 's/ *//' > ${file}
  if [[ ! -s ${file} ]];
  then
    echo "${file} is empty."
    locus=${gene:0:3}
    if [[ "${gene:3:4}" != "D" ]];
    then
      loci=("${loci[@]/$locus}")
    fi
  else
    echo "${file} successfully downloaded."
  fi
done

echo "Importing loci:"

for locus in ${loci[@]}
do
  echo ${locus}
  comm="${mixcr} importSegments -f -s ${taxonId}:${commonNames} -l ${locus} -r report_${speciesNoSpaces}_${locus}.txt"
  for gene in $(echo ${genes[@]} | tr ' ' '\n' | grep $locus)
  do
    geneLower=$(echo $gene | tr '[:upper:]' '[:lower:]')
    file="${filePrefix}${gene}.fasta"
    comm="${comm} -${geneLower:3:4} ${file}"
  done
  $comm
done

sParam=$(echo ${commonNames} | sed 's/:.*$//')
if [ -z "$sParam" ] ; then
  sParam="$taxonId"
fi

echo ""
echo "To use imported segments invoke mixcr with the following parameters:"
echo "mixcr align --library local -s ${sParam} ..."
echo ""

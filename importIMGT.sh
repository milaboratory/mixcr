#/bin/bash

echo "By using this script you agree to the terms of use of IMGT website. (see http://www.imgt.org/ for details)."
echo -n "Press ENTER to continue or other key to exit..."
read -n 1 c
if [[ "$c" != "" ]]; then
  echo ""
  exit 1;
fi

type pup >/dev/null 2>&1 || { echo >&2 "This script requires \"pup\". Try \"brew install https://raw.githubusercontent.com/EricChiang/pup/master/pup.rb\" or \"go get github.com/ericchiang/pup\"." ; exit 1; }

wg="wget --load-cookies imgt-cookies.txt --save-cookies imgt-cookies.txt -qO-"

speciesA=()

while read sp;
do
  speciesA+=("$sp")
done < <($wg 'http://imgt.org/genedb/' | pup '#Species option attr{value}')

speciesCount="${#speciesA[@]}"

echo "Available species:"
for i in $(seq 0 $((speciesCount-1)));
do
  echo "($i) ${speciesA[$i]}"
done

read -p "Please select species (e.g. '6' for ${speciesA[6]}): " speciesId
# speciesId=21

species=${speciesA[$speciesId]}
echo "You selected: ${species}."
echo -n "Getting taxonId from NCBI... "

prefix='http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=taxonomy&term='
url=$(echo ${species} | sed 's/ /%20/g')
url="${prefix}${url}"
taxonId=$(wget -qO- "$url" | xmllint --xpath '/eSearchResult/IdList/Id/text()' -)
echo "TaxonId=${taxonId}"

read -p "Please enter a list of short species names delimited by ':' to be used in -s option in 'mixcr align ...' (e.g. 'hsa:hs:homosapiens:human'): " commonNames

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
  comm="mixcr importSegments -f -s ${taxonId}:${commonNames} -l ${locus} -r report_${speciesNoSpaces}_${locus}.txt"
  for gene in $(echo ${genes[@]} | tr ' ' '\n' | grep $locus)
  do
    geneLower=$(echo $gene | tr '[:upper:]' '[:lower:]')
    file="${filePrefix}${gene}.fasta"
    comm="${comm} -${geneLower:3:4} ${file}"
  done
  $comm
done
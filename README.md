[![image](https://img.shields.io/badge/documentation-v4.x-brightgreen)](https://docs.milaboratories.com)


![MiXCR logo](./doc/_static/MiXCR_logo_dark.png#gh-light-mode-only)
![MiXCR logo](./doc/_static/MiXCR_logo_white.png#gh-dark-mode-only)

MiXCR is a universal software for fast and accurate analysis of raw T- or B- cell receptor repertoire sequencing data. It works with any kind of sequencing data:
 - Bulk repertoire sequencing data with or without UMIs
 - Single cell sequencing data including but not limited to 10x Genomics protocols 
 - RNA-Seq or any other kind of fragmented/shotgun data which may contain just a tiny fraction of target sequences
 - and any other kind of sequencing data containing TCRs or BCRs 

Powerful downstream analysis tools allow to obtain vector plots and tabular results for multiple measures. Key features include:
 - Ability to group samples by metadata values and compare repertoire features between groups 
 - Comprehensive repertoire normalization and filtering 
 - Statistical significance tests with proper p-value adjustment
 - Repertoire overlap analysis
 - Vector plots output (.svg / .pdf)
 - Tabular outputs

Other key features:

- Clonotype assembly by arbitrary gene feature, including *full-length* variable region
- PCR / Sequencing error correction with or without aid of UMI or Cell barcodes
- Robust and dedicated aligner algorithms for maximum extraction with zero false-positive rate
- Supports any custom barcode sequences architecture (UMI / Cell)
- _Human_, _Mice_, _Rat_, _Spalax_, _Alpaca_, _Monkey_
- Support IMGT reference
- Barcodes error-correction
- Adapter trimming
- Optional CDR3 reconstruction by assembling overlapping fragmented sequencing reads into complete CDR3-containing contigs when the read position is floating (e.g. shotgun-sequencing, RNA-Seq etc.)
- Optional contig assembly to build longest possible TCR/IG sequence from available data (with or without aid of UMI or Cell barcodes) 
- Comprehensive quality control reports provided at all the steps of the pipeline
- Regions not covered by the data may be imputed from germline
- Exhaustive output information for clonotypes and alignments:
    - nucleotide and amino acid sequences of all immunologically relevant regions (FR1, CDR1, ..., CDR3, etc..)
    - identified V, D, J, C genes
    - comprehensive information on nucleotide and amino acid mutations
    - positions of all immunologically relevant points in output sequences
    - and many more informative columns
- Ability to backtrack fate of each raw sequencing read through the whole pipeline 

See full documentation at [https://docs.milaboratories.com](https://docs.milaboratories.com).

## Who uses MiXCR 
MiXCR is used by 8 out of 10 world leading pharmaceutical companies in the R&D for:
- Vaccine development
- Antibody discovery
- Cancer immunotherapy research

Widely adopted by academic community with 1000+ citations in peer-reviewed scientific publications.

## Installation / Download

### Using Homebrew on Mac OS X or Linux (linuxbrew)

    brew install milaboratory/all/mixcr
    
to upgrade already installed MiXCR to the newest version:

    brew update
    brew upgrade mixcr

### Conda

We maintain [Anaconda repository](https://anaconda.org/milaboratories/mixcr) to simplify installation of MiXCR using `conda` package manager. To install latest stable MiXCR build with conda run:

```
conda install -c milaboratories mixcr
```

to install a specific version run:

```
conda install -c milaboratories mixcr=3.0.12
```

`mixcr` package specifies `openjdk` as a dependency, if you already have Java installed on your system, it might be a good idea to prevent conda from installing another copy of JDK, to do that use `--no-deps` flag:

```
conda install -c milaboratories mixcr --no-deps
```

### Docker

Official MiXCR Docker repository is hosted on the GitHub along with this repo.

Example:

```
docker run --rm \
    -e MI_LICENSE="...license-token..." \
    -v /path/to/raw/data:/raw:ro \
    -v /path/to/put/results:/work \
    ghcr.io/milaboratory/mixcr/mixcr:latest \
    align -s hs /raw/data_R1.fastq.gz /raw/data_R2.fastq.gz alignments.vdjca 
```

#### Tags

The docker repo provides pre-built docker images for all release versions of MiXCR starting from 1.1. Images come in two flavours: "mixcr only" (i.e tag `4.0.0`) and co-bundled "mixcr + imgt reference" (i.e. tag `4.0.0-imgt`), for the latter please see the license note below. All bundled versions before and including `4.0.0` contain IMGT reference version `202214-2` from [here](https://github.com/repseqio/library-imgt/releases/tag/v8), this might be different from the images from the previous official docker registry on Docker Hub (which is now deprecated and planned for removal).

See [docker packages](https://github.com/milaboratory/mixcr/pkgs/container/mixcr%2Fmixcr) page for the full list of tags including development builds.

#### Setting the license

There are several ways to pass the license for mixcr when executed inside a container:

1. Using environment variable:
   
   ```
   docker run \
       -e MI_LICENSE="...license-token..." \
       ....
   ```

2. Using license file:

   ```
   docker run \
       -v /path/to/mi.license:/opt/mixcr/mi.license:ro \
       ....
   ```

3. If it is hard to mount `mi.license` file into already populated folder `/opt/mixcr/` (i.e. in Kubernetes or with other container orchestration tools), you can tell MiXCR where to look for it:

   ```
   docker run \
       -v /path/to/folder_with_mi_license:/secrets:ro \
       -e MI_LICENSE_FILE="/secrets/milicense.txt" \
       ....
   ```

#### Migration from the previous docker images

New docker images define `mixcr` startup script as an entrypoint of the image, compared to the previous docker repo where `bash` was used instead. So, what previously was executed this way:

```
docker run ... old/mixcr/image/address:with_tag mixcr align ...
```

now will be

```
docker run ... new/mixcr/image/address:with_tag align ...
```

For those who rely on other tools inside the image, beware, new build relies on a different base image and has slightly different layout.

`mixcr` startup script is added to `PATH` environment variable, so even if you specify custom entrypoint, there is no need in using of full path to run `mixcr`. 

#### License notice for IMGT images

Images with IMGT reference library contain data imported from IMGT and is subject to terms of use listed on http://www.imgt.org site.

Data coming from IMGT server may be used for academic research only, provided that it is referred to IMGT&reg;, and cited as "IMGT&reg;, the international ImMunoGeneTics information system&reg; http://www.imgt.org (founder and director: Marie-Paule Lefranc, Montpellier, France)."

References to cite: Lefranc, M.-P. et al., Nucleic Acids Research, 27, 209-212 (1999) Cover of NAR; Ruiz, M. et al., Nucleic Acids Research, 28, 219-221 (2000); Lefranc, M.-P., Nucleic Acids Research, 29, 207-209 (2001); Lefranc, M.-P., Nucleic Acids Res., 31, 307-310 (2003); Lefranc, M.-P. et al., In Silico Biol., 5, 0006 (2004) [Epub], 5, 45-60 (2005); Lefranc, M.-P. et al., Nucleic Acids Res., 33, D593-D597 (2005) Full text, Lefranc, M.-P. et al., Nucleic Acids Research 2009 37(Database issue): D1006-D1012; doi:10.1093/nar/gkn838 Full text.

### Manual install (any OS)

* download the latest stable MiXCR build from [release page](https://github.com/milaboratory/mixcr/releases/latest)
* unzip the archive
* add resulting folder to your ``PATH`` variable
  * or add symbolic link for ``mixcr`` script to your ``bin`` folder
  * or use MiXCR directly by specifying full path to the executable script

#### Requirements

* Any OS with Java support (Linux, Windows, Mac OS X, etc..)
* Java 1.8 or higher

## Obtaining a license

To run MiXCR one needs a license file. MiXCR is free for academic users with no commercial funding. We are committed to support academic community and provide our software free of charge for scientists doing non-profit research.

Academic users can quickly get a license at https://licensing.milaboratories.com.

Commercial trial license may be requested at https://licensing.milaboratories.com or by email to licensing@milaboratories.com.

To activate the license do one of the following:

- put `mi.license` to
    - `~/.mi.license`
    - `~/mi.license`
    - directory with `mixcr.jar` file
    - directory with MiXCR executable
    - to any place and specify it in `MI_LICENSE_FILE` environment variable
- put `mi.license` content to `MI_LICENSE` environment variable
- run `mixcr activate-license` and paste `mi.license` content to the command prompt

## Usage & documentation

See usage examples and detailed documentation at https://docs.milaboratories.com

If you haven't found the answer to your question in the docs, or have any suggestions concerning new features, feel free to create an issue here, on GitHub, or write an email to support@milaboratory.com .

## License

Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved

Before downloading or accessing the software, please read carefully the
License Agreement available at:
https://github.com/milaboratory/mixcr/blob/develop/LICENSE

By downloading or accessing the software, you accept and agree to be bound
by the terms of the License Agreement. If you do not want to agree to the terms
of the Licensing Agreement, you must not download or access the software.


## Cite

* Dmitriy A. Bolotin, Stanislav Poslavsky, Igor Mitrophanov, Mikhail Shugay, Ilgar Z. Mamedov, Ekaterina V. Putintseva, and Dmitriy M. Chudakov. "MiXCR: software for comprehensive adaptive immunity profiling." *Nature methods* 12, no. 5 (**2015**): 380-381.
  
  \
  (Files referenced in this paper can be found [here](https://github.com/milaboratory/mixcr/blob/develop/doc/paper/paperAttachments.md).)

  

* Dmitriy A. Bolotin, Stanislav Poslavsky, Alexey N. Davydov, Felix E. Frenkel, Lorenzo Fanchi, Olga I. Zolotareva, Saskia Hemmers, Ekaterina V. Putintseva, Anna S. Obraztsova, Mikhail Shugay, Ravshan I. Ataullakhanov, Alexander Y. Rudensky, Ton N. Schumacher & Dmitriy M. Chudakov. "Antigen receptor repertoire profiling from RNA-seq data." *Nature Biotechnology* 35, 908–911 (**2017**)



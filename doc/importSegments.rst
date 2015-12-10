.. _ref-importSegments:

.. |br| raw:: html

   <br />

.. tip::

  If you want to impoert reference V, D and J sequences from IMGT, see :ref:`importIMGT.sh <ref-auto-imgt>` script documentation.

Import of V, D and J gene sequences from a file
===============================================

If you need to analyse data from species that are not covered by MiXCR built-it reference V, D, J genes library, or you just want to use alternative reference library, you can convert specially formatted fasta files to MiXCR loci-library format by using ``importSegments`` action.

Here is the examaple command:

::

    mixcr importSegments -p imgt -v human_TRBV.fasta -j human_TRBJ.fasta \
          -d human_TRBD.fasta -l TRB -s 9606:hs -r report.txt

This command will import IMGT formatted fasta files (like those that can be downloade on this_ page) and import it to a local loci library file (stored in ``~/.mixcr/local.ll``).

.. _this: http://www.imgt.org/vquest/refseqh.html

Command line parameters
-----------------------

Here is the list of command line parameters for ``importSegments`` action:

+------------------------------------+-------------------------------------------------------------------+
| Option                             | Description                                                       |
+====================================+===================================================================+
| ``-p {params}`` |br|               | select the parameters of import. Parameters determine how to      |
| ``--parameters {params}``          | parse fasta headers and how to extract information about anchor   |
|                                    | points (e.g. using specific positions in sequences with IMGT gaps |
|                                    | or searching for a specific patterns in gene seqeuence).          |
|                                    | |br| |br| currently, the only possible value is ``imgt``          | 
+------------------------------------+-------------------------------------------------------------------+
| ``-v {file}``                      | specify fasta-formatted file with sequences ov V genes            |
+------------------------------------+-------------------------------------------------------------------+
| ``-d {file}``                      | specify fasta-formatted file with sequences ov D genes            |
+------------------------------------+-------------------------------------------------------------------+
| ``-j {file}``                      | specify fasta-formatted file with sequences ov J genes            |
+------------------------------------+-------------------------------------------------------------------+
| ``-l {locus}`` |br|                | determines which immunological locus data is being imported       |
| ``--locus {locus}``                | |br| |br|                                                         |
|                                    | possible values: ``TRA``, ``TRB``, ``TRG``, ``TRD``,              |
|                                    | ``IGH``, ``IGL``, ``IGK``                                         |
+------------------------------------+-------------------------------------------------------------------+
| ``-s {taxonID:commName1:..}`` |br| | specify NCBI Taxonomy ID (e.g. 9606 for human) and a list of      |
| ``--species {...}``                | common species names for organism to be imported |br| |br|        |
|                                    | example: ``9606:hs:hsa:human:homsap``                             |
+------------------------------------+-------------------------------------------------------------------+
| ``-r {reportFile}`` |br|           | specify report file. |br| Report contains comprehancive error and |
| ``--report {reportFile}``          | warning log of importing procedure and amino-acid and nucleotide  |
|                                    | alignments of allelic variants imported from file, along with     |
|                                    | information ot infered positions of anchor points for all         |
|                                    | imported genes (see below)                                        |
+------------------------------------+-------------------------------------------------------------------+
| ``-f``                             | force overwrite already existing locus records in the output file |
+------------------------------------+-------------------------------------------------------------------+


Report file
^^^^^^^^^^^

It is very important to manually check results of importing, as this process involves several empirical steps like search of an anchor points using patterns in the sequence. MiXCR produces comprehansive report file with errors and warnings arised during importing and well-formatted nucleotide and amino acid alignments of allelic variants of V, D and J genes which are marked up with anchor points, so any mistakes can be easily detected.

Here is the example report file record:

.. raw:: html

    <pre style="font-size: 10px">

    TRBV4-1
    =======
                        &lt;FR1                                                                      FR1&gt;&lt;C
     TRBV4-1*01 [F]   0 GACACTGAAGTTACCCAGACACCAAAACACCTGGTCATGGGAATGACAAATAAGAAGTCTTTGAAATGTGAACAACATAT 79
     TRBV4-1*02 [F]   0                                                                               .. 1

                        DR1     CDR1&gt;&lt;FR2                                           FR2&gt;&lt;CDR2        CDR
     TRBV4-1*01 [F]  80 GGGGCACAGGGCTATGTATTGGTACAAGCAGAAAGCTAAGAAGCCACCGGAGCTCATGTTTGTCTACAGCTATGAGAAAC 159
     TRBV4-1*02 [F]   2 ............A................................................................... 81

                        2&gt;&lt;FR3
     TRBV4-1*01 [F] 160 TCTCTATAAATGAAAGTGTGCCAAGTCGCTTCTCACCTGAATGCCCCAACAGCTCTCTCTTAAACCTTCACCTACACGCC 239
     TRBV4-1*02 [F]  82 ................................................................................ 161

                                                  FR3&gt;&lt;CDR3          V&gt;
     TRBV4-1*01 [F] 240 CTGCAGCCAGAAGACTCAGCCCTGTATCTCTGCGCCAGCAGCCAAGA 286
     TRBV4-1*02 [F] 162 ..............................................- 207


     **********

                       &lt;FR1                  FR1&gt;CDR1&gt;&lt;FR2         FR2&gt;&lt;CDR2&gt;&lt;FR3
     TRBV4-1*01 [F]  0 DTEVTQTPKHLVMGMTNKKSLKCEQHMGHRAMYWYKQKAKKPPELMFVYSYEKLSINESVPSRFSPECPNSSLLNLHLHA 79
     TRBV4-1*02 [F]  0                           ...................................................... 53

                             FR3&gt;&lt;CDR3
     TRBV4-1*01 [F] 80 LQPEDSALYLCASSQ_ 95
     TRBV4-1*02 [F] 54 ................ 69

    </pre>

.. _ref-auto-imgt:

Automated import of reference sequences from IMGT
-------------------------------------------------

To simplify import of IMGT reference sequences we developed an interactive bash script that will automatically download and import all possible reference sequences for a selected species.

The sctipt named ``importIMGT.sh`` can be found in the root folder of MiXCR distribution zip file.

Script has the following dependacies:

- wget
- pup (see installation instractions here_)

.. _here: https://github.com/EricChiang/pup#install

To use the script, just execute it from any folder to where you have a write access:

::
    
    /path/to/unzipped/mixcr/importIMGT.sh

It will ask you to accept the copyright rules of IMGT website, to select a species and to provide it's common names. After doing this, script will automatically download all required files from IMGT website and import them to a local loci library.


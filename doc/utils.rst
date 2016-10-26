.. |br| raw:: html

   <br />

.. _ref-align:

Utility actions
===============


Extend alignments 
-----------------

.. danger::

    This procedure introduces non-input-data derived sequences, and potentially can create false-positive clonotypes/alignments that are not present in original sample.


``mixcr extendAlignments`` extends target sequencing reads by infering absent parts from germline refernece sequences. Extension is performed up to CDR3 by default.

Command options:

:: 

    -c, --chains
       Apply procedure only to alignments with specific immunological-receptor
       chains.
       Default: TCR
    -f, --force
       Force overwrite of output file(s).
    -h, --help
       Displays help for this command.
       Default: false
    --j-anchor
       J extension anchor point
       Default: CDR3End
    -q, --quality
       Quality score of extended sequence.
       Default: 30
    -r, --report
       Report file.
    --v-anchor
       V extension anchor point
       Default: CDR3Begin


Consider the following example:

::

    > mixcr align -p rna-seq inpup_R1.fastq.gz input_R2.fastq.gz alignments.vdjca


::

    > mixcr exportAlignmentsPretty alignments.vdjca

::

    ....

    >>> Read id: 557392

                                   FR3><CDR3 V>   <J
         Quality     666777777777788888888888888888888888888888888778
         Target0   0 GACTCTGCAGTGTACTTCTGTGCAGCTTGGAACCCGAATTCCGGGTAT 47   Score
    TRAV29DV5*00 327 gactctgcagtgtacttctgtgcagc                       352  130
       TRAJ41*00  20                              gaacTcAaattccgggtat 38   63


    Quality   887778888888888888888888888888888887777777777666
    Target1 0 CAGAACCCTGACCCTGCCGTGTACCAGCTGAGAGACTCTAAATCCAGT 47  Score
    TRAC*00 5 cagaaccctgaccctgccgtgtaccagctgagagactctaaatccagt 52  240

    ....


    >>> Read id: 5663229


        Quality     666777777777788888878888888888888888888888888888
        Target0   0 GATGATTCAGGGATGCCCGAGGATCGATTCTCAGCTAAGATGCCTAAT 47   Score
    TRBV12-3*00 222 gatgattcagggatgcccgaggatcgattctcagctaagatgcctaat 269  240
    TRBV12-4*00 222 gatgattcagggatgcccgaggatcgattctcagctaagatgcctaat 269  240

                                V>     <J           CDR3><FR4
        Quality     887888788888888878888788888888888887777777777666
        Target1   0 TGCCAGCAGTTTAGGGCCTAACACTGAAGCTTTCTTTGGACAAGGCAC 47   Score
    TRBV12-3*00 332 tgccagcagtttag                                   345  70
    TRBV12-4*00 332 tgccagcagtttag                                   345  70
     TRBJ1-1*00  22                    aacactgaagctttctttggacaaggcac 50   145

    ...


One can see that in both cases CDR3 is not covered by sequencing reads.


::

    > mixcr extendAlignments alignments.vdjca alignments_extended.vdjca


::

    > mixcr exportAlignmentsPretty alignments_extended.vdjca


::

    ....


    >>> Read id: 557392

                                   FR3><CDR3 V>   <J                        CDR3>
         Quality     666777777777788888888888888888888888888888888778666666666666
         Target0   0 GACTCTGCAGTGTACTTCTGTGCAGCTTGGAACCCGAATTCCGGGTATGCACTCAACTTC 59   Score
    TRAV29DV5*00 327 gactctgcagtgtacttctgtgcagc                                   352  130
       TRAJ41*00  20                              gaacTcAaattccgggtatgcactcaacttc 50   123


    Quality   887778888888888888888888888888888887777777777666
    Target1 0 CAGAACCCTGACCCTGCCGTGTACCAGCTGAGAGACTCTAAATCCAGT 47  Score
    TRAC*00 5 cagaaccctgaccctgccgtgtaccagctgagagactctaaatccagt 52  240

    ....


    >>> Read id: 5663229


        Quality     666777777777788888878888888888888888888888888888
        Target0   0 GATGATTCAGGGATGCCCGAGGATCGATTCTCAGCTAAGATGCCTAAT 47   Score
    TRBV12-3*00 222 gatgattcagggatgcccgaggatcgattctcagctaagatgcctaat 269  240
    TRBV12-4*00 222 gatgattcagggatgcccgaggatcgattctcagctaagatgcctaat 269  240

                    <CDR3         V>     <J           CDR3><FR4
        Quality     66887888788888888878888788888888888887777777777666
        Target1   0 TGTGCCAGCAGTTTAGGGCCTAACACTGAAGCTTTCTTTGGACAAGGCAC 49   Score
    TRBV12-3*00 330 tgtgccagcagtttag                                   345  80
    TRBV12-4*00 330 tgtgccagcagtttag                                   345  80
     TRBJ1-1*00  22                      aacactgaagctttctttggacaaggcac 50   145

    ....


Extension procedure by default is applied only to TCRs, which is relatively safe as TCRs are known to have no hypermutations.

In our experiments with artificially shortened reads infered extension sequence was correct in all cases.


Version info
------------

In order to check the current version of MiXCR as usual one can use ``-v'` option:

::

    > mixcr -v
    MiXCR v1.8-SNAPSHOT (built Thu May 12 19:24:50 MSK 2016; rev=6d2e243; branch=feature/trnaseq)
    Components: 
    MiLib v1.4-SNAPSHOT (rev=65c048a; branch=feature/mutations_aggregation)
    MiTools v1.4-SNAPSHOT (rev=c05934a; branch=develop)



In order to check which version of MiXCR was used to build some vdjca/clns file:

::

    > mixcr versionInfo file.vdjca
    MagicBytes = MiXCR.VDJC.V06
    MiXCR v1.8-SNAPSHOT (built Fri Jan 29 16:16:40 MSK 2016; rev=327c30c; branch=feature/mixcr_diff); MiLib v1.2 (rev=4f56782; branch=release/v1.2); MiTools v1.2 (rev=eb91603; branch=release/v1.2)


Merge alignments
----------------

Allows to merge multiple ``.vdjca`` files into a single one:


::

    > mixcr mergeAlignments file1.vdjca file2.vdjca ... output.vdjca




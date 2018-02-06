.. _ref-export:

Export
======

In order to export alignment results or clones from a binary file
(``.vdjca`` or ``.clns``) to a human-readable text file one can use the
``exportAlignments`` and ``exportClones`` commands respectively. The
syntax for these commands is:

::

    mixcr exportAlignments [options] alignments.vdjca alignments.txt

::

    mixcr exportClones [options] clones.clns clones.txt

The resulting tab-delimited text file will contain columns with
different types of information. If no options are specified, the default set
of columns - which is sufficient in most cases - will be exported. The
possible columns include (see below for details): aligned sequences,
qualities, all or just best hit for V, D, J and C genes, corresponding
alignments, nucleotide and amino acid sequences of gene region present
in sequence, etc. When exporting clones, the additional columns include: clone
count, clone fraction etc.

One can customize the list of fields that will be exported by passing
parameters to export commands. For example, in order to export just
clone count, best hits for V and J genes with corresponding alignments
and ``CDR3`` amino acid sequence, one can do:

::

    mixcr exportClones -count -vHit -jHit -vAlignment -jAlignment -aaFeature CDR3 clones.clns clones.txt

The columns in the resulting file will be exported in exactly the same
order as parameters on the command line. The list of available fields
will be reviewed in the next subsections. For convenience, MiXCR
provides two predefined sets of fields for exporting: ``min`` (will
export minimal required information about clones or alignments) and
``full`` (used by default); one can use these sets by specifying the
``--preset`` option:

::

    mixcr exportClones --preset min clones.clns clones.txt

One can add additional columns to the preset in the following way:

::

    mixcr exportClones --preset min -qFeature CDR2 clones.clns clones.txt

One can also put all specify export fields in a seperate file:

::

    -vHits
    -dHits
    -feature CDR3
    ...

and pass this file to the export command:

::

    mixcr exportClones --preset-file myFields.txt clones.clns clones.txt

Command line parameters
-----------------------

The following is a list of command line parameters for both ``exportAlignments`` and
``exportClones``:

+-----------------------------+-------------------------------------------------------------------+
| Option                      | Description                                                       |
+=============================+===================================================================+
| ``-h``, ``--help``          | print help message                                                |
+-----------------------------+-------------------------------------------------------------------+
| ``-f``, ``--fields``        | list available fields that can be exported                        |
+-----------------------------+-------------------------------------------------------------------+
| ``-p``, ``--preset``        | select a predefined set of fields to export (``full`` or ``min``) |
+-----------------------------+-------------------------------------------------------------------+
| ``-pf``, ``--preset-file``  | load a file with a list of fields to export                       |
+-----------------------------+-------------------------------------------------------------------+
| ``-lf``, ``--list-fields``  | list available fields that can be exported                        |
+-----------------------------+-------------------------------------------------------------------+
| ``-s``, ``--no-spaces``     | output short versions of column headers to assist with analysis   |
|                             | using Pandas, R/DataFrames or another data tables processing      |
|                             | library                                                           |
+-----------------------------+-------------------------------------------------------------------+

The following parameters only apply to ``exportClones``:

+--------------------------------------+-------------------------------------------------------------------+
| ``-c``, ``--chains``                 | Limit output to specific locus (e.g. TRA or IGH). Clone fractions |
|                                      | will be recalculated accordingly.                                 |
+--------------------------------------+-------------------------------------------------------------------+
| ``-o``, ``--filter-out-of-frames``   | Exclude out of frames (fractions will be recalculated)            |
+--------------------------------------+-------------------------------------------------------------------+
| ``-t``, ``--filter-stops``           | Exclude sequences containing stop codons (fractions will be       |
|                                      | recalculated)                                                     |
+--------------------------------------+-------------------------------------------------------------------+
| ``-m``, ``--minimal-clone-count``    | Filter clones by minimal read count.                              |
+--------------------------------------+-------------------------------------------------------------------+
| ``-q``, ``--minimal-clone-fraction`` | Filter clones by minimal clone fraction.                          |
+--------------------------------------+-------------------------------------------------------------------+



Available fields
----------------

The following fields can be exported both for alignments and clones:

.. include:: ExportFieldsVDJCObject.rst

The following fields are specific for alignments:

.. include:: ExportFieldsVDJCAlignments.rst

The following fields are specific for clones:

.. include:: ExportFieldsClone.rst



Default anchor point positions
------------------------------

Positions of anchor points produced by the ``-defaultAnchorPoints`` option are outputted as a colon separated list.
If an anchor point is not covered by the target sequence nothing is printed for it, but flanking colon symbols are
preserved to maintain positions in array. See example:

::

    :::::::::108:117:125:152:186:213:243:244:

If there are several target sequences (e.g. paired-end reads or multi-part clonal sequnce), an array is outputted for
each target sequence. In this case arrays are separated by a comma:

::

    2:61:107:107:118:::::::::::::,:::::::::103:112:120:147:181:208:238:239:

Even if there are no anchor points in one of the parts:

::

    :::::::::::::::::,:::::::::108:117:125:152:186:213:243:244:


The following table shows the correspondence between anchor points and positions in the default anchor point array:

+--------------------------+---------------------+--------------------+
| Anchors point            | Zero-based position | One-based position |
+==========================+=====================+====================+
| V5UTRBeginTrimmed        | 0                   | 1                  |
+--------------------------+---------------------+--------------------+
| V5UTREnd / L1Begin       | 1                   | 2                  |
+--------------------------+---------------------+--------------------+
| L1End / VIntronBegin     | 2                   | 3                  |
+--------------------------+---------------------+--------------------+
| VIntronEnd / L2Begin     | 3                   | 4                  |
+--------------------------+---------------------+--------------------+
| L2End / FR1Begin         | 4                   | 5                  |
+--------------------------+---------------------+--------------------+
| FR1End / CDR1Begin       | 5                   | 6                  |
+--------------------------+---------------------+--------------------+
| CDR1End / FR2Begin       | 6                   | 7                  |
+--------------------------+---------------------+--------------------+
| FR2End / CDR2Begin       | 7                   | 8                  |
+--------------------------+---------------------+--------------------+
| CDR2End / FR3Begin       | 8                   | 9                  |
+--------------------------+---------------------+--------------------+
| FR3End / CDR3Begin       | 9                   | 10                 |
+--------------------------+---------------------+--------------------+
| Number of 3' V deletions | 10                  | 11                 |
| (negative value), or     |                     |                    |
| length of 3' V P-segment |                     |                    |
| (positive value)         |                     |                    |
+--------------------------+---------------------+--------------------+
| VEndTrimmed, next        | 11                  | 12                 |
| position after last      |                     |                    |
| aligned nucleotide of V  |                     |                    |
| gene                     |                     |                    |
+--------------------------+---------------------+--------------------+
| DBeginTrimmed, position  | 12                  | 13                 |
| of first aligned         |                     |                    |
| nucleotide of D gene     |                     |                    |
+--------------------------+---------------------+--------------------+
| Number of 5' D deletions | 13                  | 14                 |
| (negative value), or     |                     |                    |
| length of 5' D P-segment |                     |                    |
| (positive value)         |                     |                    |
+--------------------------+---------------------+--------------------+
| Number of 3' D deletions | 14                  | 15                 |
| (negative value), or     |                     |                    |
| length of 3' D P-segment |                     |                    |
| (positive value)         |                     |                    |
+--------------------------+---------------------+--------------------+
| DEndTrimmed, next        | 15                  | 16                 |
| position after last      |                     |                    |
| aligned nucleotide of D  |                     |                    |
| gene                     |                     |                    |
+--------------------------+---------------------+--------------------+
| JBeginTrimmed, position  | 16                  | 17                 |
| of first aligned         |                     |                    |
| nucleotide of J gene     |                     |                    |
+--------------------------+---------------------+--------------------+
| Number of 3' J deletions | 17                  | 18                 |
| (negative value), or     |                     |                    |
| length of 3' J P-segment |                     |                    |
| (positive value)         |                     |                    |
+--------------------------+---------------------+--------------------+
| CDR3End / FR4Begin       | 18                  | 19                 |
+--------------------------+---------------------+--------------------+
| FR4End                   | 19                  | 20                 |
+--------------------------+---------------------+--------------------+
| CBegin                   | 20                  | 21                 |
+--------------------------+---------------------+--------------------+
| CExon1End                | 21                  | 22                 |
+--------------------------+---------------------+--------------------+

The following regular expressions can be used to parse the contents of this field in Python:

- for length analysis, or analysis of raw alignments:

    ::

        ^(?P<V5UTRBegin>-?[0-9]*):(?P<L1Begin>-?[0-9]*):(?P<VIntronBegin>-?[0-9]*):(?P<L2Begin>-?[0-9]*):(?P<FR1Begin>-?[0-9]*):(?P<CDR1Begin>-?[0-9]*):(?P<FR2Begin>-?[0-9]*):(?P<CDR2Begin>-?[0-9]*):(?P<FR3Begin>-?[0-9]*):(?P<CDR3Begin>-?[0-9]*):(?P<V3Deletion>-?[0-9]*):(?P<VEnd>-?[0-9]*):(?P<DBegin>-?[0-9]*):(?P<D5Deletion>-?[0-9]*):(?P<D3Deletion>-?[0-9]*):(?P<DEnd>-?[0-9]*):(?P<JBegin>-?[0-9]*):(?P<J5Deletion>-?[0-9]*):(?P<CDR3End>-?[0-9]*):(?P<CBegin>-?[0-9]*):(?P<CExon1End>-?[0-9]*)$

    snipped for Pandas:

    .. code-block:: python

        import pandas as pd
        data = pd.read_table("exported.txt", low_memory=False)
        anchorPointsRegex="^(?P<V5UTRBegin>-?[0-9]*):(?P<L1Begin>-?[0-9]*):(?P<VIntronBegin>-?[0-9]*):(?P<L2Begin>-?[0-9]*):(?P<FR1Begin>-?[0-9]*):(?P<CDR1Begin>-?[0-9]*):(?P<FR2Begin>-?[0-9]*):(?P<CDR2Begin>-?[0-9]*):(?P<FR3Begin>-?[0-9]*):(?P<CDR3Begin>-?[0-9]*):(?P<V3Deletion>-?[0-9]*):(?P<VEnd>-?[0-9]*):(?P<DBegin>-?[0-9]*):(?P<D5Deletion>-?[0-9]*):(?P<D3Deletion>-?[0-9]*):(?P<DEnd>-?[0-9]*):(?P<JBegin>-?[0-9]*):(?P<J5Deletion>-?[0-9]*):(?P<CDR3End>-?[0-9]*):(?P<CBegin>-?[0-9]*):(?P<CExon1End>-?[0-9]*)$"
        data = pd.concat([data, d.refPoints.str.extract(anchorPointsRegex, expand=True).apply(pd.to_numeric)], axis=1)

- A simplified regular expression with a smaller number of fields can be used for analysis of CDR3-assembled clonotypes:

    ::

        ^(?:-?[0-9]*:){8}(?:-?[0-9]*):(?P<CDR3Begin>-?[0-9]*):(?P<V3Deletion>-?[0-9]*):(?P<VEnd>-?[0-9]*):(?P<DBegin>-?[0-9]*):(?P<D5Deletion>-?[0-9]*):(?P<D3Deletion>-?[0-9]*):(?P<DEnd>-?[0-9]*):(?P<JBegin>-?[0-9]*):(?P<J5Deletion>-?[0-9]*):(?P<CDR3End>-?[0-9]*):(?:-?[0-9]*:){2}(?:-?[0-9]*)$

    snipped for Pandas:

    .. code-block:: python

        import pandas as pd
        data = pd.read_table("exported.txt", low_memory=False)
        anchorPointsRegex="^^(?:-?[0-9]*:){8}(?:-?[0-9]*):(?P<CDR3Begin>-?[0-9]*):(?P<V3Deletion>-?[0-9]*):(?P<VEnd>-?[0-9]*):(?P<DBegin>-?[0-9]*):(?P<D5Deletion>-?[0-9]*):(?P<D3Deletion>-?[0-9]*):(?P<DEnd>-?[0-9]*):(?P<JBegin>-?[0-9]*):(?P<J5Deletion>-?[0-9]*):(?P<CDR3End>-?[0-9]*):(?:-?[0-9]*:){2}(?:-?[0-9]*)$"
        data = pd.concat([data, d.refPoints.str.extract(anchorPointsRegex, expand=True).apply(pd.to_numeric)], axis=1)


Examples
--------

Export only the best V, D, J hits and the best V hit alignment from a ``.vdjca``
file:

::

    mixcr exportAlignments -vHit -dHit -jHit -vAlignment input.vdjca test.txt

+----------------+----------------+----------------+---------------------------------------------------------------+
| Best V hit     | Best D hit     | Best J hit     | Best V alignment                                              |
+================+================+================+===============================================================+
| IGHV4-34\*\00  |                | IGHJ4\*\00     | ``|262|452|453|47|237|SC268GSC271ASC275G|956.1,58|303|450|``  |
|                |                |                | ``56|301|SG72TSA73CSG136TSA144CSA158CSG171T|331.0|``          |
+----------------+----------------+----------------+---------------------------------------------------------------+
| IGHV2-23\*\00  | IGHD2\*\21     | IGHJ6\*\00     | ``|262|452|453|47|237|SC268GSC271ASC275G|956.1,58|303|450|``  |
|                |                |                | ``56|301|SG72TSA73CSG136TSA144CSA158CSG171T|331.0|``          |
+----------------+----------------+----------------+---------------------------------------------------------------+



The syntax of alignment is described in :ref:`appendix <ref-encoding>`.

Exporting well formatted alignments for manual inspection
---------------------------------------------------------

MiXCR is able to export alignments create with the :ref:`align <ref-align>`
step as pretty formatted text (human readable) for manual analysis. This can be
used both to inspect alignments and to facilitate optimization of
analysis parameters and library preparation protocol. To export pretty
formatted alignments use the ``exportAlignmentsPretty`` command:

::

    mixcr exportAlignmentsPretty --skip 1000 --limit 10 input.vdjca test.txt

this will export 10 results after skipping the first 1000 records, then place the
results into the file ``test.txt``. Skipping earlier records is often useful
because the first sequences in a fastq file may have lower than average read quality.
Omitting the last parameter (output file name) will print results directly
to the standard output stream (to console), like this:

::

    mixcr exportAlignmentsPretty --skip 1000 --limit 10 input.vdjca

Here is a summary of the command line options:

+---------------------+-----------------------------------------------------------------------------------------+
| Option              | Description                                                                             |
+=====================+=========================================================================================+
| ``-h``, ``--help``  | print help message                                                                      |
+---------------------+-----------------------------------------------------------------------------------------+
| ``-n``, ``--limit`` | limit number of alignments; no more than provided number of results will be outputted   |
+---------------------+-----------------------------------------------------------------------------------------+
| ``-s``, ``--skip``  | number of results to skip                                                               |
+---------------------+-----------------------------------------------------------------------------------------+
| ``-t``, ``--top``   | output only top hits for V, D, J nad C genes                                            |
+---------------------+-----------------------------------------------------------------------------------------+
| ``--cdr3-contains`` | output only those alignments in which CDR3 contains specified nucleotides (e.g.         |
|                     | ``--cdr3-contains TTCAGAGGAGC``)                                                        |
+---------------------+-----------------------------------------------------------------------------------------+
| ``--read-contains`` | output only those alignments for which the corresonding reads contain specified         |
|                     | nucleotides e.g. ``--read-contains ATGCTTGCGCGCT``)                                     |
+---------------------+-----------------------------------------------------------------------------------------+
| ``--verbose``       | use a more verbose format for alignments (see below for example)                        |
+---------------------+-----------------------------------------------------------------------------------------+


Results produced by this command have the following structure:

.. raw:: html

    <pre style="font-size: 10px">

      &gt;&gt;&gt; Read id: 1

                                                          5'UTR&gt;&lt;L1                               
       Quality    88888888888888888888888887888888888888888888888888888888888888888888888887888878
       Target0  0 AAGGCCTTTCCACTTGGTGATCAGCACTGAGCACAGAGGACTCACCATGGAGTTGGGGCTGAGCTGGGTTTTCCTTGTTG 79
    IGHV3-7*00 54 aaggcctttccacttggtgatcagcactgagcacagaggactcaccatggaAttggggctgagctgggttttccttgttg 133

                            L1&gt;&lt;L2     L2&gt;&lt;FR1                                                     
       Quality     88888888887888888888888888888889989989989889999997999999989999999999999999999899
       Target0  80 CTATTTTAGAAGGTGTCCAGTGTGAGGTGAAGTTGGTGGAGTCTGGGGGAGGCCTGGTCCAGCCTGGGGGGTCCCTGAGA 159
    IGHV3-7*00 134 ctattttagaaggtgtccagtgtgaggtgCagCtggtggagtctgggggaggcTtggtccagcctggggggtccctgaga 213

                                 FR1&gt;&lt;CDR1              CDR1&gt;&lt;FR2                                  
       Quality     999999999999999999999999999999999999999999999 9999999999999999999999999999999999
       Target0 160 CTCTCCTGTGAAGCCTCCGGATTCACCTTTAGTAGTTATTGGATG-GCATGGGTCCGCCAGGGTCCAGGGCAGGGGCTGG 238
    IGHV3-7*00 214 ctctcctgtgCagcctcTggattcacctttagtagCtattggatgAgc-tgggtccgccaggCtccagggAaggggctgg 292

                             FR2&gt;&lt;CDR2              CDR2&gt;&lt;FR3                                      
       Quality     99999999999999999999999999999999999799999999999999999999999999998999899898999999
       Target0 239 AATGGGTGGGCAACATAAGGCCGGATGGAAGTGAGAGTTGGTACTTGGAGTCTGTGATGGGGCGATTCATGATATCTAGA 318
    IGHV3-7*00 293 aGtgggtggCcaacataaAgcAAgatggaagtgagaAAtACtaTGtggaCtctgtgaAgggCcgattcaCCatCtcCaga 372

                                                                                     FR3&gt;&lt;CDR3      
        Quality     99899899999999988989999889979988888888878878788888888878888888778788888888878888
        Target0 319 GACAACGCCAAGAAGTCACTTTATCTGCAAATGGACAGCCTGAGAGTCGAGGACACGGCCGTCTATTATTGTGCGACTTC 398
     IGHV3-7*00 373 gacaacgccaagaaCtcactGtatctgcaaatgAacagcctgagagCcgaggacacggcTgtGtattaCtgtgcga     448
    IGHD3-10*00  12                                                                              ttc 14

                                     CDR3&gt;&lt;FR4                                                      
        Quality     88888788888888888888888787788777887787777877777877787787877878788788777767778788
        Target0 399 GGAGGAGCCGGAGGACTACTGGGGCCAGGGAGCCCTGGTCACCGTCTCCTCGGCTTCCACCAAGGGCCCATCGGTCTTCC 478
    IGHD3-10*00  15 gg-ggag                                                                          20
       IGHJ4*00   8              gactactggggccagggaAccctggtcaccgtctcctc                              45
       IGHG4*00   0                                                      cttccaccaagggcccatcggtcttcc 26
       IGHG3*00   0                                                      cttccaccaagggcccatcggtcttcc 26
       IGHG2*00   0                                                      cCtccaccaagggcccatcggtcttcc 26
       IGHG1*00   0                                                      cCtccaccaagggcccatcggtcttcc 26
       IGHGP*00 194                                                    AgcCtccaccaagggcccatcggtcttcc 222

                      
     Quality     87370
     Target0 479 CCTTG 483
    IGHG4*00  27 ccCtg 31
    IGHG3*00  27 ccCtg 31
    IGHG2*00  27 ccCtg 31
    IGHG1*00  27 ccCtg 31
    IGHGP*00 223 ccCtg 227

    </pre>
   

Usage of the ``--verbose`` option will produce alignments in a slightly different format: 


.. raw:: html

   <pre style="font-size: 10px">&gt;&gt;&gt; Read id: 12343    <span style="color:red;"><--- Index of analysed read in input file</span>

   &gt;&gt;&gt; Target sequences (input sequences):

   Sequence0:   <span style="color:red;"><--- Read 1 from paired-end read</span>
   Contains features: CDR1, VRegionTrimmed, L2, L, Intron, VLIntronL, FR1, Exon1,              <span style="color:red;"><--- Gene features</span>
   VExon2Trimmed                                                                                    <span style="color:red;">found in read 1</span>

        0 TCTTGGGGGATTCGGTGATCAGCACTGAACACAGAGGACTCACCATGGAGTTTGGGCTGAACTGGGTTTTCCTCGTTGCT 79  <span style="color:red;"><--- Sequyence & quality </span>
          FGGEGGGGGDG8F78CFC6CEFF&lt;,CFG9EED,6,CFCC&lt;EEGFG,CE:CCAFFGGC87CEF?A?FBC@FGGFG&gt;B,FC9          <span style="color:red;">of read 1</span>

       80 CTATTAAGAGGTGTCCAGTGTCAGGTGCAGCTGGTGGAGTCTGGGGGTGGCGTGTTCCAGCCTGGGGGGTCCGTGAGACT 159
          F9,A,95AFE,B?,E,C,9AC&lt;FGA&lt;EE5??,A,A&lt;:=:E,=B8C7+++8,++@+,885=D7:@8E+:5*1**11**++&lt
      160 CTCCTGTGCAGCGTCGGGATGCACATCATGGAGCTATGGCCAGCCCTGGGTACGCCAGGCTACAGGCCACGGGCTGGAGG 239
          &lt;++*++0++2A:ECE5EC5**2@C+:++++++22*2:+29+*2***25/79*0299))*/)*0*0*.75)7:)1)1/)))

      240 GGGTGCGTGGTAGATGGGAA 259
          )9:.)))*1)12***-/).)

   Sequence1:   <span style="color:red;"><--- Read 2 from paired-end read</span>
   Contains features: JCDR3Part, DCDR3Part, DJJunction, CDR2, JRegionTrimmed, CDR3, VDJunction,
   VJJunction, VCDR3Part, ShortCDR3, FR4, FR3

        0 CGAGGCAAGAGGCTGGTGTGGGTGGCGGTTATATGGTATGGTGGAAGTAATAAACACTATGCAGACCCCGTGAAGGGCCG 79
          **0*0**)2**/**5D7&lt;15*9&lt;5:1+*0:GF:=C&gt;6A52++*:2+++FF&gt;&gt;3&lt;++++++302**:**/&lt;+**;:/**2+

       80 ATTCACCATCGCCAGAGACAATTCCAAGAACACGCTGTATCTGCAAATGAAGAGCCTGAGAGCCGAGGACACGGCTTTGT 159
          +++&lt;0***C:2+9GGFB?,5,4,+,2F&lt;&gt;FC=*,,C:&gt;,=,@,,;3&lt;@=,3,,&lt;3,CF?=**&lt;&gt;@,?3,&lt;&lt;:3,CC,E,@

      160 ATTACTGTGCGAGAGGTCAACAGGGTGACTATGTCTACGGTAGGGACGTCGGGGGCCAAGGGACCACGGTCACCGTCTCC 239
          ,@;FCF@+F@FGGF9FD,F&gt;&gt;+B:=,,=&gt;&lt;GFCGGCFEGFF?+=B+7EF&gt;+FFA,8F&lt;E:,5+GDFFE,@F?,,7GGDFE

      240 TCAGGGAGTGCATCCGCCCCAACCCTTTTCCCCCTCTCTGCGTTGATACCACTGGCAGCTC 300
          C,FGGGEFCCGEEGGCFCC:8FGEGGGE@DFB-GFGGGGF@GFGFE&lt;,GFCCFCAGC@CCC

   &gt;&gt;&gt; Gene features that can be extracted from this (paired-)read:                         <span style="color:red;"><--- For paired-end reads</span>
   JCDR3Part, CDR1, VRegionTrimmed, L2, DCDR3Part, VDJTranscriptWithout5UTR, Exon2, L,           <span style="color:red;">some gene features</span>
   DJJunction, Intron, FR2, CDR2, VDJRegion, JRegionTrimmed, CDR3, VDJunction, VJJunction,       <span style="color:red;">can be extracted by</span>
   VLIntronL, FR1, VCDR3Part, ShortCDR3, Exon1, FR4, VExon2Trimmed, FR3                          <span style="color:red;">merging sequence</span>
                                                                                                 <span style="color:red;">information</span>

   &gt;&gt;&gt; Alignments with V gene:

   IGHV3-33*00 (total score = 1638.0) <span style="color:red;"><--- Alignment of both reads with IGHV3-33</span>
   Alignment of Sequence0 (score = 899.0):   <span style="color:red;"><--- Alignment of IGHV3-33 with read 1 from paired-end read</span>
        65 ATTCGGTGATCAGCACTGAACACAGAGGACTCACCATGGAGTTTGGGCTGAGCTGGGTTTTCCTCGTTGCTCTTTTAAGA 144 <span style="color:red;"><--- Germline</span>
           ||||||||||||||||||||||||||||||||||||||||||||||||||| ||||||||||||||||||||| ||||||
         9 ATTCGGTGATCAGCACTGAACACAGAGGACTCACCATGGAGTTTGGGCTGAACTGGGTTTTCCTCGTTGCTCTATTAAGA 88  <span style="color:red;"><--- Read</span>
           DG8F78CFC6CEFF&lt;,CFG9EED,6,CFCC&lt;EEGFG,CE:CCAFFGGC87CEF?A?FBC@FGGFG&gt;B,FC9F9,A,95AF     <span style="color:red;"><--- Quality score</span>

       145 GGTGTCCAGTGTCAGGTGCAGCTGGTGGAGTCTGGGGGAGGCGTGGTCCAGCCTGGGAGGTCCCTGAGACTCTCCTGTGC 224
           |||||||||||||||||||||||||||||||||||||| |||||| ||||||||||| ||||| ||||||||||||||||
        89 GGTGTCCAGTGTCAGGTGCAGCTGGTGGAGTCTGGGGGTGGCGTGTTCCAGCCTGGGGGGTCCGTGAGACTCTCCTGTGC 168
           E,B?,E,C,9AC&lt;FGA&lt;EE5??,A,A&lt;:=:E,=B8C7+++8,++@+,885=D7:@8E+:5*1**11**++&lt;&lt;++*++0++

       225 AGCGTCTGGATTCACCTTCA-GTAGCTATGGCATGCACTGGGTCCGCCAGGCTCCAGGCAAGGGGCTGGAGTGGGTG 300
           |||||| |||| || | ||| | |||||||||  || |||||| ||||||||| ||||| | ||||||||| |||||
       169 AGCGTCGGGATGCA-CATCATGGAGCTATGGCCAGCCCTGGGTACGCCAGGCTACAGGCCACGGGCTGGAGGGGGTG 244
           2A:ECE5EC5**2@ C+:++++++22*2:+29+*2***25/79*0299))*/)*0*0*.75)7:)1)1/))))9:.)

   Alignment of Sequence1 (score = 739.0):   <span style="color:red;"><--- Alignment of IGHV3-33 with read 2 from paired-end read</span>
       279 AGGCAAGGGGCTGGAGTGGGTGGCAGTTATATGGTATGATGGAAGTAATAAATACTATGCAGACTCCGTGAAGGGCCGAT 358
           ||||||| |||||| ||||||||| ||||||||||||| ||||||||||||| ||||||||||| |||||||||||||||
         2 AGGCAAGAGGCTGGTGTGGGTGGCGGTTATATGGTATGGTGGAAGTAATAAACACTATGCAGACCCCGTGAAGGGCCGAT 81
           0*0**)2**/**5D7&lt;15*9&lt;5:1+*0:GF:=C&gt;6A52++*:2+++FF&gt;&gt;3&lt;++++++302**:**/&lt;+**;:/**2+++

       359 TCACCATCTCCAGAGACAATTCCAAGAACACGCTGTATCTGCAAATGAACAGCCTGAGAGCCGAGGACACGGCTGTGTAT 438
           |||||||| |||||||||||||||||||||||||||||||||||||||| |||||||||||||||||||||||| |||||
        82 TCACCATCGCCAGAGACAATTCCAAGAACACGCTGTATCTGCAAATGAAGAGCCTGAGAGCCGAGGACACGGCTTTGTAT 161
           +&lt;0***C:2+9GGFB?,5,4,+,2F&lt;&gt;FC=*,,C:&gt;,=,@,,;3&lt;@=,3,,&lt;3,CF?=**&lt;&gt;@,?3,&lt;&lt;:3,CC,E,@,@

       439 TACTGTGCGAGAG 451
           |||||||||||||
       162 TACTGTGCGAGAG 174
           ;FCF@+F@FGGF9

   IGHV3-30*00 (total score = 1582.0)  <span style="color:red;"><--- Alternative hit for V gene</span>
   Alignment of Sequence0 (score = 885.0):
        65 ATTCGGTGATCAGCACTGAACACAGAGGACTCACCATGGAGTTTGGGCTGAGCTGGGTTTTCCTCGTTGCTCTTTTAAGA 144
           ||||||||||||||||||||||||||||||||||||||||||||||||||| ||||||||||||||||||||| ||||||
         9 ATTCGGTGATCAGCACTGAACACAGAGGACTCACCATGGAGTTTGGGCTGAACTGGGTTTTCCTCGTTGCTCTATTAAGA 88
           DG8F78CFC6CEFF&lt;,CFG9EED,6,CFCC&lt;EEGFG,CE:CCAFFGGC87CEF?A?FBC@FGGFG&gt;B,FC9F9,A,95AF

       145 GGTGTCCAGTGTCAGGTGCAGCTGGTGGAGTCTGGGGGAGGCGTGGTCCAGCCTGGGAGGTCCCTGAGACTCTCCTGTGC 224
           |||||||||||||||||||||||||||||||||||||| |||||| ||||||||||| ||||| ||||||||||||||||
        89 GGTGTCCAGTGTCAGGTGCAGCTGGTGGAGTCTGGGGGTGGCGTGTTCCAGCCTGGGGGGTCCGTGAGACTCTCCTGTGC 168
           E,B?,E,C,9AC&lt;FGA&lt;EE5??,A,A&lt;:=:E,=B8C7+++8,++@+,885=D7:@8E+:5*1**11**++&lt;&lt;++*++0++

       225 AGCCTCTGGATTCACCTTCA-GTAGCTATGGCATGCACTGGGTCCGCCAGGCTCCAGGCAAGGGGCTGGAGTGGGTG 300
           ||| || |||| || | ||| | |||||||||  || |||||| ||||||||| ||||| | ||||||||| |||||
       169 AGCGTCGGGATGCA-CATCATGGAGCTATGGCCAGCCCTGGGTACGCCAGGCTACAGGCCACGGGCTGGAGGGGGTG 244
           2A:ECE5EC5**2@ C+:++++++22*2:+29+*2***25/79*0299))*/)*0*0*.75)7:)1)1/))))9:.)

   Alignment of Sequence1 (score = 697.0):
       279 AGGCAAGGGGCTGGAGTGGGTGGCAGTTATATCATATGATGGAAGTAATAAATACTATGCAGACTCCGTGAAGGGCCGAT 358
           ||||||| |||||| ||||||||| |||||||  |||| ||||||||||||| ||||||||||| |||||||||||||||
         2 AGGCAAGAGGCTGGTGTGGGTGGCGGTTATATGGTATGGTGGAAGTAATAAACACTATGCAGACCCCGTGAAGGGCCGAT 81
           0*0**)2**/**5D7&lt;15*9&lt;5:1+*0:GF:=C&gt;6A52++*:2+++FF&gt;&gt;3&lt;++++++302**:**/&lt;+**;:/**2+++

       359 TCACCATCTCCAGAGACAATTCCAAGAACACGCTGTATCTGCAAATGAACAGCCTGAGAGCTGAGGACACGGCTGTGTAT 438
           |||||||| |||||||||||||||||||||||||||||||||||||||| ||||||||||| |||||||||||| |||||
        82 TCACCATCGCCAGAGACAATTCCAAGAACACGCTGTATCTGCAAATGAAGAGCCTGAGAGCCGAGGACACGGCTTTGTAT 161
           +&lt;0***C:2+9GGFB?,5,4,+,2F&lt;&gt;FC=*,,C:&gt;,=,@,,;3&lt;@=,3,,&lt;3,CF?=**&lt;&gt;@,?3,&lt;&lt;:3,CC,E,@,@

       439 TACTGTGCGAGAG 451
           |||||||||||||
       162 TACTGTGCGAGAG 174
           ;FCF@+F@FGGF9

   &gt;&gt;&gt; Alignments with D gene:

   IGHD4-17*00 (total score = 40.0)
   Alignment of Sequence1 (score = 40.0):
         7 GGTGACTA 14
           ||||||||
       183 GGTGACTA 190
           :=,,=&gt;&lt;G

   IGHD4-23*00 (total score = 36.0)
   Alignment of Sequence1 (score = 36.0):
         0 TGACTACGGT 9
           || |||||||
       191 TGTCTACGGT 200
           FCGGCFEGFF

   IGHD2-21*00 (total score = 35.0)
   Alignment of Sequence1 (score = 35.0):
        13 GGTGACT 19
           |||||||
       183 GGTGACT 189
           :=,,=&gt;&lt;

   &gt;&gt;&gt; Alignments with J gene:

   IGHJ6*00 (total score = 172.0)
   Alignment of Sequence1 (score = 172.0):
        22 GGACGTCTGGGGCAAAGGGACCACGGTCACCGTCTCCTCA 61
           ||||||| ||||| ||||||||||||||||||||||||||
       203 GGACGTCGGGGGCCAAGGGACCACGGTCACCGTCTCCTCA 242
           =B+7EF&gt;+FFA,8F&lt;E:,5+GDFFE,@F?,,7GGDFEC,F

   &gt;&gt;&gt; Alignments with C gene:

   No hits.
   </pre>
   
   
.. _ref-exporting-reads:

Exporting reads aggregated by clones
------------------------------------

MiXCR allows to preserve information about mapping between initial reads, alignments and final clonotypes by storing output of the ``assemble`` step into special "clones & alignments" container format. There are several ways of accessing this information.

Extracting reads for specific clones
####################################

The ``exportReadsForClones`` allows to extract original reads that was mapped to specific clones back into ``fastq`` or ``fasta`` formats.

The following command will create reads_cln0_R1.fastq.gz/reads_cln0_R2.fastq.gz, reads_cln1_R1.fastq.gz/reads_cln1_R2.fastq.gz, etc, containing reads corresponding to clone0, clone1 etc...

::

    mixcr exportReadsForClones -s clonesAndAlignments.clna reads.fastq.gz

Or one can extract reads for a buch of clones into a single output:

::

    mixcr exportReadsForClones clonesAndAlignments.clna 2 12 45 reads_of_my_clones.fastq.gz

See ``mixcr exportReadsForClones -h`` for more information.

.. MiXCR can preserve the mapping between initial reads and final clonotypes. There are several options for accessing this information. 

.. To enable this behaviour, specify the option ``--index`` for the :ref:`assemble <ref-assemble>` command:

.. ::

..     mixcr assemble --index index_file alignments.vdjca output.clns

.. This will tell MiXCR to store an index mapping in the file ``index_file`` (actually two files will be created: ``index_file`` and ``index_file.p`` both of which are used to store the index; in further options one should specify only ``index_file`` without ``.p`` extension and MiXCR will automatically read both required files). It is then possible to use this ``index_file`` in order to map clones to initial reads. For example using the ``-cloneId`` option for the ``exportAlignments`` command:

.. ::

..     mixcr exportAlignments -p min -cloneId index_file alignments.vdjca alignments.txt

.. will print an additional column with the id of the clone which matches the corresponding alignment:

.. +----------------+----------------+-------+----------+
.. | Best V hit     | Best D hit     |  ...  | CloneId  |
.. +================+================+=======+==========+
.. | IGHV4-34\*\00  |                |  ...  | 321      |
.. +----------------+----------------+-------+----------+
.. | IGHV2-23\*\00  | IGHD2\*\21     |  ...  |          |
.. +----------------+----------------+-------+----------+
.. | IGHV4-34\*\00  | IGHD2\*\21     |  ...  | 22143    |
.. +----------------+----------------+-------+----------+
.. | ...            | ...            |  ...  | ...      |
.. +----------------+----------------+-------+----------+

.. For more information it's also possible to export the mapping type:

.. ::

..     mixcr exportAlignments -p min -cloneIdWithMappingType index_file alignments.vdjca alignments.txt

.. Will give you something like:

.. +----------------+----------------+-------+----------------------+
.. | Best V hit     | Best D hit     |  ...  | Clone mapping        |
.. +================+================+=======+======================+
.. | IGHV4-34\*\00  |                |  ...  | 321:core             |
.. +----------------+----------------+-------+----------------------+
.. | IGHV2-23\*\00  | IGHD2\*\21     |  ...  | dropped              |
.. +----------------+----------------+-------+----------------------+
.. | IGHV4-34\*\00  | IGHD2\*\21     |  ...  | 22143:clustered      |
.. +----------------+----------------+-------+----------------------+
.. | IGHV4-34\*\00  | IGHD2\*\21     |  ...  | 23:mapped            |
.. +----------------+----------------+-------+----------------------+
.. | ...            | ...            |  ...  | ...                  |
.. +----------------+----------------+-------+----------------------+


.. It's also possible to export all read IDs that were aggregated by each clone, using the ``-readIds`` export options for ``exportClones``:

.. ::

..     mixcr exportClones -c IGH -p min -readIds index_file clones.clns clones.txt

.. This will add a column with a full enumeration of all reads that were clustered into a particular clone:


.. +----------+-------------+----------------+-----+--------------------------------+
.. | Clone ID | Clone count | Best V hit     | ... | Reads                          |
.. +==========+=============+================+=====+================================+
.. |    0     |    7213     | IGHV4-34\*\00  | ... | 56,74,92,96,101,119,169,183... |
.. +----------+-------------+----------------+-----+--------------------------------+
.. |    1     |    2951     | IGHV2-23\*\00  | ... | 46,145,194,226,382,451,464...  |
.. +----------+-------------+----------------+-----+--------------------------------+
.. |    2     |    2269     | IGHV4-34\*\00  | ... | 58,85,90,103,113,116,122,123...|
.. +----------+-------------+----------------+-----+--------------------------------+
.. |    3     |     124     | IGHV4-34\*\00  | ... | 240,376,496,617,715,783,813... |
.. +----------+-------------+----------------+-----+--------------------------------+
.. |   ...    |             | ...            | ... | ...                            |
.. +----------+-------------+----------------+-----+--------------------------------+

.. Note, the resulting txt file may be _very large_ since all read numbers that were successfully assembled will be printed.


.. Finally, you can export reads aggregated by clone into separate ``.fastq`` files. To support this behaviour the :ref:`align <ref-align>` command needs to be run with the additional ``-g`` option:

.. ::

..     mixcr align -g input.fastq alignments.vdjca.gz

.. With this option MiXCR will store the original reads in the ``.vdjca`` file.



.. One can export reads corresponding to a particular clone with the ``exportReadsForClones`` command. For example, export all reads that were assembled into the first clone (clone with cloneId = 0):

.. ::

..     mixcr exportReadsForClones index_file alignments.vdjca.gz 0 reads.fastq.gz

.. This will create the file ``reads_clns0.fastq.gz`` (or two files ``reads_clns0_R1.fastq.gz`` and ``reads_clns0_R2.fastq.gz`` if the original data were paired) with all reads that were aggregated by the first clone. One can export reads for several clones at a time:

.. ::

..     mixcr exportReadsForClones index_file alignments.vdjca.gz 0 1 2 33 54 reads.fastq.gz

.. This will create several files (``reads_clns0.fastq.gz``, ``reads_clns1.fastq.gz`` etc.) for each clone with cloneId equal to 0, 1, 2, 33 and 54 respectively.



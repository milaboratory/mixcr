.. _ref-export:

Export
======

In order to export result of alignment or clones from binary file
(``.vdjca`` or ``.clns``) to a human-readable text file one can use
``exportAlignments`` and ``exportClones`` commands respectively. The
syntax for these commands is:

::

    mixcr exportAlignments [options] alignments.vdjca alignments.txt

::

    mixcr exportClones [options] clones.clns clones.txt

The resulting tab-delimited text file will contain columns with
different types of information. If no options specified, the default set
of columns, which is sufficient in most cases, will be exported. The
possible columns are (see below for details): aligned sequences,
qualities, all or just best hit for V, D, J and C genes, corresponding
alignemtns, nucleotide and amino acid sequences of gene region present
in sequence etc. In case of clones, the additional columns are: clone
count, clone fraction etc.

One can customize the list of fields that will be exported by passing
parameters to export commands. For example, in order to export just
clone count, best hits for V and J genes with corresponding alignments
and ``CDR3`` amino acid sequence, one can do:

::

    mixcr exportClones -count -vHit -jHit -vAlignment -jAlignment -aaFeature CDR3 clones.clns clones.txt

The columns in the resulting file will be exported in the exact same
order as parameters in the command line. The list of available fields
will be reviewed in the next subsections. For convenience, MiXCR
provides two predefined sets of fields for exporting: ``min`` (will
export minimal required information about clones or alignments) and
``full`` (used by default); one can use these sets by specifying
``--preset`` option:

::

    mixcr exportClones --preset min clones.clns clones.txt

One can add additional columns to preset in the following way:

::

    mixcr exportClones --preset min -qFeature CDR2 clones.clns clones.txt

One can also put all export fields in the file like:

::

    -vHits
    -dHits
    -feature CDR3
    ...

and pass this file to export command:

::

    mixcr exportClones --presetFile myFields.txt clones.clns clones.txt

Command line parameters
-----------------------

The list of command line parameters for both ``exportAlignments`` and
``exportClones`` is the following:

+---------------------+-------------------------------------------------------------------+
| Option              | Description                                                       |
+=====================+===================================================================+
| -h, --help          | print help message                                                |
+---------------------+-------------------------------------------------------------------+
| -f, --fields        | list available fields that can be exported                        |
+---------------------+-------------------------------------------------------------------+
| -p, --preset        | select predefined set of fields to export (``full`` or ``min``)   |
+---------------------+-------------------------------------------------------------------+
| -pf, --presetFile   | load file with a list of fields to export                         |
+---------------------+-------------------------------------------------------------------+

Available fields
----------------

The following fields can be exported both for alignments and clones:

+-----------------------------------+----------------------------------------------------------+
| Field                             | Description                                              |
+===================================+==========================================================+
| ``-vHit``                         | Best V hit.                                              |
+-----------------------------------+----------------------------------------------------------+
| ``-dHit``                         | Best D hit.                                              |
+-----------------------------------+----------------------------------------------------------+
| ``-jHit``                         | Best J hit.                                              |
+-----------------------------------+----------------------------------------------------------+
| ``-cHit``                         | Best C hit.                                              |
+-----------------------------------+----------------------------------------------------------+
| ``-vHits``                        | All V hits.                                              |
+-----------------------------------+----------------------------------------------------------+
| ``-dHits``                        | All D hits.                                              |
+-----------------------------------+----------------------------------------------------------+
| ``-jHits``                        | All J hits.                                              |
+-----------------------------------+----------------------------------------------------------+
| ``-cHits``                        | All C hits.                                              |
+-----------------------------------+----------------------------------------------------------+
| ``-vAlignment``                   | Best V alignment.                                        |
+-----------------------------------+----------------------------------------------------------+
| ``-dAlignment``                   | Best D alignment.                                        |
+-----------------------------------+----------------------------------------------------------+
| ``-jAlignment``                   | Best J alignment.                                        |
+-----------------------------------+----------------------------------------------------------+
| ``-cAlignment``                   | Best C alignment.                                        |
+-----------------------------------+----------------------------------------------------------+
| ``-vAlignments``                  | All V alignments.                                        |
+-----------------------------------+----------------------------------------------------------+
| ``-dAlignments``                  | All D alignments.                                        |
+-----------------------------------+----------------------------------------------------------+
| ``-jAlignments``                  | All J alignments.                                        |
+-----------------------------------+----------------------------------------------------------+
| ``-cAlignments``                  | All C alignments.                                        |
+-----------------------------------+----------------------------------------------------------+
| ``-nFeature [feature]``           | Nucleotide sequence of specified gene feature.           |
+-----------------------------------+----------------------------------------------------------+
| ``-qFeature [feature]``           | Quality of sequences of specified gene feature.          |
+-----------------------------------+----------------------------------------------------------+
| ``-aaFeature [feature]``          | Amino acid sequence of specified gene feature.           |
+-----------------------------------+----------------------------------------------------------+
| ``-avrgFeatureQuality [feature]`` | Average quality of sequence of specified gene feature.   |
+-----------------------------------+----------------------------------------------------------+
| ``-minFeatureQuality [feature]``  | Minimal quality of sequence of specified gene feature.   |
+-----------------------------------+----------------------------------------------------------+

The following fields are specific for alignments:

+---------------+-----------------------------------------------------------------------------------------------------------------------------+
| Field         | Description                                                                                                                 |
+===============+=============================================================================================================================+
| ``-sequence`` | Aligned sequence (initial read), or 2 sequences in case of paired-end reads.                                                |
+---------------+-----------------------------------------------------------------------------------------------------------------------------+
| ``-quality``  | Initial read quality, or 2 qualities in case of paired-end reads.                                                           |
+---------------+-----------------------------------------------------------------------------------------------------------------------------+
| ``-readId``   | Index of source read (in e.g. ``.fastq`` file) for alignment.                                                               |
+---------------+-----------------------------------------------------------------------------------------------------------------------------+
| ``-targets``  | Number of targets, i.e. 1 in case of single reads and 2 in case of paired-end reads.                                        |
+---------------+-----------------------------------------------------------------------------------------------------------------------------+
| ``-descrR1``  | Description line from initial ``.fasta`` or ``.fastq`` file of the first read (only available if ``--save-description`` was | 
|               | used in :ref:`align <ref-align>` command).                                                                                  |
+---------------+-----------------------------------------------------------------------------------------------------------------------------+
| ``-descrR2``  | Description line from initial ``.fastq`` file of the second read (only available if ``--save-description`` was used in      |
|               | :ref:`align <ref-align>` command).                                                                                          |
+---------------+-----------------------------------------------------------------------------------------------------------------------------+

The following fields are specific for clones:

+---------------+----------------------------------------------------------------------------------------+
| Field         | Description                                                                            |
+===============+========================================================================================+
| ``-count``    | Clone count.                                                                           |
+---------------+----------------------------------------------------------------------------------------+
| ``-fraction`` | Clone fraction.                                                                        |
+---------------+----------------------------------------------------------------------------------------+
| ``-sequence`` | Clonal sequence (or several sequences in case of multi-featured assembling).           |
+---------------+----------------------------------------------------------------------------------------+
| ``-quality``  | Clonal sequence quality (or several qualities in case of multi-featured assembling).   |
+---------------+----------------------------------------------------------------------------------------+
| ``-targets``  | Number of targets, i.e. number of gene regions used to assemble clones.                |
+---------------+----------------------------------------------------------------------------------------+

Examples
--------

Export only best V, D, J hits and best V hit alignment from ``.vdjca``
file:

::

    mixcr exportAlignments -vHit -dHit -jHit -vAlignment input.vdjca test.txt

+----------------+----------------+----------------+------------------------------------------------------------------------------------+
| Best V hit     | Best D hit     | Best J hit     | Best V alignment                                                                   |
+================+================+================+====================================================================================+
| IGHV4-34\*\00  |                | IGHJ4\*\00     | \|262|452|453|47|237|SC268GSC271ASC275G|956.1,58|303|450|56|301|\                  |
|                |                |                | \SG72TSA73CSG136TSA144CSA158CSG171T|331.0|\                                        |
+----------------+----------------+----------------+------------------------------------------------------------------------------------+
| IGHV2-23\*\00  | IGHD2\*\21     | IGHJ6\*\00     | \|262|452|453|47|237|SC268GSC271ASC275G|956.1,58|303|450|56|301|\                  |
|                |                |                | \SG72TSA73CSG136TSA144CSA158CSG171T|331.0|                                         |
+----------------+----------------+----------------+------------------------------------------------------------------------------------+



The syntax of alignment is described in :ref:`appendix <ref-encoding>`.

Exporting well formatted alignments for manual inspection
---------------------------------------------------------

MiXCR allows to export resulting alignments after :ref:`align <ref-align>`
step as a pretty formatted text for manual analysis of produced
alignments and structure of library to facilitate optimization of
analysis parameters and libraray preparation protocol. To export pretty
formatted alignments use ``exportAlignmebtsPretty`` command:

::

    mixcr exportAlignmentsPretty --skip 1000 --limit 10 input.vdjca test.txt

this will export 10 results after skipping first 1000 records and place
result into ``test.txt`` file. Skipping of first records is often useful
because first sequences in fastq file may have lower quality then
average reads, so first resulsts are not representative. It is possible
to omit last paramenter with output file name to print result directly
to standard output stream (to console), like this:

::

    mixcr exportAlignmentsPretty --skip 1000 --limit 10 input.vdjca

Here is a summary of command line options:

+---------------+-----------------------------------------------------------------------------------------+
| Option        | Description                                                                             |
+===============+=========================================================================================+
| -h, --help    | print help message                                                                      |
+---------------+-----------------------------------------------------------------------------------------+
| -n, --limit   | limit number of alignments; no more than provided number of results will be outputted   |
+---------------+-----------------------------------------------------------------------------------------+
| -s, --skip    | number of results to skip                                                               |
+---------------+-----------------------------------------------------------------------------------------+
| -t, --top     | output only top hits for V, D, J nad C genes                                            |
+---------------+-----------------------------------------------------------------------------------------+

Results produced by this command has the following structure:

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
   
   

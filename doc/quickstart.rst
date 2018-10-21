.. _ref-quickstart:

Quick start
============

Overview
--------

Typical MiXCR workflow consists of three main processing steps:

-  :ref:`align <ref-align>`: align sequencing reads to reference V, D, J
   and C genes of T- or B- cell receptors
-  :ref:`assemble <ref-assemble>`: assemble clonotypes using alignments
   obtained on previous step (in order to extract specific gene regions
   e.g. CDR3)
-  :ref:`export <ref-export>`: export alignment (``exportAlignments``) or
   clones (``exportClones``) to human-readable text file

Optionally, MiXCR allows to assemble complete sequences using

-  :ref:`assembleContigs <ref-assembleContigs>`: assemble complete TCR/IG receptor clonotype sequences

In case of :ref:`RNA-Seq or non-targeted DNA data <ref-rna-seq>`, the workflow may include:

-  :ref:`assemblePartial <ref-assemblePartial>`: assemble overlapping fragmented sequencing reads into long-enough CDR3 containing contigs
-  :ref:`extend <ref-extend>`: impute germline sequences for good quality but trimmed TCR alignments


For simplicity, MiXCR provides command :ref:`analyze <ref-analyze>` that packs a complicated execution pipelines into a single command.


.. figure:: _static/MiXCR.svg


MiXCR supports the following formats of sequencing data: ``fasta``, ``fastq``, ``fastq.gz``, paired-end ``fastq`` and ``fastq.gz``. As an output of each processing stage, MiXCR produces binary compressed file with comprehensive information about entries produced by this stage (alignments in case of ``align`` and clones in case of ``assemble``). Each binary file can be converted to a human-readable/parsable tab-delimited text file using ``exportAlignments`` and ``exportClones`` commands.


Examples
--------

.. _ref-examples:

Default workflow / multiplex-PCR
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Analysis of **multiplex-PCR** selected DNA fragments of T-/B- cell receptor genes may be performed using single :ref:`analyze amplicon <ref-analyze-amplicon>` command:

.. code-block:: console

  > mixcr analyze amplicon --species hs \
          --starting-material dna \
          --5-end v-primers \
          --3-end j-primers \
          --adapters adapters-present \
          --receptor-type IGH \
          input_R1.fastq input_R2.fastq analysis

The value of only one optional parameter is changed from its default in this snippet (``--receptor-type IGH``) to tell MiXCR that B-cell optimized aligner should be used and to export only IGH sequences. However this parameter can be omitted (in this case MiXCR will use the default aligner and export all T-/B- cell receptor sequences, that have been found in the sample). 

The file produced (``analysis.clonotypes.IGH.txt``) will contain a tab-delimited table with information about all clonotypes assembled by CDR3 sequence (clone abundance, CDR3 sequence, V, D, J genes, etc.). For full length analysis and other useful features see examples below.


Details
"""""""

Under the hood ``analyze amplicon`` is equivalent to the execution of the following MiXCR actions options:

.. code-block:: console

  > mixcr align -s hs -p kAligner2 input_R1.fastq input_R2.fastq alignments.vdjca

  ... Building alignments

  > mixcr assemble alignments.vdjca clones.clns

  ... Assembling clones

  > mixcr exportClones --chains IGH clones.clns clones.txt

  ... Exporting clones to tab-delimited file


.. _ref-example5RACE:


Analysis of data obtained using 5'RACE-based amplification protocols
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Consider MiXCR workflow in more detail on analysis of paired-end
sequenced cDNA library of IGH gene prepared using 5'RACE-based protocol
(i.e. onе read covers CDR3 with surroundings and another one covers
5'UTR and downstream sequence of V gene). The whole analysis may be performed 
using :ref:`analyze amplicon <ref-analyze-amplicon>` command:

.. code-block:: console

  > mixcr analyze amplicon --species hs \
          --starting-material rna \
          --5-end v-primers \
          --3-end j-primers \
          --adapters adapters-present \
          input_R1.fastq input_R2.fastq analysis

This will produce files with detailed information about calculated clonotypes (``analysis.clonotypes.<chains>.txt``).

Details
"""""""

Under the hood ``analyze amplicon`` will execute the following MiXCR pipeline:

1. :ref:`Align <ref-align>` raw sequences to reference sequences of segments
   (V, D, J) of IGH gene:

  ::

    > mixcr align -s hs -OvParameters.geneFeatureToAlign=VTranscript \
      --report analysis.report input_R1.fastq input_R2.fastq analysis.vdjca

  Here the non-default value for gene feature used to align V genes (``-OvParameters.geneFeatureToAlign=VTranscript``) in order to utilize information from both reads, more specifically to let MiXCR align V gene's 5'UTRS and parts of coding sequence on 5'-end with sequence from read opposite to CDR3. MiXCR will also produce report file (specified by optional parameter ``--report``) containing run statistics which looks like this:

  ::

    Analysis Date: Mon Aug 25 15:22:39 MSK 2014
    Input file(s): input_r1.fastq,input_r2.fastq
    Output file: alignments.vdjca
    Command line arguments: align --report alignmentReport.log input_r1.fastq input_r2.fastq alignments.vdjca
    Total sequencing reads: 323248
    Successfully aligned reads: 210360
    Successfully aligned, percent: 65.08%
    Alignment failed because of absence of V hits: 4.26%
    Alignment failed because of absence of J hits: 30.19%
    Alignment failed because of low total score: 0.48%

  One can convert binary output produced by ``align`` (``analysis.vdjca``) to a human-readable text file using :ref:`exportAlignments <ref-export>` command.

2. :ref:`Assemble <ref-assemble>` clonotypes:

  .. code-block:: console

    > mixcr assemble --report analysis.report analysis.vdjca analysis.clna

  This will build clonotypes and additionally correct PCR and sequencing errors. By default, clonotypes will be assembled by CDR3 sequences; one can specify another gene region by passing additional command line arguments (see :ref:`assemble documentation <ref-assemble>`). The optional report ``analysis.report`` contain useful debugging information:

  ::

    Analysis Date: Mon Aug 25 15:29:51 MSK 2014
    Input file(s): alignments.vdjca
    Output file: clones.clns
    Command line arguments: assemble --report assembleReport.log alignments.vdjca clones.clns
    Final clonotype count: 11195
    Total reads used in clonotypes: 171029
    Reads used, percent of total: 52.89%
    Reads used as core, percent of used: 92.04%
    Mapped low quality reads, percent of used: 7.96%
    Reads clustered in PCR error correction, percent of used: 0.04%
    Clonotypes eliminated by PCR error correction: 72
    Percent of reads dropped due to the lack of clonal sequence: 2.34%
    Percent of reads dropped due to low quality: 3.96%
    Percent of reads dropped due to failed mapping: 5.87%

3. :ref:`Export <ref-export>` binary file with a list of clones (``analysis.clna``) to a human-readable text file:

  .. code-block:: console

    > mixcr exportClones --chains TRA analysis.clns analysis.clonotypes.TRA.txt
    > mixcr exportClones --chains TRB analysis.clns analysis.clonotypes.TRB.txt
    > ...

  This will export information about clones with default set of fields, e.g.:

  +-------------+----------------+-----+---------------------+--------------+-----------------+-----------------+-----+
  | Clone count | Clone fraction | ... | V hits              | J hits       | N. seq. CDR3    | AA. seq. CDR3   | ... |
  +=============+================+=====+=====================+==============+=================+=================+=====+
  | 4369        | 2.9E-3         | ... | IGHV4-39\*\00(1388) | IGHJ6        | TGTGTGAG...     | CVRHKPM...      | ... |
  |             |                |     |                     | \*\00(131)   |                 |                 |     |
  +-------------+----------------+-----+---------------------+--------------+-----------------+-----------------+-----+
  | 3477        | 2.5E-3         | ... | IGHV4-34\*\00(1944) | IGHJ4        | TGTGCGAT...     | CAIWDVGL...     | ... |
  |             |                |     |                     | \*\00(153)   |                 |                 |     |
  +-------------+----------------+-----+---------------------+--------------+-----------------+-----------------+-----+
  |      ...    |       ...      | ... |         ...         |      ...     |       ...       |       ...       | ... |
  +-------------+----------------+-----+---------------------+--------------+-----------------+-----------------+-----+

  where dots denote text not shown here (for compactness). For the full list of available export options see :ref:`export <ref-export>` documentation.

Each of the above steps can be customized in order to adapt the analysis pipeline for a specific research task (see below).


.. _ref-exampleFullLength:


High quality full length IG repertoire analysis
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

For the full length cDNA-based immunoglobulin repertoire analysis we generally recommend to prepare libraries with unique molecular identifiers (UMI) and sequence them using asymmetric paired-end 350 bp + 100 bp Illumina MiSeq sequencing (see `Nature Protocols paper <http://www.nature.com/nprot/journal/v11/n9/full/nprot.2016.093.html>`_). This approach allows to obtain long-range high quality sequencing and efficiently eliminate PCR and sequencing errors using `MiGEC software <https://milaboratory.com/software/migec/>`_. The resulting high quality data can be further processed by MiXCR for the efficient full length IGH or IGL repertoire extraction.


The whole analysis may be performed using :ref:`analyze amplicon <ref-analyze-amplicon>` command:

.. code-block:: console

  > mixcr analyze amplicon \
          --species hs \
          --starting-material rna \
          --5-end v-primers \
          --3-end j-primers \
          --adapters adapters-present \
          --receptor-type BCR \
          --region-of-interest VDJRegion \
          --only-productive \
          --align "-OreadsLayout=Collinear" \
          --assemble "-OseparateByC=true" \
          --assemble "-OqualityAggregationType=Average" \
          --assemble "-OclusteringFilter.specificMutationProbability=1E-5" \
          --assemble "-OmaxBadPointsPercent=0" \
          input_R1.fastq input_R2.fastq analysis

This will produce files (``analysis.clonotypes.IGH.txt``, ``analysis.clonotypes.IGK.txt`` and ``analysis.clonotypes.IGL.txt``) with detailed information about obtained clonotypes. Here we specified several optional parameters:

 - ``--receptor-type BCR`` tells MiXCR to that B-cell optimized aligner should be used (this is equivalent to passing ``-p kAligner2`` option for :ref:`align <ref-align>` action) and to export only IG sequences.
 - ``region-of-interest VDJRegion`` passes the ``-OassemblingFeatures=VDJRegion`` to :ref:`assemble <ref-assemble>`
 - ``--only-production`` filters off the out-of-frame and stop codon containing clonotypes in the :ref:`export <ref-export>`
 - ``--align <option>`` passes additional ``<option>`` to the :ref:`align <ref-align>` step
 - ``--assemble <option>`` passes additional ``<option>`` to the :ref:`assemble <ref-assemble>` step


Details
"""""""

The above :ref:`analyze amplicon <ref-analyze-amplicon>` command is equivalent to the execution of the following MiXCR steps.

1. Merging paired-end reads and :ref:`alignment <ref-align>`:

  MiXCR's ``align`` subcommand performs paired-end reads merging and alignment to reference V/D/J and C genes. We recommend using :ref:`KAligner2 <ref-kAligner2>` (currently in beta testing) for the full length immunoglobulin profiling:

  ::
  
    > mixcr align -p kaligner2 -s hs -r alignmentReport.txt -OreadsLayout=Collinear \
      -OvParameters.geneFeatureToAlign=VTranscript read_R1.fastq.gz read_R2.fastq.gz \
      alignments.vdjca
     
  Option ``-s`` allows to specify species (e.g. homo sapiens - ``hsa``, mus musculus - ``mmu``). Parameter ``-OreadsLayout`` allow us to set paired-end reads orientation (``Collinear``, ``Opposite``, ``Unknown``). Note, that after MiGEC analysis paired-end read pairs are in ``Collinear`` orientation.

  Instead of KAligner2, default MiXCR aligner can be used as well, but it may miss immunoglobulin subvariants that contain several nucleotide-lengths indels within the V gene segment.

2. :ref:`Assemble <ref-assemble>` clones:

  ::
  
    > mixcr assemble -r assembleReport.txt -OassemblingFeatures=VDJRegion \
      -OseparateByC=true -OqualityAggregationType=Average \
      -OclusteringFilter.specificMutationProbability=1E-5 -OmaxBadPointsPercent=0 \
      alignments.vdjca clones.clns
  
  ``-OseparateByC=true`` separates clones with different antibody isotype.
  
  Set ``-OcloneClusteringParameters=null`` parameter to switch off the frequency-based correction of PCR errors.
  
  Depending on data quality, one can adjust input threshold by changing the parameter ``-ObadQualityThreshold`` to improve clonotypes extraction. 
  
  See "Assembler parameters" section of documentation for the advanced quality filtering parameters.

3. :ref:`Export <ref-export>` clones:
  
  ::

    > mixcr exportClones -c IGH -o -t clones.clns clones.txt

  where options ``-o`` and ``-t`` filter off the out-of-frame and stop codon containing clonotypes, respectively, and ``-c`` indicates which chain will be extracted (e.g. ``IGH``, ``IGL``).


.. _ref-exampleRnaSeq:

Analysis of RNA-Seq data
^^^^^^^^^^^^^^^^^^^^^^^^

MiXCR allows to extract TCR and BCR CDR3 repertoires from RNA-Seq data. Extraction efficiency depends on the abundance of T or B cells in a sample, and also on the sequencing length. 2x150 bp or 2x100 bp paired-end sequencing is recommended. However, even from the paired-end 2x50 bp RNA-Seq data, information on the major clonotypes present (e.g. in a tumor sample) can usually be extracted. For detailed description please see :ref:`here <ref-rna-seq>`. 

The analysis can be performed in the following way using single :ref:`analyze shotgun <ref-analyze-shotgun>` command:

.. code-block:: console

  > mixcr analyze shotgun \
          --species hs \
          --starting-material rna \
          --only-productive \
          input_R1.fastq input_R2.fastq analysis

This will produce files (``analysis.clonotypes.TRA.txt``, ``analysis.clonotypes.IGH.txt`` etc.) with detailed information about obtained clonotypes. 


Details
"""""""

Under the hood the following pipeline will be evaluated:

1. :ref:`Align <ref-align>` reads:

  .. code-block:: console

    > mixcr align -s hs -p rna-seq -OallowPartialAlignments=true data_R1.fastq.gz data_R2.fastq.gz alignments.vdjca
  
  All ``mixcr align`` parameters can also be used here (e.g. ``-s`` to specify organism). 

  ``-OallowPartialAlignments=true`` option preserves partial alignments for their further use in ``assemblePartial``.

2. :ref:`Assemble parial reads <ref-assemblePartial>`:

  .. code-block:: console

    > mixcr assemblePartial alignments.vdjca alignmentsRescued.vdjca

  To obtain more assembled reads containing full CDR3 sequence it is recommended to perform several iterations of reads assembling using ``mixcr assemblePartial`` action. ``-p`` parameter is required for several iterations. In our experience, the best result is obtained after the second iteration:

  .. code-block:: console

    > mixcr assemblePartial alignments.vdjca alignmentsRescued_1.vdjca

    > mixcr assemblePartial alignmentsRescued_1.vdjca alignmentsRescued_2.vdjca

3. Extend TCR alignments with uniquely determined V and J genes and having incomplete coverage of CDR3s using germline sequences:

  .. code-block:: console

    > mixcr extendAlignments alignmentsRescued_2.vdjca alignmentsRescued_2_extended.vdjca

4. :ref:`Assemble <ref-assemble>` clones:

  .. code-block:: console

    > mixcr assemble alignmentsRescued_2_extended.vdjca clones.clns

  All ``mixcr assemble`` parametrs can also be used here.

  - For poor quality data it is recommended to decrease input quality threshold (e.g. ``-ObadQualityThreshold=15``).

  - To make error correction algorithms to combine clone abundancies add the following option: ``-OaddReadsCountOnClustering=true``

5. :ref:`Exporting <ref-export>` clones:

  .. code-block:: console

    > mixcr exportClones -c TRA -o -t clones.clns clones.txt

  One can specify immune receptor chain of interest to extract (``-c TRA`` or ``-c TRB``, etc) and exclude out-of-frame (option ``-o``) and stop codon containing variants (option ``-t``).


.. _ref-exampleMouse:

Assembling of CDR3-based clonotypes for mouse TRB sample
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This example shows how to perform routine assembly of clonotypes (based on CDR3 sequence) for mouse TRB library (aligning is performed for all possible genes - TRA/B/D/G and IGH/L/K, but only TRB clones are exported in the final table at the end).

.. code-block:: console

  > mixcr analyze amplicon --species mmu \
          --starting-material rna \
          --receptor-type TRB \
          --5-end v-primers \
          --3-end j-primers \
          --adapters adapters-present \
          input_R1.fastq input_R2.fastq analysis


Details
"""""""

The above command executes to the following pipeline:

.. code-block:: console

  > mixcr align --species mmu input_R1.fastq input_R2.fastq alignments.vdjca

  > mixcr assemble alignments.vdjca clones.clns

  > mixcr exportClones --chains TRB clones.clns clones.txt


.. _ref-exampleBackwardLinks:


Saving links between initial reads and clones
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

FIXME: broken


In this example we demonstrate how to extract initial read headers for assembled clonotypes. On the ``align`` step additional ``--save-reads`` option should be specified in order to store initial reads in the resulting ``.vdjca`` file: 

.. code-block:: console

  > mixcr align -s hs --save-reads input_R1.fastq input_R2.fastq alignments.vdjca

On the ``assemble`` stage it is necessary to specify that the alignments should be saved:

.. code-block:: console

  > mixcr assemble --write-alignments alignments.vdjca clones.clna

Having this, it is possible to export original read headers with corresponding clone IDs:

.. code-block:: console

  > mixcr exportAlignments -cloneId -descrR1 -descrR2 clones.clna alignments.txt

The resulting file ``alignments.txt`` will looks like:


+-----------+-----------------+-----------------+
| Clone ID  | Description R1  | Description R2  |
+===========+=================+=================+
|     10    | header_1_R1     | header_1_R2     |
+-----------+-----------------+-----------------+
|           | header_2_R1     | header_2_R2     |
+-----------+-----------------+-----------------+
|    2313   | header_3_R1     | header_3_R2     |
+-----------+-----------------+-----------------+
|   88142   | header_5_R1     | header_5_R2     |
+-----------+-----------------+-----------------+
|    ...    |     ...         |     ...         |
+-----------+-----------------+-----------------+

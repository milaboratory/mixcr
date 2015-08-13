Quick start
==============

Typical workflow of MiXCR consists of three steps: alignment of raw sequencing reads with reference V, D, J genes, assembling of clones from aligned reads and exporting of necessary data columns for assembled clonotypes to a tab-delimited text file. There are many parameters that a user can change to adapt MiXCR for particular needs. While all these parameters are optional there is a set of parameters that are worth considering before running the analysis:

- ``-OvParameters.geneFeatureToAlign`` sets the gene feature of V gene used for alignment. Applied on the `alignment <#align>`__ stage. Choice of the value for this parameter depends on the type of starting material and library preparation strategy used. There are three options covering most of the cases (see `Section 5 <#geneFeatures>`__ for the full list):

  - ``VRegion`` **(default)** is generally suitable for majority of use cases, on the other hand if you have some additional information about your library it is a good idea to use one of the values mentioned below instead of default. Don't change the default value if your library is prepared using multiplex PCR on the V gene side.

  - ``VTranscript`` if RNA was used as a starting material and some kind of non-template-specific technique was used for further amplification on the 5'-end of RNA (e.g. 5'RACE) (see `Example E2 <#e2>`__). Using of this option is useful for increasing of sequencing information utilization from 5'-end of the molecule, which in turn helps to increase accuracy of V gene identification.

  - ``VGene`` if DNA was used as a starting material and 5' parts of V gene (including V intron, leader sequence and 5'UTR) are supposed to be present in your data. Using of this option is useful for increasing of sequencing information utilization from 5'-end of the molecule, which in turn helps to increase accuracy of V gene identification.

  *Use* ``VTranscript`` *or* ``VGene`` *if you plan to assemble full-length clonotypes (including all FRs and CDRs) of T- or B- cell receptors.*

- The ``-OassemblingFeatures`` parameter sets the region of TCR/BCR sequence which will be used to assemble clones. Applyed on the `assembly <#assembly>`__ stage. By default its value is ``CDR3`` which results in assembling of clones by the sequence of *Complementarity Determining Region 3*. To analyse full length sequences use ``VDJRegion`` as a value for the ``assemblingFeatures`` (see `Section 5 <#geneFeatures>`__ for more details).

- Another important parameter is ``--species``, it sets the target organism. This parameter is used on the `alignment <#align>`__ stage. Possible values are ``hsa`` (or ``HomoSapiens``) and ``mmu`` (or ``MusMusculus``). Default value is ``hsa``. This parameter should be supplied on the alignment (``align``) stage. See `Example E4 <#e4>`__.

The following sections describes common use cases

Examples
--------

Default workflow
^^^^^^^^^^^^^^^^

.. tip::
  Parameters used in this example are particularly suitable for analysis of **multiplex-PCR** selected fragments of T-/B- cell receptor genes.

MiXCR can be used with the default parameters in most cases by executing
the following sequence of commands:

.. code-block:: console

  > mixcr align --loci IGH input_R1.fastq input_R2.fastq alignments.vdjca

  ... Building alignments

  > mixcr assemble alignments.vdjca clones.clns

  ... Assembling clones

  > mixcr exportClones clones.clns clones.txt

  ... Exporting clones to tab-delimited file


The value of only one parameter is changed from its default in this snippet (``--loci IGH``) to tell MiXCR to search for IGH sequences. However even this parameter can be omitted (in this case MiXCR will search through all possible T-/B- cell receptor sequences: ``TRA``, ``TRB``, ``TRG``, ``TRD``, ``IGH``, ``IGL``, ``IGK``). *Omitting of* ``--loci`` *is not recommended.*

The file produced (``clone.txt``) will contain a tab-delimited table with information about all clonotypes assembled by CDR3 sequence (clone abundance, CDR3 sequence, V, D, J genes, etc.). For full length analysis and other useful features see examples below.

Analysis of data obtained using 5'RACE-based amplification protocols
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Consider MiXCR workflow in more detail on analysis of paired-end
sequenced cDNA library of IGH gene prepared using 5'RACE-based protocol
(i.e. onе read covers CDR3 with surroundings and another one covers
5'UTR and downstream sequence of V gene):

1. `Align <#align>`__ raw sequences to reference sequences of segments
   (V, D, J) of IGH gene:

  .. code-block:: console

    > mixcr align --loci IGH -OvParameters.geneFeatureToAlign=VTranscript --report alignmentReport.log input_R1.fastq input_R2.fastq alignments.vdjca

  Here we specified non-default value for gene feature used to align V genes (``-OvParameters.geneFeatureToAlign=VTranscript``) in order to utilize information from both reads, more specifically to let MiXCR align V gene's 5'UTRS and parts of coding sequence on 5'-end with sequence from read opposite to CDR3. MiXCR can also produce report file (specified by optional parameter ``--report``) containing run statistics which looks like this:

  ::

    Analysis Date: Mon Aug 25 15:22:39 MSK 2014
    Input file(s): input_r1.fastq,input_r2.fastq
    Output file: alignments.vdjca
    Command line arguments: align --loci IGH --report alignmentReport.log input_r1.fastq input_r2.fastq alignments.vdjca
    Total sequencing reads: 323248
    Successfully aligned reads: 210360
    Successfully aligned, percent: 65.08%
    Alignment failed because of absence of V hits: 4.26%
    Alignment failed because of absence of J hits: 30.19%
    Alignment failed because of low total score: 0.48%

  One can convert binary output produced by ``align`` (``output.vdjca``) to a human-readable text file using `exportAlignments <#export>`__ command.

2. `Assemble <#assemble>`__ clonotypes:

  .. code-block:: console

    > mixcr assemble --report assembleReport.log alignments.vdjca clones.clns

  This will build clonotypes and additionally correct PCR and sequencing errors. By default, clonotypes will be assembled by CDR3 sequences; one can specify another gene region by passing additional command line arguments (see `assemble <#assemble>`__ documentation). The optional report ``assembleReport.log`` will look like:

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

3. `Export <#export>`__ binary file with a list of clones (``clones.clns``) to a human-readable text file:

  .. code-block:: console

    > mixcr exportClones clones.clns clones.txt

  This will export information about clones with default set of fields, e.g.:

  .. include:: example_output.rst

  where dots denote rows not shown here (for compactness). For the full list of available export options see `export <#export>`__ documentation.

Each of the above steps can be customized in order to adapt the analysis pipeline for a specific research task (see below).

Full length IGH analysis
^^^^^^^^^^^^^^^^^^^^^^^^

1. To build clonotypes based on the full-length sequence of variable part of IGH gene (not V gene only, but V-D-J junction with whole V Region and J Region) one need to obtain alignments fully covering V Region (like in E2). For example:

  .. code-block:: console

    > mixcr align --loci IGH -OvParameters.geneFeatureToAlign=VTranscript input_R1.fastq input_R2.fastq alignments.vdjca

2. Then assemble clones with corresponding option (``-OassemblingFeatures=VDJRegion``):

  .. code-block:: console

    > mixcr assemble -OassemblingFeatures=VDJRegion alignments.vdjca clones.clns

3. And export clones to a tab-delimited file:

  .. code-block:: console

    > mixcr exportClones clones.clns clones.txt

Resulting file will contain assembled clonotypes with sequences of all
regions (``CDR1``, ``CDR2``, ``CDR3``, ``FR1``, ``FR2``, ``FR3``,
``FR4``) for each clone.

Assembling of CDR3-based clonotypes for mouse TRB sample
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

This example shows how to perform routine assembly of clonotypes (based on CDR3 sequence) for mouse TRB library (analysis for other genes can be performed by setting different value for the ``--loci`` parameter, or even omitting it to search for all possible genes - TRA/B/D/G and IGH/L/K).

.. code-block:: console

  > mixcr align --loci TRB --species mmu input_R1.fastq input_R2.fastq alignments.vdjca

Other analysis stages can be executed without any additional parameters:

.. code-block:: console

  > mixcr assemble alignments.vdjca clones.clns

  > mixcr exportClones clones.clns clones.txt
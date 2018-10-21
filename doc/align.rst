.. |br| raw:: html

   <br />

.. include:: <isonum.txt>
.. _ref-align:

Alignment
=========

.. tip:: 

  MiXCR provides :ref:`analyze <ref-analyze>` command that packs a complicated execution pipelines (alignment, assembly, exporting etc.) into a single command. We recommend to use :ref:`analyze <ref-analyze>` for most types of input libraries instead of manual execution of all MiXCR analysis steps. Alignment options described in this section may be directly passed in :ref:`analyze <ref-analyze>` command using ``--align <option>`` option.

The ``align`` command aligns raw sequencing reads to reference V, D, J and C genes of T- and B- cell receptors. It has the following syntax:

::

    mixcr align --species <species> [options] input_file1 [input_file2] output_file.vdjca

MiXCR supports ``fasta``, ``fastq``, ``fastq.gz`` and paired-end ``fastq`` and ``fastq.gz`` input. In case of paired-end reads two input files should be specified.

To print help use:

::

    mixcr help align


.. _ref-align-cli-params:

Command line parameters
-----------------------


The following table describes command line options for ``align``:

.. list-table::
    :widths: 15 10 30
    :header-rows: 1

    * - Option
      - Default value
      - Description

    * - ``-r {file}`` |br| ``--report ...``
      -
      - Report file name. If this option is not specified, no report file be produced. See :ref:`below <ref-align-report>` for detailed description of report fields.

    * - ``-s {speciesName}`` |br| ``--species ...``
      - 
      - Species (organism). This option is required. Possible values: ``hsa`` (or ``HomoSapiens``), ``mmu`` (or ``MusMusculus``), ``rat`` (currently only ``TRB``, ``TRA`` and ``TRD`` are supported), or any species from IMGT |reg| library, if it is used (see here :ref:`import segments <ref-importSegments>`)

    * - ``-p {parameterName}`` |br| ``--parameters ...``
      - ``default``
      - Preset of parameters. Possible values: ``default``, ``kAligner2`` (B-cell analysis with long gaps) and ``rna-seq``. The ``kAligner2`` preset are specifically optimized for analysis of BCR data. The ``rna-seq`` preset are specifically optimized for analysis of Rna-Seq data :ref:`(see below) <ref-rna-seq>`

    * - ``-t {numberOfThreads}`` |br| ``--threads ...``
      - number of CPU cores in the system
      - number of alignment threads

    * - ``-n {numberOfReads}`` |br| ``--limit ...``
      -
      - Limit number of input sequences (only first ``-n`` sequences will be processed; useful for testing).

    * - ``-b`` |br| ``--library``
      - ``default``
      - V/D/J/C segment library name (see :ref:`using external library <ref-importSegments>` fro details)

    * - ``-g`` |br| ``--save-reads``
      -
      - Copy original reads from ``.fastq`` or ``.fasta`` to ``.vdjca`` file (this option is required for further export of original reads, e.g. to export reads aggregated into a clone; see :ref:`this section <ref-exporting-reads>` for details).

    * - ``--no-merge``
      -
      - Do not try to merge paired reads.

    * - ``--not-aligned-R1`` |br| ``--not-aligned-R2``
      -
      - Write all reads that were not aligned (R1 / R2 correspondingly) to the specific file.

    * - ``-Oparameter=value``
      -
      - Overrides default value of aligner ``parameter`` (see next subsection).

All parameters are optional except ``--species``.

.. _ref-aligner-parameters:

Aligner parameters
------------------

MiXCR uses a wide range of parameters that controls aligner behaviour. There are some global parameters and gene-specific parameters organized in groups: ``vParameters``, ``dParameters``, ``jParameters`` and ``cParameters``. Each group of parameters may contains further subgroups of parameters etc. In order to override some parameter value one can use ``-O`` followed by fully qualified parameter name and parameter value (e.g. ``-Ogroup1.group2.parameter=value``).

One of the key MiXCR features is ability to specify particular :ref:`gene regions <ref-geneFeatures>` which will be extracted from reference and used as a targets for alignments. Thus, each sequencing read will be aligned to these extracted reference regions. Parameters responsible for target gene regions are:

+--------------------------------------+-----------------+--------------------------------------------------------------+
| Parameter                            | Default value   | Description                                                  |
+======================================+=================+==============================================================+
| ``vParameters.geneFeatureToAlign``   | ``VRegion``     | region in V gene which will be used as target in ``align``   |
+--------------------------------------+-----------------+--------------------------------------------------------------+
| ``dParameters.geneFeatureToAlign``   | ``DRegion``     | region in D gene which will be used as target in ``align``   |
+--------------------------------------+-----------------+--------------------------------------------------------------+
| ``jParameters.geneFeatureToAlign``   | ``JRegion``     | region in J gene which will be used as target in ``align``   |
+--------------------------------------+-----------------+--------------------------------------------------------------+
| ``cParameters.geneFeatureToAlign``   | ``CExon1``      | region in C gene which will be used as target in ``align``   |
+--------------------------------------+-----------------+--------------------------------------------------------------+

It is important to specify these gene regions such that they will fully cover target clonal gene region which will be used in :ref:`assemble <ref-assemble>` (e.g. CDR3).

One can override default gene regions in the following way:

::

    mixcr align -OvParameters.geneFeatureToAlign=VTranscript input_file1 [input_file2] output_file.vdjca

Other global aligner parameters are:


+------------------------------------+---------------+---------------------------------------------------------------------------------------+
| Parameter                          | Default value | Description                                                                           |
+====================================+===============+=======================================================================================+
|  ``saveOriginalReads``             | ``false``     | Save original sequencing reads in ``.vdjca`` file.                                    |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
|  ``allowPartialAlignments``        | ``false``     | Save incomplete alignments (e.g. only V / only J) in ``.vdjca`` file                  |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
|  ``allowChimeras``                 | ``false``     | Accept alignments with different loci of V and J genes (by default such alignments    |
|                                    |               | are dropped).                                                                         |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
|  ``minSumScore``                   | ``120.0``     | Minimal total alignment score value of V and J genes.                                 |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
|  ``maxHits``                       | ``5``         | Maximal number of hits for each gene type: if input sequence align to more than       |
|                                    |               | ``maxHits`` targets, then only  top ``maxHits`` hits will be kept.                    |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
|  ``minimalClonalSequenceLength``   | ``12``        | Minimal clonal sequence length (e.g. minimal sequence of CDR3 to be used for clone    |
|                                    |               | assembly)                                                                             |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
|  ``vjAlignmentOrder``              | ``VThenJ``    | Order in which V and J genes aligned in target (possible values ``JThenV`` and        |
|  (*only for single-end*            |               | ``VThenJ``). Parameter affects only *single-read* alignments and alignments of        |
|  *analysis*)                       |               | overlapped paired-end reads. Non-overlaping paired-end reads are always processed in  |
|                                    |               | ``VThenJ`` mode. ``JThenV`` can be used for short reads (~100bp) with full (or nearly |
|                                    |               | full) J gene coverage.                                                                |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
| ``relativeMinVFR3CDR3Score``       | ``0.7``       | Relative minimal alignment score of ``FR3+VCDR3Part`` region for V gene. V hit will   | 
| (*only for paired-end*             |               | be kept only if its ``FR3+VCDR3Part`` part aligns with score greater than             |
| *analysis*)                        |               | ``relativeMinVFR3CDR3Score * maxFR3CDR3Score``, where ``maxFR3CDR3Score`` is the      |
|                                    |               | maximal alignment score for ``FR3+VCDR3Part`` region among all of V hits for current  |
|                                    |               | input reads pair.                                                                     | 
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
| ``readsLayout``                    | ``Opposite``  | Relative orientation of paired reads. Available values: ``Opposite``, ``Collinear``,  |
| (*only for paired-end*             |               | ``Unknown``.                                                                          |
| *analysis*)                        |               |                                                                                       |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+

.. raw:: html

   <!--
   | `relativeMinVScore` <br> (_only for paired-end analysis_)| 0.7 | Relative minimum score of V gene. Only those V hits will be considered, which score is greater then `relativeMinVScore * maxVScore`, where `maxVScore` is the maximum score throw all obtained V hits. |-->

One can override these parameters in the following way:

::

    mixcr align --species hs -OmaxHits=3 input_file1 [input_file2] output_file.vdjca


.. _ref-vdjc-aligners-parameters:

V, J and C aligners parameters
------------------------------

MiXCR uses same types of aligners to align V, J and C genes (``KAligner`` from `MiLib <https://github.com/milaboratory/milib>`_; the idea of ``KAligner`` is inspired by `this article <http://nar.oxfordjournals.org/content/41/10/e108>`_). These parameters are placed in ``parameters`` subgroup and can be overridden using e.g. ``-OjParameters.parameters.mapperKValue=7``. The following parameters for V, J and C aligners are available:

+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+
| Parameter                | Default  | Default  | Default   | Description                                                                |
|                          | V value  | J value  | C value   |                                                                            |
+==========================+==========+==========+===========+============================================================================+
| ``mapperKValue``         | ``5``    | ``5``    | ``5``     | Length of seeds used in aligner.                                           |
+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+
| ``floatingLeftBound``    | ``true`` | ``true`` | ``false`` | Specifies whether left bound of  alignment is fixed or float: if           |
|                          |          |          |           | ``floatingLeftBound`` set to false, the left bound of either target        |
|                          |          |          |           | or query will be aligned. Default values are suitable in most cases.       |
+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+
| ``floatingRightBound``   | ``true`` | ``true`` | ``false`` | Specifies whether right bound of alignment is fixed or float:              |
|                          |          |          |           | if ``floatingRightBound`` set to false, the right bound of either          |
|                          |          |          |           | target or query will be aligned. Default values are suitable in most       | 
|                          |          |          |           | cases. If your target molecules have no primer sequences in J Region       |
|                          |          |          |           | (e.g. library was amplified using primer to the C region) you can          |
|                          |          |          |           | change value of this parameter for J gene to ``false`` to increase         |
|                          |          |          |           | J gene identification accuracy and overall specificity of alignments.      |
+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+
| ``minAlignmentLength``   | ``15``   | ``15``   | ``15``    | Minimal length of aligned region.                                          |
+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+
| ``maxAdjacentIndels``    | ``2``    | ``2``    | ``2``     | Maximum number of indels between two seeds.                                |
+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+
| ``absoluteMinScore``     | ``40.0`` | ``40.0`` | ``40.0``  | Minimal score of alignment: alignments with smaller score will be dropped. |
+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+
| ``relativeMinScore``     | ``0.87`` | ``0.87`` | ``0.87``  | Minimal relative score of  alignments: if alignment score is smaller than  |
|                          |          |          |           | ``relativeMinScore * maxScore``,  where ``maxScore`` is the best score     |
|                          |          |          |           | among all alignments for particular gene type (V, J or C) and input        |
|                          |          |          |           | sequence, it will be dropped.                                              |
+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+
| ``maxHits``              | ``7``    | ``7``    | ``7``     | Maximal number of hits: if input sequence align with more than ``maxHits`` |
|                          |          |          |           | queries, only top ``maxHits`` hits will be kept.                           |
+--------------------------+----------+----------+-----------+----------------------------------------------------------------------------+

These parameters can be overridden like in the following example:

::

    mixcr align --species hs  \
                -OvParameters.parameters.minAlignmentLength=30 \
                -OjParameters.parameters.relativeMinScore=0.7 \ 
                input_file1 [input_file2] output_file.vdjca

Scoring used in aligners is specified by ``scoring`` subgroup of
parameters. It contains the following parameters:

+------------------+----------------------------------------+-----------------------------------------------------------------------------+
| Parameter        | Default value                          | Description                                                                 |
+==================+========================================+=============================================================================+
| ``subsMatrix``   | ``simple(match = 5,``                  | Substitution matrix. Available types:                                       |
|                  |  ``mismatch = -9)``                    |                                                                             |
|                  |                                        |  - ``simple`` --- a matrix with diagonal elements equal to ``match`` and    |
|                  |                                        |    other elements equal to ``mismatch``                                     |
|                  |                                        |  - ``raw`` --- a complete set of 16 matrix elements should be specified;    | 
|                  |                                        |    for  example:                                                            |
|                  |                                        |    ``raw(5,-9,-9,-9,-9,5,-9,-9,-9,-9,5,-9,-9,-9,-9,5)``                     |
|                  |                                        |    (*equivalent to the  default value*)                                     |
+------------------+----------------------------------------+-----------------------------------------------------------------------------+
| ``gapPenalty``   | ``-12``                                | Penalty for gap.                                                            |
+------------------+----------------------------------------+-----------------------------------------------------------------------------+

Scoring parameters can be overridden in the following way:

::

    mixcr align --species hs -OvParameters.parameters.scoring.gapPenalty=-20 input_file1 [input_file2] output_file.vdjca

::

    mixcr align --species hs -OvParameters.parameters.scoring.subsMatrix=simple(match=4,mismatch=-11) \
                 input_file1 [input_file2] output_file.vdjca

.. _ref-dAlignerParameters:

D aligner parameters
--------------------

The following parameters can be overridden for D aligner:

+------------------------+-----------------+----------------------------------------------------------------------------------------------+
| Parameter              | Default value   | Description                                                                                  |
+========================+=================+==============================================================================================+
| ``absoluteMinScore``   | ``30.0``        | Minimal score of alignment: alignments with smaller scores will be dropped.                  |
+------------------------+-----------------+----------------------------------------------------------------------------------------------+
| ``relativeMinScore``   | ``0.85``        | Minimal relative score of alignment: if alignment score is smaller than                      |
|                        |                 | ``relativeMinScore * maxScore``, where ``maxScore`` is the best score among all alignments   |
|                        |                 | for particular sequence, it will be dropped.                                                 |
+------------------------+-----------------+----------------------------------------------------------------------------------------------+
| ``maxHits``            | ``3``           | Maximal number of hits: if input sequence align with more than ``maxHits`` queries, only top |
|                        |                 | ``maxHits`` hits will be kept.                                                               |
+------------------------+-----------------+----------------------------------------------------------------------------------------------+

One can override these parameters like in the following example:

::

    mixcr align --species hs -OdParameters.absoluteMinScore=10 input_file1 [input_file2] output_file.vdjca

Scoring parameters for D aligner are the following:

+-------------------------+----------------------------------------+--------------------------------------------------------------------+
| Parameter               | Default value                          | Description                                                        |
+=========================+========================================+====================================================================+
| ``type``                | ``affine``                             | Type of scoring. Possible values: ``affine``, ``linear``.          |
+-------------------------+----------------------------------------+--------------------------------------------------------------------+
| ``subsMatrix``          | ``simple(match = 5,``                  | Substitution matrix. Available types:                              |
|                         |  ``mismatch = -9)``                    |                                                                    |
|                         |                                        |  - ``simple`` --- a matrix with diagonal elements equal to         |
|                         |                                        |    ``match`` and other elements equal to ``mismatch``              |
|                         |                                        |  - ``raw`` --- a complete set of 16 matrix elements should be      |
|                         |                                        |    specified; for  example:                                        |
|                         |                                        |    ``raw(5,-9,-9,-9,-9,5,-9,-9,-9,-9,5,-9,-9,-9,-9,5)``            |
|                         |                                        |     (*equivalent to the default value*)                            |
+-------------------------+----------------------------------------+--------------------------------------------------------------------+
| ``gapOpenPenalty``      | ``-10``                                | Penalty for gap opening.                                           |
+-------------------------+----------------------------------------+--------------------------------------------------------------------+
| ``gapExtensionPenalty`` | ``-1``                                 | Penalty for gap extension.                                         |
+-------------------------+----------------------------------------+--------------------------------------------------------------------+

These parameters can be overridden in the following way:

::

    mixcr align --species hs -OdParameters.scoring.gapExtensionPenalty=-5 input_file1 [input_file2] output_file.vdjca



.. _ref-align-overlap:

Paired-end reads overlap
------------------------

MiXCR tries to overlap paired-end (PE) reads if it is possible (overlap here is used in the same sense as in e.g. PEAR software). There are two stages when MiXCR decides to merge R1 and R2 reads:


    1. Before PE-read alignment.

        Using algorithm similar to PEAR an other software. The following thresholds are used (not listed above):

        ``-OmergerParameters.minimalOverlap=17`` (minimal number of nucleotides to overlap)

        ``-OmergerParameters.minimalIdentity=0.9`` (minimal identity, minimal fraction of matching nucleotides between sequences)

    2. After PE-read alignment.

        If two reads were aligned against the same V gene (which is the most common case; while the same algorithm is applied to J alignments), and MiXCR detects that the same nucleotides (positions in the reference sequence) were aligned in both mates - this is a strong evidence that paired-end reads actually overlap. In this case MiXCR merges them into a single sequence using this new information. Overlap offset is determined by alignment ranges in reference sequence. This helps to merge PE-reads which overlap even by a single nucleotide. ``Alignment-aided overlaps`` field from report file, shows the number of such overlaps.

        During this procedure, performs a check on sequence equality in the overlapping region, if it fails merge is aborted (sequences are too different; the same ``-OmergerParameters.minimalIdentity`` value is used here as threshold). Another piece of the information MiXCR gains from this event, is that certain paradoxical condition is found, this may be a sign of false-positive alignment in one of the PE reads. In this case MiXCR drops one of the alignments (one that have smaller score). Number of such evens is shown in ``Paired-end alignment conflicts eliminated`` field in report.

.. _ref-align-report:

Report
------

Summary of alignment procedure can be exported with ``-r``/``--report`` option. Report is appended to the end of the file if it already exist, the same file name can be used in several analysis runs.

Report contains the following lines:

.. list-table::
    :widths: 5 10
    :header-rows: 1

    * - Report line
      - Description

    * - Total sequencing reads
      - Total number of analysed sequencing

    * - Successfully aligned reads
      - Number of successful alignments. Number of alignments written to the output file. |br| Without ``-OallowPartialAlignments=true`` (default behaviour): number of reads with both V and J alignments, that passed all alignment thresholds. |br| With ``-OallowPartialAlignments=true`` (see :ref:`here <ref-rna-seq>` for details): number of reads with at least one of V or J alignments, that passed all alignment thresholds and cover at least one nucleotide of CDR3.

    * - Chimeras
      - Number of detected chimeras. This option will not be added to the report if no chimeric alignments were detected (e.g. by default MiXCR drops all chimeric alignments; to allow chimeras, add ``-OallowChimeras=true`` option to the command line). Chimeric alignment is defined as as having V, J or C genes from the incompatible chains, e.g. TRBV / TRAJ or IGHV / TRBC, etc...)

    * - Paired-end alignment conflicts eliminated
      - (see :ref:`above descriptions <ref-align-overlap>` for details of PE merging procedure)

    * - Overlapped
      - Total number of overlapped paired-end reads (see :ref:`above <ref-align-overlap>` for more details)

    * - Overlapped and aligned
      - Total number of reads that were overlapped and aligned (in any order) (see :ref:`above <ref-align-overlap>` for more details)

    * - Alignment-aided overlaps
      - (see :ref:`above descriptions <ref-align-overlap>` for details of PE merging procedure). High value, may indicate problems with the sequencing data being analysed (any data pre-processing step may be the source of this problem or this may be a sign of invitro chimerization). Small number of such events is ok, especially for RNA-Seq and similar data, that contains unspliced or wrongly spliced sequences (see this `comment <https://github.com/milaboratory/mixcr/issues/332#issuecomment-366035395>`_ for an illustration of this problem)

    * - V gene chimeras / J gene chimeras
      - Number of events where different V or J genes correspondingly were aligned in different paired-end reads. This type of chimerization is different from one mentioned for "Chimeras" report line. High number of such events for V genes is a strong evidence of sample preparation problems, raw data should be manually inspected to verify expected library structure.

    * - ... chains
      - Number of reads aligned with this type of immunological chain. E.g. TRB for TRBV+TRBJ[+TRBC]. Empty chain name is for chimeras.
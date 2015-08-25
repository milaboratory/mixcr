.. _ref-align:

Alignment
=========


The ``align`` command aligns raw sequencing reads to reference V, D, J and C genes of T- and B- cell receptors. It has the following syntax:

::

    mixcr align [options] input_file1 [input_file2] output_file.vdjca

MiXCR supports ``fasta``, ``fastq``, ``fastq.gz`` and paired-end ``fastq`` and ``fastq.gz`` input. In case of paired-end reads two input files should be specified.

.. raw:: html

   <!-- 

   MiXCR uses the following algorithms in case of single and paired-end reads: 

    * In case of single reads it <span style="color: red;">WRITE DESCRIPTION</span>
    * in case of paired-end reads it <span style="color: red;">WRITE DESCRIPTION</span>

   -->

Command line parameters
-----------------------

The following table contains description of command line options for ``align``:

+----------------------------------+----------------------------+------------------------------------------------------------+
| Option                           | Default value              | Description                                                |
+==================================+============================+============================================================+
| ``-h``, ``--help``               |                            | Print help message.                                        |
+----------------------------------+----------------------------+------------------------------------------------------------+
| ``-r``, ``--report``             |                            | Report file name. If this option is not                    |
|                                  |                            | specified, no report file be produced.                     |
+----------------------------------+----------------------------+------------------------------------------------------------+
| ``-l``, ``--loci``               | ``ALL``                    | Target immunological loci list separated by "``,``".       |
|                                  |                            | Available values: ``IGH``, ``IGL``, ``IGK``, ``TRA``,      |
|                                  |                            | ``TRB``, ``TRG``, ``TRD``, ``IG`` (for all immunoglobulin  |
|                                  |                            | loci), ``TCR`` (for all T-cell receptor loci), ``ALL``     |
|                                  |                            | (for all loci) .                                           |
+----------------------------------+----------------------------+------------------------------------------------------------+
| ``-s``, ``--species``            | ``HomoSapiens``            | Species (organism). Possible values: ``hsa`` (or           |
|                                  |                            | ``HomoSapiens``) and ``mmu`` (or ``MusMusculus``).         |
+----------------------------------+----------------------------+------------------------------------------------------------+
| ``-i``, ``--diff-loci``          |                            | Accept alignments with different loci of V and J genes     |
|                                  |                            | (by default such alignments are dropped).                  |
+----------------------------------+----------------------------+------------------------------------------------------------+
| ``-t``, ``--threads``            | number of                  | Number of processing threads.                              |
|                                  | available CPU cores        |                                                            |
+----------------------------------+----------------------------+------------------------------------------------------------+
| ``-n``, ``--limit``              |                            | Limit number of sequences that will be analysed (only      | 
|                                  |                            | first ``-n`` sequences will be processed from input        |
|                                  |                            | file(s)).                                                  |
+----------------------------------+----------------------------+------------------------------------------------------------+
| ``-a``, ``--save-description``   |                            | Copy read(s) description line from ``.fastq`` or           |
|                                  |                            | ``.fasta`` to ``.vdjca`` file (can be then exported with   |
|                                  |                            | ``-descrR1`` and ``-descrR2`` options in                   |
|                                  |                            | :ref:`exportAlignments <ref-export>` action).              |
+----------------------------------+----------------------------+------------------------------------------------------------+
| ``-Oparameter=value``            |                            | Overrides default value of aligner ``parameter``           |
|                                  |                            | (see next subsection).                                     |
+----------------------------------+----------------------------+------------------------------------------------------------+

All parameters are optional.

Aligner parameters
------------------

MiXCR uses a wide range of parameters that controls aligner behaviour. There are some global parameters and gene-specific parameters organized in groups: ``vParameters``, ``dParameters``, ``jParameters`` and ``cParameters``. Each group of parameters may contain further subgroups of parameters etc. In order to override some parameter value one can use ``-O`` followed by fully qualified parameter name and parameter value (e.g. ``-Ogroup1.group2.parameter=value``).

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
| ``minSumScore``                    | ``120.0``     |  Minimal total alignment score value of V and J genes.                                |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
| ``maxHits``                        | ``5``         | Maximal number of hits for each gene type: if input sequence align to more than       |
|                                    |               | ``maxHits`` targets, then only  top ``maxHits`` hits will be kept.                    |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
| | ``relativeMinVFR3CDR3Score``     | ``0.7``       | Relative minimal alignment score of ``FR3+VCDR3Part`` region for V gene. V hit will   | 
| | (*only for paired-end*           |               | be kept only if its ``FR3+VCDR3Part`` part aligns with score greater than             |
| | *analysis*)                      |               | ``relativeMinVFR3CDR3Score * maxFR3CDR3Score``, where ``maxFR3CDR3Score`` is the      |
|                                    |               | maximal alignment score for ``FR3+VCDR3Part`` region among all of V hits for current  |
|                                    |               | input reads pair.                                                                     | 
+------------------------------------+---------------+---------------------------------------------------------------------------------------+
| | ``readsLayout``                  | ``Opposite``  | Relative orientation of paired reads. Available values: ``Opposite``, ``Collinear``,  |
| | (*only for paired-end*           |               | ``Unknown``.                                                                          |
| | *analysis*)                      |               |                                                                                       |
+------------------------------------+---------------+---------------------------------------------------------------------------------------+

.. raw:: html

   <!--
   | `relativeMinVScore` <br> (_only for paired-end analysis_)| 0.7 | Relative minimum score of V gene. Only those V hits will be considered, which score is greater then `relativeMinVScore * maxVScore`, where `maxVScore` is the maximum score throw all obtained V hits. |-->

One can override these parameters in the following way:

::

    mixcr align -OmaxHits=3 input_file1 [input_file2] output_file.vdjca

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

    mixcr align -OvParameters.parameters.minAlignmentLength=30 \
                -OjParameters.parameters.relativeMinScore=0.7 \ 
                input_file1 [input_file2] output_file.vdjca

Scoring used in aligners is specified by ``scoring`` subgroup of
parameters. It contains the following parameters:

+------------------+----------------------------------------+-----------------------------------------------------------------------------+
| Parameter        | Default value                          | Description                                                                 |
+==================+========================================+=============================================================================+
| ``subsMatrix``   | ``simple(match = 5, mismatch = -9)``   | Substitution matrix. Available types:                                       |
|                  |                                        |  - ``simple`` --- a matrix with diagonal elements equal to ``match`` and    |
|                  |                                        |    other elements equal to ``mismatch``                                     |
|                  |                                        |  - ``raw`` --- a complete set of 16 matrix elements should be specified;    | 
|                  |                                        |    for  example:                                                            |
|                  |                                        |   ``raw(5,-9,-9,-9,-9,5,-9,-9,-9,-9,5,-9,-9,-9,-9,5)``                      |
|                  |                                        |   (*equivalent to the  default value*)                                      |
+------------------+----------------------------------------+-----------------------------------------------------------------------------+
| ``gapPenalty``   | ``-12``                                | Penalty for gap.                                                            |
+------------------+----------------------------------------+-----------------------------------------------------------------------------+

Scoring parameters can be overridden in the following way:

::

    mixcr align -OvParameters.parameters.scoring.gapPenalty=-20 input_file1 [input_file2] output_file.vdjca

::

    mixcr align -OvParameters.parameters.scoring.subsMatrix=simple(match=4,mismatch=-11) \
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

    mixcr align -OdParameters.absoluteMinScore=10 input_file1 [input_file2] output_file.vdjca

Scoring parameters for D aligner are the following:

   |

+-------------------------+----------------------------------------+--------------------------------------------------------------------+
| Parameter               | Default value                          | Description                                                        |
+=========================+========================================+====================================================================+
| ``type``                | ``affine``                             | Type of scoring. Possible values: ``affine``, ``linear``.          |
+-------------------------+----------------------------------------+--------------------------------------------------------------------+
| ``subMatrix``           | ``simple(match = 5, mismatch = -9)``   | Substitution matrix. Available types:                              |
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

    mixcr align -OdParameters.scoring.gapExtensionPenalty=-5 input_file1 [input_file2] output_file.vdjca
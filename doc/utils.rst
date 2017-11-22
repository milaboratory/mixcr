.. |br| raw:: html

   <br />

.. _ref-utils:

Utility actions
===============


Version info
------------

In order to check the current version of MiXCR as usual one can use ``-v`` option:

::

    > mixcr -v
    MiXCR v2.1 (built Mon Feb 06 19:56:13 MSK 2017; rev=a9958cd; branch=release/v2.1)
    RepSeq.IO v1.2.6 (rev=958e019)
    MiLib v1.7.1 (rev=f6ccdbc)
    Built-in V/D/J/C library: repseqio.v1.2

    Library search path:
    - built-in libraries
    - /Users/dbolotin/.
    - /Users/dbolotin/.mixcr/libraries


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


Filter alignments
-----------------

Allows to filter alignments in ``.vdjca`` file. Example:

::

    > mixcr filterAlignments --chains TRA,TRB input_file.vdjca output_file.vdjca

The available options are:

+--------------------------------+-------------------------------------------------------------------------------------------------+
| Option                         | Description                                                                                     |
+================================+=================================================================================================+
| ``-e``, ``--cdr3-equals``      | Include only those alignments which CDR3 equals to a specified nucleotide sequence              |
+--------------------------------+-------------------------------------------------------------------------------------------------+
| ``c``, ``--chains``            | Include only alignments with specified immunological protein chains (comma separated list       |
|                                | of some of IGH, IGL, IGK, TRA, TRB, TRG, TRD chains)                                            |
+--------------------------------+-------------------------------------------------------------------------------------------------+
| ``-x``, ``--chimeras-only``    | Output only chimeric alignments                                                                 |
+--------------------------------+-------------------------------------------------------------------------------------------------+
| ``-g``, ``--contains-feature`` | Include only those alignments that contain specified gene feature (see :ref:`ref-geneFeatures`) |
+--------------------------------+-------------------------------------------------------------------------------------------------+
| ``-i``, ``--read-ids``         | Output alignments with specified IDs only                                                       |
+--------------------------------+-------------------------------------------------------------------------------------------------+
| ``-n``, ``--limit``            | Maximal number of alignments to process                                                         |
+--------------------------------+-------------------------------------------------------------------------------------------------+


.. |br| raw:: html

   <br />

.. _ref-utils:

Utility actions
===============


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




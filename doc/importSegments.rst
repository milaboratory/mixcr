.. _ref-importSegments:

.. |br| raw:: html

   <br />

Using external libraries for alignment
================================

.. tip::

  MiXCR utilases libraries in .json format (see https://github.com/repseqio for details). 
  
  NOTICE. In some cases when using an external library mixcr will try to establish connection with NCBI over the internet.

.. _ref-auto-imgt:

IMGT library
-------------------------------------------------
Compiled IMGT library file for MiXCR can be downloaded at https://github.com/repseqio/library-imgt/releases. In order to use the library put the .json library file to ~/.mixcr/libraries or to the directory from where mixcr is started.

.. code-block:: console

  > mixcr align -b imgt input_R1.fastq input_R2.fastq alignments.vdjca

  ... Building alignments

``-b`` option specifies the library to use for alignment. If the short name is given (ex.``-b imgt``) mixcr will look for the latest version in the folder. Otherwise, to use one of the old versions give the full name including the version number (ex. ``-b imgt.201631-4`` ) 

.. code-block:: console

  > mixcr assemble alignments.vdjca clones.clns

  ... Assembling clones

  > mixcr exportClones --chains IGH clones.clns clones.txt

  ... Exporting clones to tab-delimited file

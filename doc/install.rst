Installation
===============

System requirements
-----------------------
  
- Any Java-enabled platform (Windows, Linux, Mac OS X)
- Java version 7 or higher (download from `Oracle web
  site <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`__)
- 1--16 Gb RAM (depending on number of clones in the sample)

Installation on Mac OS X / Linux
------------------------------------

- Check that you have Java 1.7+ installed on your system by typing
  ``java -version``. Here is the example output of this command:

  .. code-block:: console

    > java -version
    java version "1.7.0_65"
    Java(TM) SE Runtime Environment (build 1.7.0_65-b17)
    Java HotSpot(TM) 64-Bit Server VM (build 24.65-b04, mixed mode)

- unzip the archive with MiXCR
- add ``bin`` folder of MiXCR distribution to your ``PATH`` variable or
  add symbolic link to ``mixcr`` script to the user folder with
  executables (e.g. ``~/bin/`` in Ubuntu and many other popular linux
  distributions)

Installation on Windows
---------------------------

Currently there is no execution script or installer for Windows. Still
MiXCR can easily be used by direct execution from the jar file.

-  check that you have Java 1.7+ installed on your system by typing
   ``java -version``. Here is the example output of this command:

``java version "1.7.0_65"    Java(TM) SE Runtime Environment (build 1.7.0_65-b17)    Java HotSpot(TM) 64-Bit Server VM (build 24.65-b04, mixed mode)``

-  unzip the archive with MiXCR
-  use ``mixcr.jar`` from the ``jar`` folder in the following way:

``java -Xmx4g -Xms3g -jar path_to_mixcr\jar\mixcr.jar ...``

For example:

``java -Xmx4g -Xms3g -jar C:\path_to_mixcr\jar\mixcr.jar align input.fastq.gz output.vdjсa``

To use mixcr from ``jar`` file one need to substitute ``mixcr`` command
with ``java -Xmx4g -Xms3g -jar path_to_mixcr\jar\mixcr.jar`` in all
examples from this manual.

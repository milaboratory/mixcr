Installation
===============

System requirements
-----------------------
  
- Any Java-enabled platform (Windows, Linux, Mac OS X)
- Java version 8 or higher (download from `Oracle web site <http://www.oracle.com/technetwork/java/javase/downloads/index.html>`_)
- 1--16 Gb RAM (depending on number of clones in the sample)

Installation on Mac OS X / Linux using Homebrew
-----------------------------------------------

`Homebrew <http://brew.sh/>`_ is a simple package manager developed for Mac OS X and also `ported <https://github.com/Homebrew/linuxbrew>`_ to Linux.
To install MiXCR using Homebrew just type the following commands:

::

    brew tap milaboratory/all
    brew install mixcr

Installation on Mac OS X / Linux / FreeBSD from zip distribution
----------------------------------------------------------------

- Check that you have Java **1.8+** installed on your system by typing ``java -version``. Here is the example output of this command:

  .. code-block:: console

    > java -version
    java version "1.8.0_66"
    Java(TM) SE Runtime Environment (build 1.8.0_66-b17)
    Java HotSpot(TM) 64-Bit Server VM (build 25.66-b17, mixed mode)

- download latest binary distributaion of MiXCR from the `release page <https://github.com/milaboratory/mixcr/releases>`_ on GitHub
- unzip the archive
- add extracted folder of MiXCR distribution to your ``PATH`` variable or add symbolic link for ``mixcr`` script to your ``bin/`` folder (e.g. ``~/bin/`` in Ubuntu and many other popular linux distributions)

Installation on Windows
-----------------------

Currently there is no execution script or installer for Windows. Still MiXCR can easily be used by direct execution from the jar file.

- check that you have Java **1.7+** installed on your system by typing ``java -version``
- download latest binary distributaion of MiXCR from the `release page <https://github.com/milaboratory/mixcr/releases>`_ on GitHub
- unzip the archive
- use ``mixcr.jar`` from the archive in the following way:

  .. code-block:: powershell

    > java -Xmx4g -Xms3g -jar path_to_mixcr\jar\mixcr.jar ...

For example:

  .. code-block:: powershell

    > java -Xmx4g -Xms3g -jar C:\path_to_mixcr\jar\mixcr.jar align input.fastq.gz output.vdjca

To use mixcr from ``jar`` file one need to substitute ``mixcr`` command
with ``java -Xmx4g -Xms3g -jar path_to_mixcr\jar\mixcr.jar`` in all
examples from this manual.

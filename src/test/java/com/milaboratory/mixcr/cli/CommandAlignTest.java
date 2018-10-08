package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.cli.Main;
import org.junit.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 *
 */
public class CommandAlignTest {
    static PrintStream oldErr, oldOut;
    static ByteArrayOutputStream err, out;

    {
        oldErr = System.err;
        oldOut = System.out;
        System.setErr(new PrintStream(err = new ByteArrayOutputStream()));
        System.setOut(new PrintStream(out = new ByteArrayOutputStream()));
    }

    static String getErrLines() {
        return new String(err.toByteArray());
    }

    static void resetErr() {
        System.setErr(oldErr);
    }

    static void resetOut() {
        System.setOut(oldOut);
    }

    static void reset() {
        resetErr();
        resetOut();
    }

    @Test
    public void test1() {
        String[] args = {"align", "in", "out"};

        CommandLine cmd = Main.parse(args);
        reset();


        System.out.println(getErrLines());
    }

    //    @Test
//    public void test1() {
//        CommandLine.run(new Main(), "align", "-Ohui=pizda", "hui", "suka");
////        CommandLine.run(new PicoMain(), "help", "align");
//    }
//
//
//    /**
//     *
//     */
//    @CommandLine.Command(name = "align",
//            sortOptions = false,
//            customSynopsis = "My custom synopsis",
//            mixinStandardHelpOptions = true,
////        headerHeading = "Usage:%n%n",
////        synopsisHeading = "%n",
//            descriptionHeading = "%nDescription:%n%n",
////        parameterListHeading = "%nParameters:%n",
//            optionListHeading = "%nOptions:%n",
////        header = "Align raw sequencing data",
//            description = "Builds alignments with V,D,J and C genes for input sequencing reads.")
//    public class CommandCC implements Runnable {
//        @CommandLine.Parameters(
//                hidden = true,
//                arity = "2..3",
//                hideParamSyntax = true,
//                description = "File(s) to process.")
//        private String[] inOut;
//
//
//        @Override
//        public void run() {
//            System.out.println(Arrays.toString(inOut));
//        }
//    }

}
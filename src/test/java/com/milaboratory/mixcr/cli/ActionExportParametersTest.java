package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.export.Field;
import com.milaboratory.mixcr.export.FieldExtractors;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Created by poslavsky on 28/09/15.
 */
public class ActionExportParametersTest {
    @Test
    public void testName() throws Exception {
        Field[] fields = FieldExtractors.getFields();
        for (Field field : fields)
            System.out.println(field.getCommand() + "   " + field.getDescription() + "   " + field);
    }

    @Test
    public void test1() throws Exception {
        ArrayList<String>[] description = FieldExtractors.getDescription(Clone.class);
        System.out.println("Available export fields:\n" + Util.printTwoColumns(
                description[0], description[1], 21, 50, 5, "\n"));
    }
}
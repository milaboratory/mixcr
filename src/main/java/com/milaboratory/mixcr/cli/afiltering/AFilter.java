/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli.afiltering;

import cc.redberry.primitives.Filter;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

/**
 * Created by dbolotin on 05/09/14.
 */
public class AFilter implements Filter<VDJCAlignments> {
    final ScriptEngine engine;
    final Invocable invocable;

    public AFilter(ScriptEngine engine, Invocable invocable) {
        this.engine = engine;
        this.invocable = invocable;
    }

    @Override
    public boolean accept(VDJCAlignments alignments) {
        engine.put("a", alignments);
        try {
            Object value = invocable.invokeFunction("evaluate_filter");
            return (Boolean) value;
        } catch (ScriptException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static AFilter build(String filterCode) {
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            Reader reader = null;
            String script = null;
            try {
                reader = new BufferedReader(new InputStreamReader(
                        AFilter.class.getClassLoader().getResourceAsStream("js/filter_init.js")));

                char[] buffer = new char[1024];
                int size;
                StringBuilder sb = new StringBuilder();
                while ((size = reader.read(buffer)) >= 0)
                    sb.append(buffer, 0, size);
                script = sb.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    if (reader != null)
                        reader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            int filterBegin = filterCode.lastIndexOf(';') + 1;
            script = script.replace("/*CODE*/", filterCode.substring(0, filterBegin));
            script = script.replace("/*FILTER*/", filterCode.substring(filterBegin));
            engine.eval(script);

            for (Map.Entry<GeneFeature, String> entry : GeneFeature.getNameByFeature().entrySet())
                engine.put(entry.getValue(), entry.getKey());

            for (GeneType geneType : GeneType.values())
                engine.put(new String(new char[]{geneType.getLetter()}), geneType);

            Invocable inv = (Invocable) engine;
            return new AFilter(engine, inv);
        } catch (ScriptException e) {
            throw new IllegalArgumentException(e);
        }
    }
}

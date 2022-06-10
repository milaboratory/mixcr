/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
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

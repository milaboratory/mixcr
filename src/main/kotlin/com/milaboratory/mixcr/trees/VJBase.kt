package com.milaboratory.mixcr.trees;

import io.repseq.core.GeneType;

import java.util.Objects;

public class VJBase {
    public final String VGeneName;
    public final String JGeneName;
    final Integer CDR3length;

    public VJBase(String VGeneName, String JGeneName, Integer CDR3length) {
        this.VGeneName = VGeneName;
        this.JGeneName = JGeneName;
        this.CDR3length = CDR3length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VJBase vjBase = (VJBase) o;
        return VGeneName.equals(vjBase.VGeneName) && JGeneName.equals(vjBase.JGeneName) && Objects.equals(CDR3length, vjBase.CDR3length);
    }

    @Override
    public int hashCode() {
        return Objects.hash(VGeneName, JGeneName, CDR3length);
    }

    @Override
    public String toString() {
        return "VJBase{" +
                "VGeneName='" + VGeneName + '\'' +
                ", JGeneName='" + JGeneName + '\'' +
                ", cdr3length=" + CDR3length +
                '}';
    }

    public String getGeneName(GeneType geneType) {
        switch (geneType) {
            case Variable:
                return VGeneName;
            case Joining:
                return JGeneName;
            default:
                throw new IllegalArgumentException();
        }
    }
}

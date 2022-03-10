package com.milaboratory.mixcr.trees;

import io.repseq.core.GeneType;

import java.util.Objects;

class VJBase {
    final String VGeneName;
    final String JGeneName;

    public VJBase(String VGeneName, String JGeneName) {
        this.VGeneName = VGeneName;
        this.JGeneName = JGeneName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VJBase vjBase = (VJBase) o;
        return VGeneName.equals(vjBase.VGeneName) && JGeneName.equals(vjBase.JGeneName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(VGeneName, JGeneName);
    }

    @Override
    public String toString() {
        return "VJBase{" +
                "VGeneName='" + VGeneName + '\'' +
                ", JGeneName='" + JGeneName + '\'' +
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

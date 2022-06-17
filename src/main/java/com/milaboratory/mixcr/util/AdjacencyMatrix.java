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
package com.milaboratory.mixcr.util;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.List;

public class AdjacencyMatrix {
    public final BitArrayInt[] data;
    public final int size;

    public AdjacencyMatrix(int size) {
        this.size = size;
        this.data = new BitArrayInt[size];
        for (int i = 0; i < size; i++)
            this.data[i] = new BitArrayInt(size);
    }

    public void setConnected(int i, int j) {
        setConnected(i, j, true);
    }

    public boolean isConnected(int i, int j) {
        return this.data[i].get(j);
    }

    public void setConnected(int i, int j, boolean connected) {
        if (connected) {
            this.data[i].set(j);
            this.data[j].set(i);
        } else {
            this.data[i].clear(j);
            this.data[j].clear(i);
        }
    }

    public void clear(int i) {
        for (int j = 0; j < size; ++j)
            setConnected(i, j, false);
    }

    public List<BitArrayInt> calculateMaximalCliques() {
        return calculateMaximalCliques(0);
    }

    /**
     * Extract maximal cliques using Bron–Kerbosch algorithm.
     *
     * @return list of maximal cliques
     */
    public List<BitArrayInt> calculateMaximalCliques(int maxCliques) {
        List<BitArrayInt> cliques = new ArrayList<>();
        BStack stack = new BStack();
        stack.currentP().setAll();
        stack.currentPi().setAll();
        BitArrayInt tmp = new BitArrayInt(size);

        while (true) {
            int v = stack.nextVertex();

            if (v == -1)
                if (stack.pop())
                    continue;
                else
                    break;

            stack.currentP().clear(v);

            stack.loadAndGetNextR().set(v);
            stack.loadAndGetNextP().and(data[v]);
            stack.loadAndGetNextX().and(data[v]);

            stack.currentX().set(v);

            if (stack.nextP().isEmpty() && stack.nextX().isEmpty()) {
                cliques.add(stack.nextR().clone());
                if (maxCliques != 0 && cliques.size() >= maxCliques)
                    return cliques;
                continue;
            }

            tmp.loadValueFrom(stack.nextP());
            tmp.or(stack.nextX());
            int u = 0, bestU = -1, count = -1;
            while ((u = tmp.nextBit(u)) != -1) {
                int c = tmp.numberOfCommonBits(data[u]);
                if (bestU == -1 || c > count) {
                    bestU = u;
                    count = c;
                }
                ++u;
            }

            stack.nextPi().loadValueFrom(data[bestU]);
            stack.nextPi().clear(bestU);
            stack.nextPi().xor(stack.nextP());
            stack.nextPi().and(stack.nextP());

            stack.push();
        }
        return cliques;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < size; i++)
            builder.append(data[i]).append('\n');
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    final class BStack {
        final BList R = new BList();
        final BList X = new BList();
        final BList P = new BList();
        /**
         * P for vertices enumeration = P / N(v)
         */
        final BList Pi = new BList();
        final TIntArrayList lastVertex = new TIntArrayList();
        int currentLevel = 0;

        public BStack() {
            lastVertex.add(0);
        }

        BitArrayInt currentR() {
            return R.get(currentLevel);
        }

        BitArrayInt currentX() {
            return X.get(currentLevel);
        }

        BitArrayInt currentP() {
            return P.get(currentLevel);
        }

        BitArrayInt currentPi() {
            return Pi.get(currentLevel);
        }

        int nextVertex() {
            int nextVertex = currentPi().nextBit(lastVertex.get(lastVertex.size() - 1));
            lastVertex.set(currentLevel, nextVertex + 1);
            return nextVertex;
        }

        BitArrayInt loadAndGetNextR() {
            R.get(currentLevel + 1).loadValueFrom(R.get(currentLevel));
            return R.get(currentLevel + 1);
        }

        BitArrayInt loadAndGetNextX() {
            X.get(currentLevel + 1).loadValueFrom(X.get(currentLevel));
            return X.get(currentLevel + 1);
        }

        BitArrayInt loadAndGetNextP() {
            P.get(currentLevel + 1).loadValueFrom(P.get(currentLevel));
            return P.get(currentLevel + 1);
        }

        BitArrayInt nextX() {
            return X.get(currentLevel + 1);
        }

        BitArrayInt nextP() {
            return P.get(currentLevel + 1);
        }

        BitArrayInt nextR() {
            return R.get(currentLevel + 1);
        }

        BitArrayInt nextPi() {
            return Pi.get(currentLevel + 1);
        }

        void push() {
            currentLevel++;
            lastVertex.add(0);
        }

        boolean pop() {
            currentLevel--;
            lastVertex.removeAt(lastVertex.size() - 1);
            return currentLevel != -1;
        }
    }

    final class BList {
        final List<BitArrayInt> list = new ArrayList<>();

        public BitArrayInt get(int i) {
            if (i >= list.size())
                for (int j = list.size(); j <= i; j++)
                    list.add(new BitArrayInt(size));
            return list.get(i);
        }
    }
}

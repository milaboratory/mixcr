package com.milaboratory.mixcr.postanalysis.clustering;

import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleBiFunction;

public final class HierarchicalClustering {
    private HierarchicalClustering() {}

    public static double EuclideanDistance(double[] vectori, double[] vectorj) {
        double diff_square_sum = 0.0;
        for (int i = 0; i < vectori.length; i++) {
            diff_square_sum += (vectori[i] - vectorj[i]) * (vectori[i] - vectorj[i]);
        }
        return diff_square_sum;
    }

    public static double ManhattenDistance(double[] vectori, double[] vectorj) {
        double abs_sum = 0.0;
        for (int i = 0; i < vectori.length; i++) {
            abs_sum += (java.lang.Math.abs(vectori[i]) - java.lang.Math.abs(vectorj[i]));
        }
        return abs_sum;
    }

    public static double ChebishevDistance(double[] vectori, double[] vectorj) {
        double max_distance = 0.0;
        for (int i = 0; i < vectori.length; i++) {
            double distance = (java.lang.Math.abs(vectori[i]) - java.lang.Math.abs(vectorj[i]));
            if (distance >= max_distance) {
                max_distance = distance;
            }
        }
        return max_distance;
    }

    private static class PairDistance implements Comparable<PairDistance> {
        final int id1, id2;
        final double distance;

        PairDistance(int id1, int id2, double distance) {
            this.id1 = id1;
            this.id2 = id2;
            this.distance = distance;
        }

        @Override
        public int compareTo(HierarchicalClustering.PairDistance o) {
            return Double.compare(distance, o.distance);
        }
    }

    public static <T> List<HierarchyNode> clusterize(T[] vectors, double distanceOffset, ToDoubleBiFunction<T, T> distanceFunc) {
        if (vectors.length == 0)
            return Collections.emptyList();
        if (vectors.length == 1)
            return Collections.singletonList(new HierarchyNode(0, new int[0], 0));

        List<HierarchyNode> result = new ArrayList<>();
        List<PairDistance> distances = new ArrayList<>();
        TIntObjectHashMap<int[]> clusters = new TIntObjectHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Integer.MIN_VALUE);
        double[][] rawDist = new double[vectors.length][vectors.length];

        if (distanceOffset > 1) {
            throw new IllegalArgumentException("Offset must be less then 1");
        }

        for (int i = 0; i < vectors.length; i++) {
            clusters.put(i, new int[]{i});
            for (int j = i + 1; j < vectors.length; j++) {
                PairDistance distance = new PairDistance(i, j, distanceFunc.applyAsDouble(vectors[i], vectors[j]));
                distances.add(distance);
                rawDist[i][j] = distance.distance;
                rawDist[j][i] = distance.distance;
            }
        }

        Collections.sort(distances);

        for (int id = -1; ; id--) {

            TIntArrayList children = new TIntArrayList(Constants.DEFAULT_CAPACITY, Integer.MIN_VALUE);
            TIntHashSet childrenNeighbors = new TIntHashSet();

            double currentNodeDistance = distances.get(0).distance;
            double distanceSum = distances.get(0).distance;

            children.add(distances.get(0).id1);
            children.add(distances.get(0).id2);


            for (int i = 1; i < distances.size(); i++) {
                if (distances.get(i).distance <= currentNodeDistance * (1 + distanceOffset)) {
                    for (int j = 0; j < children.size(); j++) {
                        if (distances.get(i).id1 == children.get(j)) {
                            childrenNeighbors.add(distances.get(i).id2);
                            distanceSum += distances.get(i).distance;
                        } else if (distances.get(i).id2 == children.get(j)) {
                            childrenNeighbors.add(distances.get(i).id1);
                            distanceSum += distances.get(i).distance;
                        }
                    }
                }
            }
            children.addAll(childrenNeighbors);

            HierarchyNode node = new HierarchyNode(id, children.toArray(), distanceSum / (children.size() - 1));
            result.add(node);

            if (distances.size() == 1) {
                return result;
            }

            TIntArrayList allChildrenList = new TIntArrayList();

            for (int i = 0; i < children.size(); i++) {
                int[] c = clusters.remove(children.get(i));
                allChildrenList.addAll(c);
            }

            int[] allChildren = allChildrenList.toArray();


            for (int i = distances.size() - 1; i >= 0; i--) {
                for (int j = 0; j < children.size(); ++j) {
                    int child = children.get(j);
                    if (distances.get(i).id1 == child || distances.get(i).id2 == child) {
                        distances.remove(i);
                        break;
                    }
                }
            }

            TIntObjectIterator<int[]> it = clusters.iterator();
            while (it.hasNext()) {
                it.advance();
                int id1 = id;
                int id2 = it.key();
                double minDistance = Double.MAX_VALUE;

                int[] children1 = allChildren;
                int[] children2 = it.value();
                for (int i : children1) {
                    for (int j : children2) {
                        if (rawDist[i][j] < minDistance) {
                            minDistance = rawDist[i][j];
                        }
                    }
                }
                distances.add(new PairDistance(id1, id2, minDistance));

            }
            if (distances.isEmpty())
                return result;
            clusters.put(id, allChildren);

            Collections.sort(distances);
        }
    }
}

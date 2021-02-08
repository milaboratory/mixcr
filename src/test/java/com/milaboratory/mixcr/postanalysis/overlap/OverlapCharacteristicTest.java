package com.milaboratory.mixcr.postanalysis.overlap;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.SimpleProcessorWrapper;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.TestDataset;
import com.milaboratory.mixcr.postanalysis.TestObjectWithPayload;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.OutputTable;
import com.milaboratory.mixcr.postanalysis.ui.OverlapSummary;
import com.milaboratory.util.sorting.MergeStrategy;
import com.milaboratory.util.sorting.SortingProperty;
import com.milaboratory.util.sorting.SortingPropertyRelation;
import com.milaboratory.util.sorting.SortingUtil;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well512a;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 */
public class OverlapCharacteristicTest {
    @Test
    @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
    public void testOverlapPort1() {
        List<ElementOrdering> initialSort = Arrays.asList(
                new ElementOrdering(0, 2),
                new ElementOrdering(2, 5),
                new ElementOrdering(5, 8)
        );

        List<ElementOrdering> targetGroupping = Arrays.asList(
                new ElementOrdering(0, 4),
                new ElementOrdering(4, 5)
        );

        MergeStrategy<Element> strategy = MergeStrategy.calculateStrategy(initialSort, targetGroupping);

        RandomDataGenerator rng = new RandomDataGenerator(new Well512a());
        OverlapData ovp = new OverlapData(IntStream.range(0, 10)
                .mapToObj(__ -> rndDataset(rng, rng.nextInt(11111, 22222), 10))
                .toArray(TestDataset[]::new),
                targetGroupping);

        OutputPortCloseable<List<List<Element>>> join = strategy
                .join(Arrays.stream(ovp.datasets)
                        .map(TestDataset::mkElementsPort)
                        .collect(Collectors.toList()));

        DescriptiveStatistics ds = new DescriptiveStatistics();
        for (List<List<Element>> row : CUtils.it(join)) {
            Payload payload = null;
            int nOverlapped = 0;
            for (int index = 0; index < row.size(); index++) {
                List<Element> cell = row.get(index);
                if (!cell.isEmpty())
                    ++nOverlapped;
                for (Element element : cell) {
                    List<Element> expected = ovp.index
                            .getOrDefault(element.payload, Collections.emptyMap())
                            .getOrDefault(index, Collections.emptyList());

                    Assert.assertTrue(expected.contains(element));

                    if (payload == null)
                        payload = element.payload;
                    else
                        Assert.assertEquals(0, ovp.pComparator.compare(payload, element.payload));
                }
            }
            if (nOverlapped > 1)
                ds.addValue(nOverlapped);
            else
                ds.addValue(0);
        }
        System.out.println(ds);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOverlapCharacteristic1() {
        List<ElementOrdering> initialSort = Arrays.asList(
                new ElementOrdering(0, 2),
                new ElementOrdering(2, 5),
                new ElementOrdering(5, 8)
        );

        List<ElementOrdering> targetGroupping = Arrays.asList(
                new ElementOrdering(0, 4),
                new ElementOrdering(4, 5)
        );

        MergeStrategy<Element> strategy = MergeStrategy.calculateStrategy(initialSort, targetGroupping);

        RandomDataGenerator rng = new RandomDataGenerator(new Well512a());
        int nDatasets = 10;
        OverlapData ovp = new OverlapData(IntStream.range(0, nDatasets)
                .mapToObj(__ -> rndDataset(rng, rng.nextInt(11111, 22222), 10))
                .toArray(TestDataset[]::new),
                targetGroupping);

//        OutputPortCloseable<List<List<Element>>> join = strategy
//                .join(Arrays.stream(ovp.datasets)
//                        .map(OverlapCharacteristicTest::asOutputPort)
//                        .collect(Collectors.toList()));

        List<OverlapCharacteristic<Element>> chars = new ArrayList<>();
        for (int i = 0; i < ovp.datasets.length; i++) {
            for (int j = i + 1; j < ovp.datasets.length; j++) {
                chars.add(new OverlapCharacteristic<>("overlap_" + i + "_" + j,
                        e -> e.weight, new NoPreprocessing<>(), i, j));
            }
        }

        CharacteristicGroup<OverlapKey<OverlapType>, OverlapGroup<Element>> chGroup =
                new CharacteristicGroup<>("overlap", chars, Arrays.asList(new OverlapSummary<>()));

        PostanalysisRunner<OverlapGroup<Element>> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(chGroup);
        List<String> datasetIds = Arrays.stream(ovp.datasets).map(d -> d.id).collect(Collectors.toList());

        PostanalysisResult paResult = runner.run(new OverlapDataset<Element>(datasetIds) {
            @Override
            public OutputPortCloseable<OverlapGroup<Element>> mkElementsPort() {
                return new SimpleProcessorWrapper<>(strategy
                        .join(Arrays.stream(ovp.datasets)
                                .map(TestDataset::mkElementsPort)
                                .collect(Collectors.toList())), OverlapGroup::new);
            }
        });

        CharacteristicGroupResult<OverlapKey<OverlapType>> chGroupResult = paResult.getTable(chGroup);
        Map<Object, OutputTable> outputs = chGroupResult.getOutputs();

        double[][] expectedSharedElements = new double[nDatasets][nDatasets];
        double[][] expectedD = new double[nDatasets][nDatasets];
        double[][]
                sumF1 = new double[nDatasets][nDatasets],
                sumF2 = new double[nDatasets][nDatasets],
                expectedF1 = new double[nDatasets][nDatasets];
        double[][] expectedF2 = new double[nDatasets][nDatasets];
        double[][]
                meanF1 = new double[nDatasets][nDatasets],
                meanF2 = new double[nDatasets][nDatasets],
                sumDeltaSqF1 = new double[nDatasets][nDatasets],
                sumDeltaSqF2 = new double[nDatasets][nDatasets],
                expectedR = new double[nDatasets][nDatasets];

        ovp.scanOverlap((i1, i2, w1, w2, f1, f2) -> {
            ++expectedSharedElements[i1][i2];
            ++expectedD[i1][i2];
            sumF1[i1][i2] += f1;
            sumF2[i1][i2] += f2;
            expectedF2[i1][i2] += Math.sqrt(f1 * f2);
        });

        for (int i1 = 0; i1 < nDatasets; i1++) {
            for (int i2 = 0; i2 < nDatasets; i2++) {
                expectedD[i1][i2] /= ovp.diversity.getOrDefault(i1, 1);
                expectedD[i1][i2] /= ovp.diversity.getOrDefault(i2, 1);
                expectedF1[i1][i2] = Math.sqrt(sumF1[i1][i2] * sumF2[i1][i2]);
                meanF1[i1][i2] = sumF1[i1][i2] / expectedSharedElements[i1][i2];
                meanF2[i1][i2] = sumF2[i1][i2] / expectedSharedElements[i1][i2];
            }
        }

        ovp.scanOverlap((i1, i2, w1, w2, f1, f2) -> {
            expectedR[i1][i2] += (f1 - meanF1[i1][i2]) * (f2 - meanF2[i1][i2]);
            sumDeltaSqF1[i1][i2] += Math.pow(f1 - meanF1[i1][i2], 2);
            sumDeltaSqF2[i1][i2] += Math.pow(f2 - meanF2[i1][i2], 2);
        });

        for (int i1 = 0; i1 < nDatasets; i1++) {
            for (int i2 = 0; i2 < nDatasets; i2++) {
                expectedR[i1][i2] /= Math.sqrt(sumDeltaSqF1[i1][i2] * sumDeltaSqF2[i1][i2]);
                if (Double.isNaN(expectedR[i1][i2]))
                    expectedR[i1][i2] = 0.0;
            }
        }

        System.out.println(Arrays.stream(expectedSharedElements).map(Arrays::toString).collect(Collectors.joining("\n")));
        assert2dEquals(expectedSharedElements, outputs.get(OverlapType.SharedClonotypes)
                .reorder(datasetIds, datasetIds)
                .rows(0, 0));
        assert2dEquals(expectedD, outputs.get(OverlapType.D)
                .reorder(datasetIds, datasetIds)
                .rows(0, 0));
        assert2dEquals(expectedF1, outputs.get(OverlapType.F1)
                .reorder(datasetIds, datasetIds)
                .rows(0, 0));
        assert2dEquals(expectedF2, outputs.get(OverlapType.F2)
                .reorder(datasetIds, datasetIds)
                .rows(0, 0));
        assert2dEquals(expectedR, outputs.get(OverlapType.R_Intersection)
                .reorder(datasetIds, datasetIds)
                .rows(0, 0));
    }

    interface OverlapScanFunction {
        void apply(int i1, int i2, double w1, double w2, double f1, double f2);
    }

    private void assert2dEquals(double[][] expected, double[][] actual) {
        assert2dEquals(expected, actual, 1e-10);
    }

    private void assert2dEquals(double[][] expected, double[][] actual, double delta) {
        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertArrayEquals(expected[i], actual[i], delta);
        }
    }

    private static <T> OutputPortCloseable<T> asOutputPort(List<T> l) {
        OutputPort<T> p = CUtils.asOutputPort(l);
        return new OutputPortCloseable<T>() {
            @Override
            public void close() {}

            @Override
            public T take() {
                return p.take();
            }
        };
    }

    public static final class OverlapData {
        final TestDataset<Element>[] datasets;
        final List<ElementOrdering> ordering;
        final Comparator<Element> comparator;
        final Comparator<Payload> pComparator;
        final TreeMap<Payload, Map<Integer, List<Element>>> index;
        final Map<Integer, Integer> diversity;
        final Map<Integer, Double> sumWeight;

        public OverlapData(TestDataset<Element>[] datasets, List<ElementOrdering> ordering) {
            this.datasets = datasets;
            this.ordering = ordering;
            this.comparator = SortingUtil.combine(ordering);
            this.pComparator = SortingUtil.combine(ordering.stream().map(s -> s.inner).collect(Collectors.toList()));
            this.index = new TreeMap<>(pComparator);
            this.diversity = new HashMap<>();
            this.sumWeight = new HashMap<>();

            for (TestDataset<Element> d : datasets) {
                d.data.sort(comparator);
            }
            for (int index = 0; index < datasets.length; index++) {
                List<Element> dataset = datasets[index].data;
                for (Element element : dataset) {
                    Map<Integer, List<Element>> m = this.index.computeIfAbsent(element.payload, payload -> new HashMap<>());
                    m.computeIfAbsent(index, __ -> new ArrayList<>()).add(element);
                }
            }

            index.forEach((payload, row) -> row.forEach((key, value) -> {
                int index = key;
                int val = diversity.getOrDefault(index, 0);
                diversity.put(index, val + 1);
                double w = sumWeight.getOrDefault(index, 0.0);
                sumWeight.put(index, w + value.stream().mapToDouble(v -> v.weight).sum());
            }));
        }

        void scanOverlap(OverlapScanFunction scanner) {
            index.forEach((payload, row) -> {
                for (Map.Entry<Integer, List<Element>> e1 : row.entrySet()) {
                    int i1 = e1.getKey();
                    double w1 = e1.getValue().stream().mapToDouble(v -> v.weight).sum();
                    double f1 = w1 / sumWeight.getOrDefault(i1, 1.0);
                    for (Map.Entry<Integer, List<Element>> e2 : row.entrySet()) {
                        int i2 = e2.getKey();
                        double w2 = e2.getValue().stream().mapToDouble(v -> v.weight).sum();
                        double f2 = w2 / sumWeight.getOrDefault(i2, 1.0);
                        if (i1 != i2) {
                            scanner.apply(i1, i2, w1, w2, f1, f2);
                        }
                    }
                }
            });
        }
    }

    public static TestDataset<Element> rndDataset(RandomDataGenerator rng, int size, int pSize) {
        Element[] r = new Element[size];
        for (int i = 0; i < size; i++) {
            r[i] = new Element(
                    rng.nextUniform(0, 1),
                    rng.nextUniform(0, 100),
                    rndPayload(rng, pSize));
        }
        return new TestDataset<>(Arrays.asList(r));
    }

    public static Payload rndPayload(RandomDataGenerator rng, int pSize) {
        int[] data = new int[pSize];
        for (int i = 0; i < pSize; i++) {
            data[i] = rng.nextInt(0, pSize);
        }
        return new Payload(data);
    }

    public static final class Payload implements Comparable<Payload> {
        final int[] data;

        public Payload(int[] data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return Arrays.toString(data);
        }

        @Override
        public int compareTo(OverlapCharacteristicTest.Payload o) {
            for (int i = 0; i < data.length; i++) {
                int c;
                if ((c = Integer.compare(data[i], o.data[i])) != 0)
                    return c;
            }
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Payload payload = (Payload) o;
            return Arrays.equals(data, payload.data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }


    private static final class PayloadSortingProperty implements SortingProperty<Payload> {
        public final int from, to;

        public PayloadSortingProperty(int from, int to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public Payload get(Payload payload) {
            return new Payload(Arrays.copyOfRange(payload.data, from, to));
        }

        @Override
        public int compare(Payload o1, Payload o2) {
            return get(o1).compareTo(get(o2));
        }

        @Override
        public SortingPropertyRelation relationTo(SortingProperty<?> other) {
            PayloadSortingProperty oth = (PayloadSortingProperty) other;

            if (from == oth.from && to == oth.to)
                return SortingPropertyRelation.Equal;

            if (oth.from <= this.from && this.to <= oth.to) {
                return SortingPropertyRelation.Necessary;
            }

            if (this.from <= oth.from && oth.to <= this.to) {
                return SortingPropertyRelation.Sufficient;
            }

            return SortingPropertyRelation.None;
        }
    }

    private static final class ElementOrdering implements SortingProperty<Element> {
        final PayloadSortingProperty inner;

        public ElementOrdering(int from, int to) {
            this.inner = new PayloadSortingProperty(from, to);
        }

        @Override
        public Object get(Element o) {
            return inner.get(o.payload);
        }

        @Override
        public int compare(Element o1, Element o2) {
            return inner.compare(o1.payload, o2.payload);
        }

        @Override
        public SortingPropertyRelation relationTo(SortingProperty<?> other) {
            return inner.relationTo(((ElementOrdering) other).inner);
        }
    }

    static final class Element extends TestObjectWithPayload<Payload> {
        public Element(double value, double weight, Payload payload) {
            super(value, weight, payload);
        }
    }
}

package com.milaboratory.mixcr.cli;

import org.junit.Test;
import org.mapdb.*;

import java.io.*;
import java.util.*;

/**
 * Created by poslavsky on 05/10/15.
 */
public class ActionAssembleTest implements Serializable {
    static class obj implements Serializable {
        final int a, b;

        public obj(int a, int b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public String toString() {
            return "a = " + a + " b = " + b;
        }
    }

    static class ser0 implements Serializer<obj> {
        @Override
        public void serialize(DataOutput out, obj value) throws IOException {
            out.writeInt(value.a);
            out.writeInt(value.b);
        }

        @Override
        public obj deserialize(DataInput in, int available) throws IOException {
            int a = in.readInt();
            int b = in.readInt();
            return new obj(a, b);
        }

        @Override
        public int fixedSize() {
            return 8;
        }
    }

    static class ser extends BTreeKeySerializer<obj> implements Serializable {
        final Comparator<obj> comparator;

        public ser(Comparator<obj> comparator) {
            this.comparator = comparator;
        }

        @Override
        public void serialize(DataOutput out, int start, int end, Object[] keys) throws IOException {
            for (int i = start; i < end; ++i) {
                out.writeInt(((obj) keys[i]).a);
                out.writeInt(((obj) keys[i]).b);
            }
        }

        @Override
        public Object[] deserialize(DataInput in, int start, int end, int size) throws IOException {
            Object[] objs = new Object[size];
            for (int i = start; i < end; ++i) {
                int a = in.readInt();
                int b = in.readInt();
                objs[i] = new obj(a, b);
            }
            return objs;
        }

        @Override
        public Comparator<obj> getComparator() {
            return comparator;
        }
    }

    static class compa implements Comparator<obj>, Serializable {
        @Override
        public int compare(obj o1, obj o2) {
            int compare = Integer.compare(o1.a, o2.a);
            return compare == 0 ? Integer.compare(o1.b, o2.b) : compare;
        }

        @Override
        public boolean equals(Object a) {
            return a instanceof compa;
        }
    }

    static class compb implements Comparator<obj>, Serializable {
        @Override
        public int compare(obj o1, obj o2) {
            int compare = Integer.compare(o1.b, o2.b);
            return compare == 0 ? Integer.compare(o1.a, o2.a) : compare;
        }

        @Override
        public boolean equals(Object a) {
            return a instanceof compb;
        }
    }

    @Test
    public void test1() throws Exception {

        File f = File.createTempFile("mapdb", "temp");
        f.delete();

        DB db = DBMaker
                .newFileDB(f)
                .transactionDisable()
                .make();

        compa compa = new compa();
        compb compb = new compb();

        final Random rnd = new Random();
        final int n = 1_000_000;
        class it implements Iterator<obj>, Serializable {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < n;
            }

            @Override
            public obj next() {
                ++i;
                return new obj(rnd.nextInt(10000), rnd.nextInt(10000));
            }

            @Override
            public void remove() {
            }
        }
        Iterator<obj> sorta = Pump.sort(new it(),
                true, 50000,
                Collections.reverseOrder(compa), //reverse  order comparator
                new ser0()
        );
        Iterator<obj> sortb = Pump.sort(new it(),
                true, 50000,
                Collections.reverseOrder(compb), //reverse  order comparator
                new ser0()
        );

//        for (int i = 0; i <= n; ++i) {
//            obj obj = new obj(rnd.nextInt(10000), rnd.nextInt(10000));
//            setA.add(obj);
//            setB.add(obj);
//        }
        NavigableSet<obj> setA = db
                .createTreeSet("setA")
                .pumpSource(sorta)
                .serializer(new ser(compa))
                .comparator(compa)
                .makeOrGet();

        NavigableSet<obj> setB = db
                .createTreeSet("setB")
                .pumpSource(sortb)
                .serializer(new ser(compb))
                .comparator(compb)
                .makeOrGet();

        db.commit();
        db.close();

        System.out.println(" Чтение ");

        db = DBMaker.newFileDB(f)
                .transactionDisable()
                .make();

        setA = db.getTreeSet("setA");
        setB = db.getTreeSet("setB");

//        int i = 0;
//        for (obj obj : setA.subSet(new obj(0, 0), new obj(10, 10))) {
//            Assert.assertEquals(obj.a, i++);
////            Assert.assertEquals(obj.a + obj.b, n);
//        }
//
//        i = 0;
//        for (obj obj : setB.subSet(new obj(0, 0), new obj(10, 10))) {
//            Assert.assertEquals(obj.b, i++);
////            Assert.assertEquals(obj.a + obj.b, n);
//        }

        System.out.println(setA.size());
        System.out.println(setB.size());

        db.close();
        f.delete();
    }
}
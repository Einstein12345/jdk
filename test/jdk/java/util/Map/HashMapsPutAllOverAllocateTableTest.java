/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8281631
 * @summary HashMap copy constructor and putAll can over-allocate table
 * @author  Xeno Amess
 *
 * @run junit/othervm/timeout=1000
 *      --add-opens java.base/java.lang=ALL-UNNAMED
 *      --add-opens java.base/java.util=ALL-UNNAMED
 *      HashMapsPutAllOverAllocateTableTest
 */

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

@RunWith(Parameterized.class)
public class HashMapsPutAllOverAllocateTableTest {

    private static final int TEST_SIZE = 128;
    private static final Integer[] INTEGER_ARRAY = new Integer[TEST_SIZE];

    static {
        for (int i = 0; i < TEST_SIZE; ++i) {
            INTEGER_ARRAY[i] = i;
        }
    }

    private static <T extends Map<Object, Object>> Object[] testCase(
            String name,
            Supplier<T> createNewMap,
            Function<Integer, T> createNewMapWithInt,
            Function<T, T> createNewMapWithMap,
            int mapSize
    ) {
        return new Object[]{
                name + " size " + mapSize,
                createNewMap,
                createNewMapWithInt,
                createNewMapWithMap,
                mapSize
        };
    }

    @Parameterized.Parameters
    public static List<Object[]> testFunctionsList() {
        List<Object[]> testParameters = new ArrayList<>(TEST_SIZE * 3);
        for (int i = 0; i <= TEST_SIZE; ++i) {
            testParameters.add(
                    testCase(
                            HashMap.class.getName(),
                            HashMap::new,
                            HashMap::new,
                            HashMap::new,
                            i
                    )
            );
            testParameters.add(
                    testCase(
                            LinkedHashMap.class.getName(),
                            LinkedHashMap::new,
                            LinkedHashMap::new,
                            LinkedHashMap::new,
                            i
                    )
            );
            testParameters.add(
                    testCase(
                            WeakHashMap.class.getName(),
                            WeakHashMap::new,
                            WeakHashMap::new,
                            WeakHashMap::new,
                            i
                    )
            );
        }
        return testParameters;
    }

    private final String testName;

    private final Supplier<Map<Object, Object>> createNewMap;

    private final Function<Integer, Map<Object, Object>> createNewMapWithInt;

    private final Function<Map<Object, Object>, Map<Object, Object>> createNewMapWithMap;

    private final int mapSize;

    public HashMapsPutAllOverAllocateTableTest(
            String testName,
            Supplier<Map<Object, Object>> createNewMap,
            Function<Integer, Map<Object, Object>> createNewMapWithInt,
            Function<Map<Object, Object>, Map<Object, Object>> createNewMapWithMap,
            int mapSize
    ) {
        this.testName = testName;
        this.createNewMap = createNewMap;
        this.createNewMapWithInt = createNewMapWithInt;
        this.createNewMapWithMap = createNewMapWithMap;
        this.mapSize = mapSize;
    }

    public static void fillN(int mapSize, Map<Object, Object> map) {
        for (int i = 0; i < mapSize; i++) {
            map.put(INTEGER_ARRAY[i], INTEGER_ARRAY[i]);
        }
    }

    public static int getArrayLength(Map<?, ?> map) throws
            IllegalAccessException {
        Field field = null;
        Class<?> mapClass = map.getClass();
        while (!Map.class.equals(mapClass)) {
            try {
                field = mapClass.getDeclaredField("table");
                break;
            } catch (NoSuchFieldException ignored) {
            }
            mapClass = mapClass.getSuperclass();
        }
        Objects.requireNonNull(field);
        field.setAccessible(true);
        Object table = field.get(map);
        if (table == null) {
            return -1;
        }
        return Array.getLength(table);
    }

    @Test
    public void test() throws IllegalAccessException {

        Map<Object, Object> a = createNewMap.get();
        fillN(mapSize, a);
        int lengthA = getArrayLength(a);
        {
            Map<Object, Object> b = createNewMapWithInt.apply(mapSize);
            fillN(mapSize, b);
            int length = getArrayLength(b);
            Assert.assertTrue(
                    testName + " : " + "length b larger than length a!",
                    length <= lengthA
            );
        }

        {
            Map<Object, Object> c = createNewMapWithMap.apply(a);
            int length = getArrayLength(c);
            Assert.assertTrue(
                    testName + " : " + "length c larger than length a!",
                    length <= lengthA
            );
        }

        {
            Map<Object, Object> d = createNewMap.get();
            d.putAll(a);
            int length = getArrayLength(d);
            Assert.assertTrue(
                    testName + " : " + "length d larger than length a!",
                    length <= lengthA
            );
        }

    }

}

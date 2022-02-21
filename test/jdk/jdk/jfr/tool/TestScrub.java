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

package jdk.jfr.tool;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * @test
 * @summary Test jfr scrub
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.tool.TestScrub
 */
public class TestScrub {

    @Name("example.Tiger")
    @Category("Mammal")
    private static class TigerEvent extends Event {
    }

    @Name("example.Zebra")
    @Category("Mammal")

    private static class ZebraEvent extends Event {
    }

    @Name("example.Tigerfish")
    @Category("Fish")
    private static class TigerfishEvent extends Event {
    }

    public static void main(String[] args) throws Throwable {
        Path file = Path.of("recording.jfr");
        try (Recording r = new Recording()) {
            r.start();
            emit(100, "India", TigerEvent.class);
            emit(100, "Namibia", ZebraEvent.class);
            emit(10000, "Lake Tanganyika", TigerfishEvent.class);
            r.stop();
            r.dump(file);
        }

        testEventInclude(file);
        testEventExclude(file);
        testEventMixedIncludeExclude(file);

        testCategoryExclude(file);
        testCategoryInclude(file);

        testThreadExclude(file);
        testThreadInclude(file);
    }

    private static void testEventInclude(Path file) throws Throwable {
        for (var event : scrub(file, "--include-events", "Zebra")) {
            assertEvent(event, "Zebra");
            assertNotEvent(event, "Tiger", "Tigerfish");
        }
        for (var event : scrub(file, "--include-events", "Tiger*")) {
            assertEvent(event, "Tiger", "Tigerfish");
            assertNotEvent(event, "Zebra");
        }
        for (var event : scrub(file, "--include-events", "Tiger,Zebra")) {
            assertEvent(event, "Tiger", "Zebra");
            assertNotEvent(event, "Tigerfish");
        }
        for (var event : scrub(file, "--include-events", "Tiger", "--include-events", "Zebra")) {
            assertEvent(event, "Tiger", "Zebra");
            assertNotEvent(event, "Tigerfish");
        }
    }

    private static void testEventExclude(Path file) throws Throwable {
        for (var event : scrub(file, "--exclude-events", "Zebra")) {
            assertNotEvent(event, "Zebra");
            assertEvent(event, "Tiger", "Tigerfish");
        }
        for (var event : scrub(file, "--exclude-events", "Tiger*")) {
            assertEvent(event, "Zebra");
            assertNotEvent(event, "Tiger", "Tigerfish");
        }
        for (var event : scrub(file, "--exclude-events", "Tiger,Zebra")) {
            assertEvent(event, "Tigerfish");
            assertNotEvent(event, "Tiger", "Zebra");
        }

        for (var event : scrub(file, "--exclude-events", "Tiger", "--exclude-events", "Zebra")) {
            assertEvent(event, "Tigerfish");
            assertNotEvent(event, "Tiger", "Zebra");
        }
    }

    private static void testEventMixedIncludeExclude(Path file) throws Throwable {
        for (var event : scrub(file, "--include-events", "Tiger*", "--exclude-events", "Tigerfish")) {
            assertNotEvent(event, "Zebra", "Tigerfish");
            assertEvent(event, "Tiger");
        }
        for (var event : scrub(file, "--exclude-events", "Tiger*", "--include-events", "Tiger")) {
            assertEvent(event, "Zebra", "Tiger");
            assertNotEvent(event, "Tigerfish");
        }
        for (var event : scrub(file, "--exclude-events", "example.*", "--include-events", "example.*")) {
            assertNotEvent(event, "Tigerfish", "Tiger", "Zebra");
        }
        for (var event : scrub(file, "--include-events", "example.*", "--exclude-events", "example.*")) {
            assertNotEvent(event, "Tigerfish", "Tiger", "Zebra");
        }
    }

    private static void testCategoryInclude(Path file) throws Throwable {
        for (var event : scrub(file, "--include-categories", "Mammal")) {
            assertEvent(event, "Zebra", "Tiger");
            assertNotEvent(event, "Tigerfish");
        }
        for (var event : scrub(file, "--include-categories", "Sahara")) {
            assertNotEvent(event, "Tiger", "Tigerfish", "Zebra");
        }
        for (var event : scrub(file, "--include-categories", "Fish,Mammal")) {
            assertEvent(event, "Tiger", "Zebra", "Tigerfish");
        }
        for (var event : scrub(file, "--include-categories", "Mammal", "--include-categories", "Fish")) {
            assertEvent(event, "Tiger", "Zebra", "Tigerfish");
        }
    }

    private static void testCategoryExclude(Path file) throws Throwable {
        for (var event : scrub(file, "--exclude-categories", "Mammal")) {
            assertNotEvent(event, "Zebra", "Tiger");
            assertEvent(event, "Tigerfish");
        }
        for (var event : scrub(file, "--exclude-categories", "Mammal,Fish")) {
            assertNotEvent(event, "Zebra", "Tiger", "Tigerfish");
        }
        for (var event : scrub(file, "--exclude-categories", "Mammal")) {
            assertNotEvent(event, "Zebra", "Tiger");
            assertEvent(event, "Tigerfish");
        }
        for (var event : scrub(file, "--exclude-categories", "Mammal")) {
            assertNotEvent(event, "Zebra", "Tiger");
            assertEvent(event, "Tigerfish");
        }
    }

    private static void testThreadInclude(Path file) throws Throwable {
        for (var event : scrub(file, "--include-threads", "Namibia")) {
            assertThread(event, "Namibia");
            assertNotThread(event, "India", "Lake Tanganyika");
        }

        for (var event : scrub(file, "--include-threads", "Nam*")) {
            assertThread(event, "Namibia");
            assertNotThread(event, "Lake Tanganyika", "India");
        }

        for (var event : scrub(file, "--include-threads", "Namibia,Lake")) {
            assertThread(event, "Namibia", "Lake Tanganyika");
            assertNotThread(event, "India");
        }

        for (var event : scrub(file, "--include-threads", "India", "--include-threads", "Lake Tanganyika")) {
            assertThread(event, "India", "Lake Tanganyika");
            assertNotThread(event, "Namibia");
        }
    }

    private static void testThreadExclude(Path file) throws Throwable {
        for (var event : scrub(file, "--exclude-threads", "Namibia")) {
            assertThread(event, "India", "Lake Tanganyika");
            assertNotThread(event, "Namibia");
        }

        for (var event : scrub(file, "--exclude-threads", "Nam*")) {
            assertThread(event, "Lake Tanganyika", "India");
            assertNotThread(event, "Namibia");
        }

        for (var event : scrub(file, "--exclude-threads", "Namibia,Lake Tanganyika")) {
            assertThread(event, "India");
            assertNotThread(event, "Namibia", "Lake Tanganyika");
        }

        for (var event : scrub(file, "--exclude-events", "India", "--include-events", "Lake Tanganyika")) {
            assertThread(event, "Namibia");
            assertNotThread(event, "India", "Lake Tanganyika");
        }
    }

    private static void assertNotThread(RecordedEvent event, String... threadNames) {
        String s = event.getThread().getJavaName();
        for (String threadName : threadNames) {
            if (threadName.equals(s)) {
                throw new AssertionError("Found unexpected thread" + threadName);
            }
        }
    }

    private static void assertThread(RecordedEvent event, String... threadNames) {
        String s = event.getThread().getJavaName();
        for (String threadName : threadNames) {
            if (threadName.equals(s)) {
                return;
            }
        }
        throw new AssertionError("Found unexpected thread" + s);
    }

    private static void assertNotEvent(RecordedEvent event, String... eventNames) {
        String s = event.getEventType().getName();
        for (String eventName : eventNames) {
            String n = "example." + eventName;
            if (n.equals(s)) {
                throw new AssertionError("Found unexpected " + eventName + " event");
            }
        }
    }

    private static void assertEvent(RecordedEvent event, String... eventNames) {
        String s = event.getEventType().getName();
        for (String eventName : eventNames) {
            String n = "example." + eventName;
            if (n.equals(s)) {
                return;
            }
        }
        throw new AssertionError("Found unexpected " + s + " event");
    }

    private static List<RecordedEvent> scrub(Path input, String... options) throws Throwable {
        Path output = Path.of("scrubbed.jfr");
        List<String> arguments = new ArrayList<>();
        arguments.add("scrub");
        arguments.addAll(Arrays.asList(options));
        arguments.add(input.toAbsolutePath().toString());
        arguments.add(output.toAbsolutePath().toString());

        var outp = ExecuteHelper.jfr(arguments.toArray(String[]::new));
        System.out.println(outp.getStderr());
        System.out.println(outp.getStdout());
        List<RecordedEvent> events = RecordingFile.readAllEvents(output);
        Files.delete(output);
        return events;
    }

    private static void emit(int count, String threadName, Class<? extends Event> eventClass) throws Throwable {
        Thread t = new Thread(() -> emitEvents(count, eventClass), threadName);
        t.start();
        t.join();
    }

    private static void emitEvents(int count, Class<? extends Event> eventClass) {
        for (int i = 0; i < count; i++) {
            try {
                Event event = eventClass.getDeclaredConstructor().newInstance();
                event.commit();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

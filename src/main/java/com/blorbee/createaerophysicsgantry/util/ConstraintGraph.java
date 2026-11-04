package com.blorbee.createaerophysicsgantry.util;

import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ConstraintGraph {
    public static final class Edge {
        final UUID a;
        final UUID b;
        final PhysicsConstraintHandle handle;
        Edge(UUID a, UUID b, PhysicsConstraintHandle handle) {
            this.a = a;
            this.b = b;
            this.handle = handle;
        }
    }

    private static final Set<Edge> EDGES = ConcurrentHashMap.newKeySet();

    public static Edge add(@Nullable UUID a, @Nullable UUID b, PhysicsConstraintHandle handle) {
        Edge e = new Edge(a, b, handle);
        EDGES.add(e);
        return e;
    }

    public static void remove(@Nullable Edge e) {
        if (e != null)
            EDGES.remove(e);
    }

    public static void clear() {
        EDGES.clear();
    }

    public static boolean isAnchoredToWorld(UUID start, @Nullable PhysicsConstraintHandle ignoreHandle) {
        EDGES.removeIf(e -> !e.handle.isValid());

        Set<UUID> seen = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            for (Edge e : EDGES) {
                if (e.handle == ignoreHandle || !e.handle.isValid())
                    continue;

                UUID other;
                if (cur.equals(e.a)) other = e.b;
                else if (cur.equals(e.b)) other = e.a;
                else continue;

                if (other == null)
                    return true; // reached the world
                if (seen.add(other))
                    queue.add(other);
            }
        }
        return false;
    }
}

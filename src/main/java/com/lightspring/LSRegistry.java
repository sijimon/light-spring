package com.lightspring;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LSRegistry implements AutoCloseable {
    private final String basePackage;
    private final boolean preferAnnotations;

    private final Map<Class<?>, Class<?>> components = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<Class<?>>> creationStack = ThreadLocal.withInitial(ArrayDeque::new);

    private LSRegistry(String basePackage, boolean preferAnnotations) {
        this.basePackage = basePackage;
        this.preferAnnotations = preferAnnotations;
    }

    public static class RegistryBuilder {
        private String basePackage;
        private boolean preferAnnotations;

        public RegistryBuilder basePackage(String pkg) {
            this.basePackage = pkg;
            return this;
        }

        public RegistryBuilder preferAnnotations(boolean v) {
            this.preferAnnotations = v;
            return this;
        }

        public LSRegistry build() {
            return new LSRegistry(basePackage, preferAnnotations);
        }
    }

    public void scan() {
        if (basePackage == null || basePackage.isBlank()) {
            throw new IllegalStateException("Base package must be set before scan()");
        }
        String path = basePackage.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL url = cl.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Could not find base package on classpath: " + basePackage);
        }
        if (!"file".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalStateException("Only file-system scanning is supported in v0.1 (URL=" + url + ")");
        }

        File root = new File(url.getFile());
        List<String> classNames = new ArrayList<>();
        collectClassNames(root, basePackage, classNames);

        for (String cn : classNames) {
            try {
                Class<?> c = Class.forName(cn, false, cl);
                if (isComponentCandidate(c)) {
                    registerComponent(c);
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        for (Class<?> type : components.keySet()) {
            getBean(type);
        }
    }

    public <T> T getBean(Class<T> type) {
        Object existing = singletons.get(type);
        if (existing != null) return type.cast(existing);

        Class<?> impl = resolveImplementation(type);
        if (impl == null) {
            throw new NoSuchElementException("No component registered for: " + type.getName());
        }

        try {
            Object created = createInstance(impl);
            singletons.putIfAbsent(impl, created);
            if (!impl.equals(type)) {
                singletons.putIfAbsent(type, created);
            }
            return type.cast(created);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create bean: " + impl.getName(), e);
        }
    }

    @Override
    public void close() {
        singletons.clear();
        components.clear();
        creationStack.remove();
    }

    private void collectClassNames(File dir, String pkg, List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectClassNames(f, pkg + "." + f.getName(), out);
            } else if (f.getName().endsWith(".class")) {
                String simple = f.getName().substring(0, f.getName().length() - 6);
                out.add(pkg + "." + simple);
            }
        }
    }

    private boolean isComponentCandidate(Class<?> c) {
        if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) return false;
        if (!Modifier.isPublic(c.getModifiers())) return false;
        boolean markerBased = !preferAnnotations;
        if (markerBased) {
            return ComponentMarker.class.isAssignableFrom(c);
        } else {
            return ComponentMarker.class.isAssignableFrom(c);
        }
    }

    private void registerComponent(Class<?> impl) {
        components.putIfAbsent(impl, impl);
        for (Class<?> itf : impl.getInterfaces()) {
            if (itf == ComponentMarker.class) continue;
            components.putIfAbsent(itf, impl);
        }
    }

    private Class<?> resolveImplementation(Class<?> requested) {
        Class<?> impl = components.get(requested);
        if (impl != null) return impl;
        if (components.containsKey(requested)) return components.get(requested);
        for (Map.Entry<Class<?>, Class<?>> e : components.entrySet()) {
            if (e.getKey().equals(requested) || e.getValue().equals(requested)) {
                return e.getValue();
            }
        }
        if (isComponentCandidate(requested)) return requested;
        return null;
    }

    private Object createInstance(Class<?> impl) throws Exception {
        Deque<Class<?>> stack = creationStack.get();
        if (stack.contains(impl)) {
            throw new IllegalStateException(renderCycle(stack, impl));
        }
        stack.push(impl);
        try {
            Constructor<?>[] ctors = impl.getDeclaredConstructors();
            Arrays.sort(ctors, Comparator.<Constructor<?>>comparingInt(Constructor::getParameterCount).reversed());
            for (Constructor<?> ctor : ctors) {
                Class<?>[] params = ctor.getParameterTypes();
                Object[] args = new Object[params.length];
                boolean resolvable = true;
                for (int i = 0; i < params.length; i++) {
                    try {
                        args[i] = getBean(params[i]);
                    } catch (Exception e) {
                        resolvable = false;
                        break;
                    }
                }
                if (resolvable) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(args);
                }
            }
            try {
                Constructor<?> noArg = impl.getDeclaredConstructor();
                noArg.setAccessible(true);
                return noArg.newInstance();
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("No suitable constructor found for " + impl.getName());
            }
        } finally {
            stack.pop();
        }
    }

    private String renderCycle(Deque<Class<?>> stack, Class<?> again) {
        List<String> chain = new ArrayList<>(stack.size() + 1);
        for (Class<?> c : stack) chain.add(c.getName());
        chain.add(again.getName());
        return "Circular dependency detected: " + String.join(" -> ", chain);
    }
}

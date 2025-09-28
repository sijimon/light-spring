package com.lightspring;

public class LSApplicationContext implements AutoCloseable {
    private final LSRegistry registry;

    private LSApplicationContext(LSRegistry registry) {
        this.registry = registry;
    }

    public static class ApplicationContextBuilder {
        private String basePackage;
        private boolean preferAnnotations;

        public ApplicationContextBuilder basePackage(String pkg) {
            this.basePackage = pkg;
            return this;
        }

        public ApplicationContextBuilder preferAnnotations(boolean v) {
            this.preferAnnotations = v;
            return this;
        }

        public LSApplicationContext build() {
            LSRegistry reg = new LSRegistry.RegistryBuilder()
                    .basePackage(basePackage)
                    .preferAnnotations(preferAnnotations)
                    .build();
            return new LSApplicationContext(reg);
        }
    }

    public void refresh() {
        registry.scan();
    }

    public <T> T getBean(Class<T> type) {
        return registry.getBean(type);
    }

    @Override
    public void close() {
        registry.close();
    }
}

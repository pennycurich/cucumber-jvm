package cucumber.runtime.java.spring;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class GlueCodeContext {
    protected static final ThreadLocal<GlueCodeContext> GLUE_CODE_CONTEXT_REFERENCE = new ThreadLocal<>();
    protected static final ThreadLocal<Integer> COUNTER_REFERENCE = new ThreadLocal<>();
    protected static final ThreadLocal<Map<String, Object>> OBJECTS_REFERENCE = new ThreadLocal<>();
    protected static final ThreadLocal<Map<String, Runnable>> CALLBACKS_REFERENCE = new ThreadLocal<>();


    protected GlueCodeContext() {
        GLUE_CODE_CONTEXT_REFERENCE.set(this);
        COUNTER_REFERENCE.set(0);
        OBJECTS_REFERENCE.set(new HashMap<>());
        CALLBACKS_REFERENCE.set(new HashMap<>());
    }

    public void start() {
        cleanUp();
        COUNTER_REFERENCE.set(COUNTER_REFERENCE.get() + 1);
    }


    protected void cleanUp() {
        OBJECTS_REFERENCE.get().clear();
        CALLBACKS_REFERENCE.get().clear();
    }

    public String getId() {
        long threadId = Thread.currentThread().getId();
        return String.format("cucumber_glue_%s:%s", threadId, COUNTER_REFERENCE.get());
    }

    public void stop() {
        runCallbacks();
        cleanUp();
    }

    protected void runCallbacks() {
        Map<String, Runnable> map = CALLBACKS_REFERENCE.get();
        Collection<Runnable> callbacks = map.values();
        callbacks.forEach(Runnable::run);
    }

    public Object get(String name) {
        Map<String, Object> map = OBJECTS_REFERENCE.get();
        return map.get(name);
    }

    public Object put(String name, Object object) {
        Map<String, Object> map = OBJECTS_REFERENCE.get();
        return map.put(name, object);
    }

    public Object remove(String name) {
        Map<String, Runnable> callbacksMap = CALLBACKS_REFERENCE.get();
        callbacksMap.remove(name);

        Map<String, Object> objectsMap = OBJECTS_REFERENCE.get();
        return objectsMap.remove(name);
    }


    public Runnable registerDestructionCallback(String name, Runnable callback) {
        Map<String, Runnable> map = CALLBACKS_REFERENCE.get();
        return map.put(name, callback);
    }

    public static GlueCodeContext getInstance() {
        GlueCodeContext context = GLUE_CODE_CONTEXT_REFERENCE.get();
        return null == context ? new GlueCodeContext() : context;
    }
}

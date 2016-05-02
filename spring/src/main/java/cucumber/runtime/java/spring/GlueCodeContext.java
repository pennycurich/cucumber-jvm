package cucumber.runtime.java.spring;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class GlueCodeContext {
	protected static final AtomicInteger COUNTER = new AtomicInteger(0);

    protected static final ThreadLocal<GlueCodeContext> GLUE_CODE_CONTEXT_REFERENCE = new ThreadLocal<>();
    protected static final ThreadLocal<Map<String, Object>> OBJECTS_REFERENCE = new ThreadLocal<>();
    protected static final ThreadLocal<Map<String, Runnable>> CALLBACKS_REFERENCE = new ThreadLocal<>();

	private Integer id;

    protected GlueCodeContext() {
        GLUE_CODE_CONTEXT_REFERENCE.set(this);
        OBJECTS_REFERENCE.set(new HashMap<>());
        CALLBACKS_REFERENCE.set(new HashMap<>());
    }

    public void start() {
        cleanUp();
		id = COUNTER.incrementAndGet();
    }


    protected void cleanUp() {
        OBJECTS_REFERENCE.get().clear();
        CALLBACKS_REFERENCE.get().clear();
    }

    public String getId() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
        return String.format("cucumber_glue_%s:%s", threadId, id);
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

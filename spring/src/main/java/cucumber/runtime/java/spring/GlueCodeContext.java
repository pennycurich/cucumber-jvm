package cucumber.runtime.java.spring;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

class GlueCodeContext {
	protected static final ThreadLocal<GlueCodeContext> GLUE_CODE_CONTEXT_REFERENCE = new ThreadLocal<>();

	private int id;
	private Map<String, Object> objectMap;
	private Map<String, Runnable> callbackMap;

	protected GlueCodeContext() {
		GLUE_CODE_CONTEXT_REFERENCE.set(this);
		objectMap = new HashMap<>();
		callbackMap = new LinkedHashMap<>();
		id = 0;
	}

	public void start() {
		cleanUp();
		id++;
	}

	protected void cleanUp() {
		objectMap.clear();
		callbackMap.clear();
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
		Collection<Runnable> callbacks = callbackMap.values();
		callbacks.forEach(Runnable::run);
	}

	public Object get(String name) {
		return objectMap.get(name);
	}

	public Object put(String name, Object object) {
		return objectMap.put(name, object);
	}

	public Object remove(String name) {
		callbackMap.remove(name);
		return objectMap.remove(name);
	}

	public Runnable registerDestructionCallback(String name, Runnable callback) {
		return callbackMap.put(name, callback);
	}

	public static GlueCodeContext getInstance() {
		GlueCodeContext context = GLUE_CODE_CONTEXT_REFERENCE.get();
		return null == context ? new GlueCodeContext() : context;
	}
}

package cucumber.runtime.java.spring;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class GlueCodeContext {
	protected static final Deque<GlueCodeContext> ALL_CONTEXTS = new ConcurrentLinkedDeque<>();
	protected static final AtomicInteger COUNTER = new AtomicInteger(0);

	private final ConcurrentMap<String, Object> objectMap;
	private final ConcurrentMap<String, Runnable> callbackMap;

	public GlueCodeContext() {
		objectMap = new ConcurrentHashMap<>();
		callbackMap = new ConcurrentHashMap<>();
		ALL_CONTEXTS.add(this);
	}

	public String getId() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		return String.format("cucumber_glue_%s:%s", threadId, COUNTER.get());
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

	protected void runDestructionCallbacks() {
		callbackMap.values().forEach(this::runDestructionCallback);
	}

	protected void runDestructionCallback(Runnable callback) {
		try {
			callback.run();
		} catch (Exception e) {
			Log log = LogFactory.getLog(this.getClass());
			log.warn("unable to run destruction callback: " + callback, e);
		}
	}

	public static void start() {
		for (GlueCodeContext context : ALL_CONTEXTS) {
			context.objectMap.clear();
			context.callbackMap.clear();
		}
		COUNTER.incrementAndGet();
	}

	public static void stop() {
		for (GlueCodeContext context : ALL_CONTEXTS) {
			context.objectMap.clear();
			context.runDestructionCallbacks();
			context.callbackMap.clear();

		}
	}
}

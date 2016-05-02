package cucumber.runtime.java.spring;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

class GlueCodeScope implements Scope {
	public static final String NAME = "cucumber-glue";

	protected static final ThreadLocal<GlueCodeContext> THREAD_LOCAL_CONTEXT= new ThreadLocal<>();

	@Override
	public Object get(String name, ObjectFactory<?> objectFactory) {
		GlueCodeContext context = getGlueCodeContext();
		Object o = context.get(name);
		if (o == null) {
			o = objectFactory.getObject();
			context.put(name, o);
		}
		return o;
	}

	protected GlueCodeContext getGlueCodeContext() {
		GlueCodeContext context = THREAD_LOCAL_CONTEXT.get();
		if (null == context) {
			context = new GlueCodeContext();
			THREAD_LOCAL_CONTEXT.set(context);
		}
		return context;
	}


	@Override
	public Object remove(String name) {
		GlueCodeContext context = THREAD_LOCAL_CONTEXT.get();
		return null == context ? null : context.remove(name);
	}

	@Override
	public void registerDestructionCallback(String name, Runnable callback) {
		GlueCodeContext context = getGlueCodeContext();
		context.registerDestructionCallback(name, callback);
	}

	@Override
	public Object resolveContextualObject(String key) {
		return null;
	}

	@Override
	public String getConversationId() {
		GlueCodeContext context = getGlueCodeContext();
		return context.getId();
	}
}

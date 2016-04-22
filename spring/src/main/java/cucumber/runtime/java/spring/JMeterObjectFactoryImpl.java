package cucumber.runtime.java.spring;

import cucumber.api.java.ObjectFactory;
import cucumber.runtime.CucumberException;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.TestContextManager;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;

/**
 * Spring based implementation of ObjectFactory.
 * <ul>
 * <li>It uses TestContextManager to manage the spring context.
 * Configuration via: &#64;ContextConfiguration or &#64;ContextHierarcy
 * At least on step definition class needs to have a ^#64ContextConfiguration or
 * &#64;ContextHierarchy annotation. If more that one step definition class has such
 * an annotation, the annotations must be equal on the different step definition
 * classes. If no step definition class with @ContextConfiguration or
 * &#64;ContextHierarcy is found, it will try to load cucumber.xml from the classpath.
 * </li>
 * <li>The step definitions class with &#64;ContextConfiguration or &#64;ContextHierarchy
 * annotation, may also have a &#64;WebAppConfiguration or &#64;DirtiesContext annotation.
 * </li>
 * <li>The step definitions added to the TestContextManagers context and
 * is reloaded for each scenario.</li>
 * </ul>
 * <p>
 * Application beans are accessible from the step definitions using autowiring
 * (with annotations).
 */
public class JMeterObjectFactoryImpl implements ObjectFactory, JMeterObjectFactory {

	protected static final String CUCUMBER_RESOURCE = "cucumber.xml";

	protected CucumberTestContextManager testContextManager;
	protected ConfigurableApplicationContext fallback;

	protected final Collection<Class<?>> stepClasses = new HashSet<>();
	protected Class<?> stepClassWithSpringContext = null;

	public JMeterObjectFactoryImpl() {
	}

	@Override
	public boolean addClass(final Class<?> stepClass) {
		if (!stepClasses.contains(stepClass)) {
			if (dependsOnSpringContext(stepClass)) {
				if (stepClassWithSpringContext == null) {
					stepClassWithSpringContext = stepClass;
				}
				else {
					checkAnnotationsEqual(stepClassWithSpringContext, stepClass);
				}
			}
			stepClasses.add(stepClass);
		}
		return true;
	}

	protected void checkAnnotationsEqual(Class<?> stepClassWithSpringContext, Class<?> stepClass) {
		Annotation[] annotations1 = stepClassWithSpringContext.getAnnotations();
		Annotation[] annotations2 = stepClass.getAnnotations();
		if (annotations1.length != annotations2.length) {
			throw new CucumberException("Annotations differs on glue classes found: " +
				stepClassWithSpringContext.getName() + ", " +
				stepClass.getName());
		}
		for (Annotation annotation : annotations1) {
			if (!isAnnotationInArray(annotation, annotations2)) {
				throw new CucumberException("Annotations differs on glue classes found: " +
					stepClassWithSpringContext.getName() + ", " +
					stepClass.getName());
			}
		}
	}

	protected boolean isAnnotationInArray(Annotation annotation, Annotation[] annotations) {
		for (Annotation annotationFromArray : annotations) {
			if (annotation.equals(annotationFromArray)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void start() {
		ConfigurableApplicationContext context = getContext();
		if (null == context) {
			if (stepClassWithSpringContext != null) {
				testContextManager = new CucumberTestContextManager(stepClassWithSpringContext);
				try {
					testContextManager.beforeTestClass();
				}
				catch (Exception e) {
					throw new CucumberException(e.getMessage(), e);
				}
			}
			else {
				URL resource = getClass().getClassLoader().getResource(CUCUMBER_RESOURCE);
				fallback = null == resource ? new GenericApplicationContext() : new ClassPathXmlApplicationContext(CUCUMBER_RESOURCE);
			}

			context = getContext();
			context.registerShutdownHook();
			GlueCodeContext.getInstance().start();
		}

		registerGlueCodeScope();
		stepClasses.forEach(this::registerStepClassBeanDefinition);
	}

	protected ConfigurableApplicationContext getContext() {
		return null == testContextManager ? fallback : testContextManager.getContext();
	}

	protected void registerGlueCodeScope() {
		ConfigurableApplicationContext context = getContext();
		do {
			ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
			Scope registeredScope = beanFactory.getRegisteredScope(GlueCodeScope.NAME);
			if (null == registeredScope) {
				beanFactory.registerScope(GlueCodeScope.NAME, new GlueCodeScope());
			}
			context = (ConfigurableApplicationContext) context.getParent();
		} while (context != null);
	}

	private void registerStepClassBeanDefinition(Class<?> stepClass) {
		ConfigurableApplicationContext context = getContext();
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
		BeanDefinition beanDefinition = BeanDefinitionBuilder
			.genericBeanDefinition(stepClass)
			.setScope(GlueCodeScope.NAME)
			.getBeanDefinition();
		registry.registerBeanDefinition(stepClass.getName(), beanDefinition);
	}

	@Override
	public void stop() {
		notifyContextManagerAboutTestClassFinished();
		GlueCodeContext.getInstance().stop();
	}

	@Override
	public void destroy() {
		ConfigurableApplicationContext context = getContext();
		try {
			if (null != context && context.isActive()) {
				context.close();
			}
		}
		finally {
			testContextManager = null;
			fallback = null;
		}
	}

	private void notifyContextManagerAboutTestClassFinished() {
		if (testContextManager != null) {
			try {
				testContextManager.afterTestClass();
			}
			catch (Exception e) {
				throw new CucumberException(e.getMessage(), e);
			}
		}
	}

	@Override
	public <T> T getInstance(final Class<T> type) {
		try {
			BeanFactory beanFactory = getContext().getBeanFactory();
			return beanFactory.getBean(type);
		}
		catch (BeansException e) {
			throw new CucumberException(e.getMessage(), e);
		}
	}

	private boolean dependsOnSpringContext(Class<?> type) {
		boolean hasStandardAnnotations = annotatedWithSupportedSpringRootTestAnnotations(type);

		if (hasStandardAnnotations) {
			return true;
		}

		final Annotation[] annotations = type.getDeclaredAnnotations();
		return (annotations.length == 1) && annotatedWithSupportedSpringRootTestAnnotations(annotations[0].annotationType());
	}

	private boolean annotatedWithSupportedSpringRootTestAnnotations(Class<?> type) {
		return type.isAnnotationPresent(ContextConfiguration.class)
			|| type.isAnnotationPresent(ContextHierarchy.class);
	}
}

class CucumberTestContextManager extends TestContextManager {

	public CucumberTestContextManager(Class<?> testClass) {
		super(testClass);
	}

	public ConfigurableApplicationContext getContext() {
		return (ConfigurableApplicationContext) getTestContext().getApplicationContext();
	}
}

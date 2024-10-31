/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.event;

import java.util.Map;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

/**
 * {@link GenericApplicationListener} adapter that determines supported event types
 * through introspecting the generically declared type of the target listener.
 *
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 3.0
 * @see org.springframework.context.ApplicationListener#onApplicationEvent
 */
public class GenericApplicationListenerAdapter implements GenericApplicationListener {

	private static final Map<Class<?>, ResolvableType> eventTypeCache = new ConcurrentReferenceHashMap<>();


	private final ApplicationListener<ApplicationEvent> delegate;

	/**
	 * 泛型中声明的类型
	 */
	@Nullable
	private final ResolvableType declaredEventType;


	/**
	 * Create a new GenericApplicationListener for the given delegate.
	 * @param delegate the delegate listener to be invoked
	 */
	@SuppressWarnings("unchecked")
	public GenericApplicationListenerAdapter(ApplicationListener<?> delegate) {
		Assert.notNull(delegate, "Delegate listener must not be null");
		this.delegate = (ApplicationListener<ApplicationEvent>) delegate;
		// 解析声明的类型
		this.declaredEventType = resolveDeclaredEventType(this.delegate);
	}


	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		this.delegate.onApplicationEvent(event);
	}

	/**
	 *
	 * @param eventType the event type (never {@code null})
	 * @return
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean supportsEventType(ResolvableType eventType) {
		if (this.delegate instanceof GenericApplicationListener) {
			return ((GenericApplicationListener) this.delegate).supportsEventType(eventType);
		}
		else if (this.delegate instanceof SmartApplicationListener) {
			Class<? extends ApplicationEvent> eventClass = (Class<? extends ApplicationEvent>) eventType.resolve();
			return (eventClass != null && ((SmartApplicationListener) this.delegate).supportsEventType(eventClass));
		}
		else {
			// listener中声明的泛型类型是否是否是 eventType 的子类，
			// 也就是说泛型中的类型得是 ApplicationStartingEvent 的父类
			return (this.declaredEventType == null || this.declaredEventType.isAssignableFrom(eventType));
		}
	}

	@Override
	public boolean supportsSourceType(@Nullable Class<?> sourceType) {
		/**
		 * 代理类如果是 SmartApplicationListener，则判断自身的supportsSourceType，如果自身没有则直接调用接口默认方法
		 * 否则，直接返回true
		 */
		return !(this.delegate instanceof SmartApplicationListener) ||
				((SmartApplicationListener) this.delegate).supportsSourceType(sourceType);
	}

	@Override
	public int getOrder() {
		return (this.delegate instanceof Ordered ? ((Ordered) this.delegate).getOrder() : Ordered.LOWEST_PRECEDENCE);
	}

	@Override
	public String getListenerId() {
		return (this.delegate instanceof SmartApplicationListener ?
				((SmartApplicationListener) this.delegate).getListenerId() : "");
	}


	/**
	 * 返回的可能不是 ApplicationEvent
	 * @param listener
	 * @return
	 */
	@Nullable
	private static ResolvableType resolveDeclaredEventType(ApplicationListener<ApplicationEvent> listener) {
		ResolvableType declaredEventType = resolveDeclaredEventType(listener.getClass());
		// 判断是否是 ApplicationEvent 的子类
		if (declaredEventType == null || declaredEventType.isAssignableFrom(ApplicationEvent.class)) {
			Class<?> targetClass = AopUtils.getTargetClass(listener);
			if (targetClass != listener.getClass()) {
				declaredEventType = resolveDeclaredEventType(targetClass);
			}
		}
		return declaredEventType;
	}

	/**
	 * 解析声明的泛型类型
	 * @param listenerType
	 * @return
	 */
	@Nullable
	static ResolvableType resolveDeclaredEventType(Class<?> listenerType) {
		ResolvableType eventType = eventTypeCache.get(listenerType);
		if (eventType == null) {
			// 获取ApplicationListener接口中的泛型类型的第1个参数，也即声明的泛型类型
			eventType = ResolvableType.forClass(listenerType).as(ApplicationListener.class).getGeneric();
			eventTypeCache.put(listenerType, eventType);
		}
		return (eventType != ResolvableType.NONE ? eventType : null);
	}

}

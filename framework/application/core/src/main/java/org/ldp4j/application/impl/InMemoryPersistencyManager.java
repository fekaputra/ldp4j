/**
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   This file is part of the LDP4j Project:
 *     http://www.ldp4j.org/
 *
 *   Center for Open Middleware
 *     http://www.centeropenmiddleware.com/
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Copyright (C) 2014 Center for Open Middleware.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Artifact    : org.ldp4j.framework:ldp4j-application-core:1.0.0-SNAPSHOT
 *   Bundle      : ldp4j-application-core-1.0.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.application.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.ldp4j.application.data.Name;
import org.ldp4j.application.endpoint.Endpoint;
import org.ldp4j.application.engine.context.EntityTag;
import org.ldp4j.application.ext.ResourceHandler;
import org.ldp4j.application.lifecycle.LifecycleException;
import org.ldp4j.application.lifecycle.Managed;
import org.ldp4j.application.resource.Container;
import org.ldp4j.application.resource.Resource;
import org.ldp4j.application.resource.ResourceId;
import org.ldp4j.application.spi.PersistencyManager;
import org.ldp4j.application.spi.Transaction;
import org.ldp4j.application.template.BasicContainerTemplate;
import org.ldp4j.application.template.ContainerTemplate;
import org.ldp4j.application.template.DirectContainerTemplate;
import org.ldp4j.application.template.ImmutableTemplateFactory;
import org.ldp4j.application.template.IndirectContainerTemplate;
import org.ldp4j.application.template.MembershipAwareContainerTemplate;
import org.ldp4j.application.template.ResourceTemplate;
import org.ldp4j.application.template.TemplateLibrary;
import org.ldp4j.application.template.TemplateVisitor;

final class InMemoryPersistencyManager implements PersistencyManager, Managed {

	private final class ImmutableTemplateLibrary implements TemplateLibrary {

		@Override
		public ResourceTemplate findByHandler(Class<? extends ResourceHandler> handlerClass) {
			return templateOfHandler(handlerClass);
		}

		@Override
		public ResourceTemplate findById(String templateId) {
			return templateOfId(templateId);
		}

		@Override
		public boolean contains(ResourceTemplate template) {
			return InMemoryPersistencyManager.this.templateLibrary.contains(template);
		}

		@Override
		public void accept(final TemplateVisitor visitor) {
			InMemoryPersistencyManager.this.templateLibrary.accept(
				new TemplateVisitor() {
					@Override
					public void visitResourceTemplate(ResourceTemplate template) {
						ImmutableTemplateFactory.newImmutable(template).accept(visitor);
					}
					@Override
					public void visitContainerTemplate(ContainerTemplate template) {
						ImmutableTemplateFactory.newImmutable(template).accept(visitor);
					}
					@Override
					public void visitBasicContainerTemplate(BasicContainerTemplate template) {
						ImmutableTemplateFactory.newImmutable(template).accept(visitor);
					}
					@Override
					public void visitMembershipAwareContainerTemplate(MembershipAwareContainerTemplate template) {
						ImmutableTemplateFactory.newImmutable(template).accept(visitor);
					}
					@Override
					public void visitDirectContainerTemplate(DirectContainerTemplate template) {
						ImmutableTemplateFactory.newImmutable(template).accept(visitor);
					}
					@Override
					public void visitIndirectContainerTemplate(IndirectContainerTemplate template) {
						ImmutableTemplateFactory.newImmutable(template).accept(visitor);
					}
				}
			);
		}
	}

	private final InMemoryResourceRepository resourceRepository;
	private final InMemoryEndpointRepository endpointRepository;
	private final InMemoryTemplateLibrary templateLibrary;

	private final ThreadLocal<InMemoryTransaction> currentTransaction;
	private final AtomicLong transactionCounter;

	InMemoryPersistencyManager() {
		this.resourceRepository=new InMemoryResourceRepository();
		this.endpointRepository=new InMemoryEndpointRepository();
		this.templateLibrary=new InMemoryTemplateLibrary();
		this.currentTransaction=new ThreadLocal<InMemoryTransaction>();
		this.transactionCounter=new AtomicLong();
	}

	private Class<? extends ResourceHandler> toResourceHandlerClass(Class<?> targetClass) {
		checkArgument(ResourceHandler.class.isAssignableFrom(targetClass),"Class '%s' does not implement '%s'",targetClass.getCanonicalName(),ResourceHandler.class.getCanonicalName());
		return targetClass.asSubclass(ResourceHandler.class);
	}

	private ResourceTemplate findTemplate(String id) {
		checkNotNull(id,"TemplateId identifier cannot be null");
		ResourceTemplate result = this.templateOfId(id);
		if(result==null) {
			throw new IllegalStateException("Could not find template '"+id+"'");
		}
		return result;
	}

	private <T extends Resource> ResourceTemplate findInstantiableTemplate(String templateId, boolean requireContainer) {
		ResourceTemplate template = findTemplate(templateId);
		if(!(template instanceof ContainerTemplate) && requireContainer) {
			throw new IllegalStateException("Cannot create a container from non-container template '"+template.id()+"'");
		}
		return template;
	}

	private InMemoryResource createResource(ResourceTemplate template, Name<?> resourceId, Resource parent) {
		checkNotNull(resourceId,"ResourceSnapshot identifier cannot be null");
		final ResourceId parentId=parent!=null?parent.id():null;
		final ResourceId id=ResourceId.createId(resourceId, template);
		final AtomicReference<InMemoryResource> result=new AtomicReference<InMemoryResource>();
		template.
			accept(
				new TemplateVisitor() {
					@Override
					public void visitResourceTemplate(ResourceTemplate template) {
						result.set(new InMemoryResource(id,parentId));
					}
					@Override
					public void visitContainerTemplate(ContainerTemplate template) {
						result.set(new InMemoryContainer(id,parentId));
					}
					@Override
					public void visitBasicContainerTemplate(BasicContainerTemplate template) {
						visitContainerTemplate(template);
					}
					@Override
					public void visitMembershipAwareContainerTemplate(MembershipAwareContainerTemplate template) {
						visitContainerTemplate(template);
					}
					@Override
					public void visitDirectContainerTemplate(DirectContainerTemplate template) {
						visitContainerTemplate(template);
					}
					@Override
					public void visitIndirectContainerTemplate(IndirectContainerTemplate template) {
						visitContainerTemplate(template);
					}
				}
			);
		InMemoryResource resource = result.get();
		resource.setPersistencyManager(this);
		return resource;
	}

	void disposeTransaction(InMemoryTransaction transaction) {
		this.currentTransaction.set(null);
	}

	@Override
	public Transaction currentTransaction() {
		InMemoryTransaction transaction=this.currentTransaction.get();
		if(transaction==null) {
			transaction=new InMemoryTransaction(this.transactionCounter.getAndIncrement(),this);
			this.currentTransaction.set(transaction);
		}
		return transaction;
	}

	@Override
	public ResourceTemplate registerHandler(Class<?> targetClass) {
		return ImmutableTemplateFactory.newImmutable(this.templateLibrary.registerHandler(targetClass));
	}

	@Override
	public boolean isHandlerRegistered(Class<?> handlerClass) {
		return this.templateLibrary.findByHandler(toResourceHandlerClass(handlerClass))!=null;
	}

	@Override
	public TemplateLibrary exportTemplates() {
		return new ImmutableTemplateLibrary();
	}

	public Resource createResource(String templateId, Name<?> resourceId, Resource parent) {
		return createResource(findTemplate(templateId),resourceId,parent);
	}

	public <T extends Resource> T createResource(String templateId, Name<?> resourceId, Resource parent, Class<? extends T> expectedResourceClass) {
		ResourceTemplate template=
			findInstantiableTemplate(
				templateId,
				Container.class.isAssignableFrom(expectedResourceClass));
		Resource newResource = createResource(template,resourceId,parent);
		return expectedResourceClass.cast(newResource);
	}

	@Override
	public Endpoint createEndpoint(Resource resource, String path, EntityTag entityTag, Date lastModified) {
		checkNotNull(resource,"Endpoint's resource cannot be null");
		checkNotNull(entityTag,"Endpoint's entity tag cannot be null");
		checkNotNull(lastModified,"Endpoint's Last modified data cannot be null");
		return new InMemoryEndpoint(this.endpointRepository.nextIdentifier(),path,resource.id(),entityTag,lastModified);
	}

	@Override
	public <T extends Resource> T resourceOfId(ResourceId id, Class<? extends T> expectedResourceClass) {
		return this.resourceRepository.resourceById(id, expectedResourceClass);
	}

	@Override
	public Resource resourceOfId(ResourceId id) {
		return this.resourceRepository.resourceOfId(id);
	}

	@Override
	public Container containerOfId(ResourceId id) {
		return this.resourceRepository.containerOfId(id);
	}

	@Override
	public Endpoint endpointOfPath(String path) {
		return this.endpointRepository.endpointOfPath(path);
	}

	@Override
	public Endpoint endpointOfResource(ResourceId id) {
		return this.endpointRepository.endpointOfResource(id);
	}

	@Override
	public void add(Resource resource) {
		this.resourceRepository.add(resource);
	}

	@Override
	public void add(Endpoint endpoint) {
		this.endpointRepository.add(endpoint);
	}

	@Override
	public void remove(Resource resource) {
		this.resourceRepository.remove(resource);
	}

	@Override
	public void remove(Endpoint endpoint) {
		this.endpointRepository.remove(endpoint);
	}

	@Override
	public void init() throws LifecycleException {
		this.resourceRepository.init();
		this.endpointRepository.init();
	}

	@Override
	public void shutdown() throws LifecycleException {
		this.endpointRepository.shutdown();
		this.resourceRepository.shutdown();
	}

	@Override
	public ResourceTemplate templateOfHandler(Class<? extends ResourceHandler> handlerClass) {
		checkNotNull(handlerClass,"Resource handler cannot be null");
		return ImmutableTemplateFactory.newImmutable(this.templateLibrary.findByHandler(handlerClass));
	}

	@Override
	public ResourceTemplate templateOfId(String templateId) {
		checkNotNull(templateId,"Template identifier cannot be null");
		return ImmutableTemplateFactory.newImmutable(this.templateLibrary.findById(templateId));
	}

	public <T extends ResourceTemplate> T templateOfId(String templateId, Class<? extends T> templateClass) {
		checkNotNull(templateClass,"Template class cannot be null");
		ResourceTemplate found = templateOfId(templateId);
		if(found==null) {
			return null;
		} else if(!templateClass.isInstance(found)) {
			// TODO: Define a specialized runtime exception
			throw new IllegalArgumentException("Cannot cast template '"+templateId+"' to '"+templateClass.getCanonicalName()+"' ("+found.getClass().getCanonicalName()+")");
		}
		return templateClass.cast(found);
	}

}
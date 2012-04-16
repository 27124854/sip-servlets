/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.as7;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.mobicents.as7.Constants.ENABLED;
import static org.mobicents.as7.Constants.PROTOCOL;
import static org.mobicents.as7.Constants.SCHEME;
import static org.mobicents.as7.Constants.SOCKET_BINDING;
import static org.mobicents.as7.Constants.USE_STATIC_ADDRESS;
import static org.mobicents.as7.Constants.STATIC_SERVER_ADDRESS;
import static org.mobicents.as7.Constants.STATIC_SERVER_PORT;
import static org.mobicents.as7.Constants.USE_STUN;
import static org.mobicents.as7.Constants.STUN_SERVER_ADDRESS;
import static org.mobicents.as7.Constants.STUN_SERVER_PORT;
import java.util.List;
import java.util.Locale;

import org.apache.catalina.connector.Connector;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;

/**
 * {@code OperationHandler} responsible for adding a web connector.
 *
 * @author Emanuel Muckenhuber
 */
class SipConnectorAdd extends AbstractAddStepHandler implements DescriptionProvider {

    static final String OPERATION_NAME = ADD;

    static ModelNode getRecreateOperation(ModelNode address, ModelNode existing) {
        ModelNode op = Util.getEmptyOperation(OPERATION_NAME, address);
        op.get(PROTOCOL).set(existing.get(PROTOCOL));
        op.get(SOCKET_BINDING).set(existing.get(SOCKET_BINDING));
        if (existing.hasDefined(SCHEME)) op.get(SCHEME).set(existing.get(SCHEME).asString());
        if (existing.hasDefined(ENABLED)) op.get(ENABLED).set(existing.get(ENABLED).asBoolean());
        if (existing.hasDefined(USE_STATIC_ADDRESS)) op.get(USE_STATIC_ADDRESS).set(existing.get(USE_STATIC_ADDRESS).asBoolean());
        if (existing.hasDefined(STATIC_SERVER_ADDRESS)) op.get(STATIC_SERVER_ADDRESS).set(existing.get(STATIC_SERVER_ADDRESS).asString());
        if (existing.hasDefined(STATIC_SERVER_PORT)) op.get(STATIC_SERVER_PORT).set(existing.get(STATIC_SERVER_PORT).asInt());
        if (existing.hasDefined(USE_STUN)) op.get(USE_STUN).set(existing.get(USE_STUN).asBoolean());
        if (existing.hasDefined(STUN_SERVER_ADDRESS)) op.get(STUN_SERVER_ADDRESS).set(existing.get(STUN_SERVER_ADDRESS).asString());
        if (existing.hasDefined(STUN_SERVER_PORT)) op.get(STUN_SERVER_PORT).set(existing.get(STUN_SERVER_PORT).asInt());

        return op;
    }

    static final SipConnectorAdd INSTANCE = new SipConnectorAdd();

    private SipConnectorAdd() {
        //
    }

    @Override
    protected void populateModel(final ModelNode operation, final Resource resource) {
        final ModelNode model = resource.getModel();

        populateModel(operation, model);
        SipConfigurationHandlerUtils.initializeConnector(resource, operation);
    }

    @Override
    protected void populateModel(ModelNode operation, ModelNode subModel) {
        subModel.get(PROTOCOL).set(operation.get(PROTOCOL));
        subModel.get(SOCKET_BINDING).set(operation.get(SOCKET_BINDING));
        if (operation.hasDefined(SCHEME)) subModel.get(SCHEME).set(operation.get(SCHEME));
        if (operation.hasDefined(ENABLED)) subModel.get(ENABLED).set(operation.get(ENABLED).asBoolean());
        if (operation.hasDefined(USE_STATIC_ADDRESS)) subModel.get(USE_STATIC_ADDRESS).set(operation.get(USE_STATIC_ADDRESS).asBoolean());
        if (operation.hasDefined(STATIC_SERVER_ADDRESS)) subModel.get(STATIC_SERVER_ADDRESS).set(operation.get(STATIC_SERVER_ADDRESS));
        if (operation.hasDefined(STATIC_SERVER_PORT)) subModel.get(STATIC_SERVER_PORT).set(operation.get(STATIC_SERVER_PORT));
        if (operation.hasDefined(USE_STUN)) subModel.get(USE_STUN).set(operation.get(USE_STUN).asBoolean());
        if (operation.hasDefined(STUN_SERVER_ADDRESS)) subModel.get(STUN_SERVER_ADDRESS).set(operation.get(STUN_SERVER_ADDRESS));
        if (operation.hasDefined(STUN_SERVER_PORT)) subModel.get(STUN_SERVER_PORT).set(operation.get(STUN_SERVER_PORT));
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler, List<ServiceController<?>> newControllers) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String name = address.getLastElement().getValue();
        final String bindingRef = operation.require(SOCKET_BINDING).asString();

        final boolean enabled = operation.hasDefined(ENABLED) ? operation.get(ENABLED).asBoolean() : true;

        final boolean useStaticAddress = operation.hasDefined(USE_STATIC_ADDRESS) ? operation.get(USE_STATIC_ADDRESS).asBoolean() : false;
        final String staticServerAddress = operation.hasDefined(STATIC_SERVER_ADDRESS) ? operation.get(STATIC_SERVER_ADDRESS).asString() : null;
        final int staticServerPort = operation.hasDefined(STATIC_SERVER_PORT) ? operation.get(STATIC_SERVER_PORT).asInt() : -1;

        final boolean useStun = operation.hasDefined(USE_STUN) ? operation.get(USE_STUN).asBoolean() : false;
        final String stunServerAddress = operation.hasDefined(STUN_SERVER_ADDRESS) ? operation.get(STUN_SERVER_ADDRESS).asString() : null;
        final int stunServerPort = operation.hasDefined(STUN_SERVER_PORT) ? operation.get(STUN_SERVER_PORT).asInt() : -1;

        final SipConnectorService service = new SipConnectorService(operation.require(PROTOCOL).asString(), operation.get(SCHEME).asString(), useStaticAddress, staticServerAddress, staticServerPort, useStun, stunServerAddress, stunServerPort);
        final ServiceBuilder<Connector> serviceBuilder = context.getServiceTarget().addService(SipSubsystemServices.JBOSS_SIP_CONNECTOR.append(name), service)
                .addDependency(SipSubsystemServices.JBOSS_SIP, SipServer.class, service.getServer())
                .addDependency(SocketBinding.JBOSS_BINDING_NAME.append(bindingRef), SocketBinding.class, service.getBinding());
        serviceBuilder.setInitialMode(enabled ? Mode.ACTIVE : Mode.NEVER);
        if (enabled) {
            serviceBuilder.addListener(verificationHandler);
        }
        final ServiceController<Connector> serviceController = serviceBuilder.install();
        if(newControllers != null) {
            newControllers.add(serviceController);
        }
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        return SipSubsystemDescriptions.getConnectorAdd(locale);
    }

    private ModelNode resolveExpressions(OperationContext context, ModelNode connector) throws OperationFailedException {
        ModelNode result = connector.clone();
        for (Property p :connector.asPropertyList()){
            ModelNode node = p.getValue();
            if (node.getType() == ModelType.EXPRESSION){
                result.get(p.getName()).set(context.resolveExpressions(node));
            }
        }
        return result;
    }

}

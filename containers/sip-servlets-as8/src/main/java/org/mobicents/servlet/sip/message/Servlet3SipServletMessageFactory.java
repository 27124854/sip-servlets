/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package org.mobicents.servlet.sip.message;

import javax.sip.Dialog;
import javax.sip.Transaction;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.mobicents.servlet.sip.core.MobicentsSipFactory;
import org.mobicents.servlet.sip.core.MobicentsSipServletMessageFactory;
import org.mobicents.servlet.sip.core.message.MobicentsSipServletRequest;
import org.mobicents.servlet.sip.core.message.MobicentsSipServletResponse;
import org.mobicents.servlet.sip.core.session.MobicentsSipSession;

/**
 * @author jean.deruelle@gmail.com
 *
 *         This class is based on org.mobicents.servlet.sip.message.Servlet3SipServletResponseImpl class from sip-servlet-as7
 *         project, re-implemented for jboss as8 (wildfly) by:
 * @author kakonyi.istvan@alerant.hu
 *
 */
public class Servlet3SipServletMessageFactory implements MobicentsSipServletMessageFactory {

    private SipFactoryImpl sipFactoryImpl;

    /*
     * (non-Javadoc)
     *
     * @see org.mobicents.servlet.sip.core.MobicentsSipServletMessageFactory# createSipServletRequest(javax.sip.message.Request,
     * org.mobicents.servlet.sip.core.MobicentsSipFactory, org.mobicents.servlet.sip.core.session.MobicentsSipSession,
     * javax.sip.Transaction, javax.sip.Dialog, boolean)
     */
    @Override
    public MobicentsSipServletRequest createSipServletRequest(Request request, MobicentsSipSession sipSession,
            Transaction transaction, Dialog dialog, boolean createDialog) {
        return new Servlet3SipServletRequestImpl(request, sipFactoryImpl, sipSession, transaction, dialog, createDialog);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.mobicents.servlet.sip.core.MobicentsSipServletMessageFactory#
     * createSipServletResponse(javax.sip.message.Response, org.mobicents.servlet.sip.core.MobicentsSipFactory,
     * javax.sip.Transaction, org.mobicents.servlet.sip.core.session.MobicentsSipSession, javax.sip.Dialog, boolean, boolean)
     */
    @Override
    public MobicentsSipServletResponse createSipServletResponse(Response response, Transaction transaction,
            MobicentsSipSession session, Dialog dialog, boolean hasBeenReceived, boolean isRetransmission) {
        return new Servlet3SipServletResponseImpl(response, sipFactoryImpl, transaction, session, dialog, hasBeenReceived,
                isRetransmission);
    }

    /**
     * @param mobicentsSipFactory the mobicentsSipFactory to set
     */
    public void setMobicentsSipFactory(MobicentsSipFactory mobicentsSipFactory) {
        this.sipFactoryImpl = (SipFactoryImpl) mobicentsSipFactory;
    }

    /**
     * @return the mobicentsSipFactory
     */
    public MobicentsSipFactory getMobicentsSipFactory() {
        return sipFactoryImpl;
    }
}

/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.ui.web.admin.cainterface;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;

import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.publisher.ActiveDirectoryPublisher;
import org.ejbca.core.model.ca.publisher.BasePublisher;
import org.ejbca.core.model.ca.publisher.CustomPublisherContainer;
import org.ejbca.core.model.ca.publisher.ValidationAuthorityPublisher;
import org.ejbca.core.model.ca.publisher.LdapPublisher;
import org.ejbca.core.model.ca.publisher.LdapSearchPublisher;
import org.ejbca.core.model.ca.publisher.PublisherConnectionException;
import org.ejbca.core.model.ca.publisher.PublisherConst;
import org.ejbca.core.model.ca.publisher.PublisherExistsException;
import org.ejbca.ui.web.RequestHelper;
import org.ejbca.ui.web.admin.configuration.EjbcaWebBean;


/**
 * Contains help methods used to parse a publisher jsp page requests.
 *
 * @author  Philip Vendil
 * @version $Id: EditPublisherJSPHelper.java 14106 2012-02-16 09:11:44Z primelars $
 */
public class EditPublisherJSPHelper implements java.io.Serializable {

    /**
     * Determines if a de-serialized file is compatible with this class.
     *
     * Maintainers must change this value if and only if the new version
     * of this class is not compatible with old versions. See Sun docs
     * for <a href=http://java.sun.com/products/jdk/1.1/docs/guide
     * /serialization/spec/version.doc.html> details. </a>
     *
     */
	private static final long serialVersionUID = 436830207093078434L;
	
    public static final String ACTION                              = "action";
    public static final String ACTION_EDIT_PUBLISHERS              = "editpublishers";
    public static final String ACTION_EDIT_PUBLISHER               = "editpublisher";

    public static final String ACTION_CHANGE_PUBLISHERTYPE         = "changepublishertype";


    public static final String CHECKBOX_VALUE                     = BasePublisher.TRUE;

//  Used in publishers.jsp
    public static final String BUTTON_EDIT_PUBLISHER              = "buttoneditpublisher";
    public static final String BUTTON_DELETE_PUBLISHER            = "buttondeletepublisher";
    public static final String BUTTON_ADD_PUBLISHER               = "buttonaddpublisher";
    public static final String BUTTON_RENAME_PUBLISHER            = "buttonrenamepublisher";
    public static final String BUTTON_CLONE_PUBLISHER             = "buttonclonepublisher";

    public static final String SELECT_PUBLISHER                   = "selectpublisher";
    public static final String TEXTFIELD_PUBLISHERNAME            = "textfieldpublishername";
    public static final String HIDDEN_PUBLISHERNAME               = "hiddenpublishername";

//  Buttons used in publisher.jsp
    public static final String BUTTON_TESTCONNECTION    = "buttontestconnection";
    public static final String BUTTON_SAVE              = "buttonsave";
    public static final String BUTTON_CANCEL            = "buttoncancel";

    public static final String TYPE_CUSTOM              = "typecustom";
    public static final String TYPE_LDAP                = "typeldap";
    public static final String TYPE_AD                  = "typead";
    public static final String TYPE_LDAP_SEARCH         = "typeldapsearch";

    public static final String HIDDEN_PUBLISHERTYPE      = "hiddenpublishertype";
    public static final String SELECT_PUBLISHERTYPE      = "selectpublishertype";

    public static final String SELECT_APPLICABLECAS      = "selectapplicablecas";
    public static final String TEXTAREA_DESCRIPTION      = "textareadescription";

    public static final String TEXTFIELD_CUSTOMCLASSPATH = "textfieldcustomclasspath";
    public static final String TEXTAREA_CUSTOMPROPERTIES = "textareacustomproperties";

    public static final String TEXTFIELD_LDAPHOSTNAME          = "textfieldldaphostname";
    public static final String TEXTFIELD_LDAPPORT              = "textfieldldapport";
    public static final String TEXTFIELD_LDAPBASEDN            = "textfieldldapbasedn";
    public static final String TEXTFIELD_LDAPLOGINDN           = "textfieldldaplogindn";
    public static final String TEXTFIELD_LDAPUSEROBJECTCLASS   = "textfieldldapuserobjectclass";
    public static final String TEXTFIELD_LDAPCAOBJECTCLASS     = "textfieldldapcaobjectclass";
    public static final String TEXTFIELD_LDAPUSERCERTATTRIBUTE = "textfieldldapusercertattribute";
    public static final String TEXTFIELD_LDAPCACERTATTRIBUTE   = "textfieldldapcacertattribute";
    public static final String TEXTFIELD_LDAPCRLATTRIBUTE      = "textfieldldapcrlattribute";
    public static final String TEXTFIELD_LDAPDELTACRLATTRIBUTE = "textfieldldapdeltacrlattribute";
    public static final String TEXTFIELD_LDAPARLATTRIBUTE      = "textfieldldaparlattribute";
    public static final String TEXTFIELD_LDAPSEARCHBASEDN      = "textfieldldapsearchbasedn";
    public static final String TEXTFIELD_LDAPSEARCHFILTER      = "textfieldldapsearchfilter";
    public static final String TEXTFIELD_LDAPTIMEOUT           = "textfieldldaptimeout";
    public static final String TEXTFIELD_LDAPREADTIMEOUT       = "textfieldldapreadtimeout";
    public static final String TEXTFIELD_LDAPSTORETIMEOUT      = "textfieldldapstoretimeout";
    public static final String TEXTFIELD_VA_DATASOURCE         = "textfieldvadatasource";
    public static final String PASSWORD_LDAPLOGINPASSWORD      = "textfieldldaploginpassword";
    public static final String PASSWORD_LDAPCONFIRMLOGINPWD    = "textfieldldaploginconfirmpwd";
    public static final String CHECKBOX_LDAPUSESSL             = "checkboxldapusessl";
    public static final String CHECKBOX_LDAPCREATENONEXISTING  = "checkboxldapcreatenonexisting";
    public static final String CHECKBOX_LDAPMODIFYEXISTING     = "checkboxldapmodifyexisting";
    public static final String CHECKBOX_LDAPMODIFYEXISTINGATTRIBUTES     = "checkboxldapmodifyexistingattributes";
    public static final String CHECKBOX_LDAPADDNONEXISTING     = "checkboxldapaddnonexisting";
    public static final String CHECKBOX_LDAP_CREATEINTERMEDIATENODES = "checkboxldapcreateintermediatenodes";
    public static final String CHECKBOX_LDAPADDMULTIPLECERTIFICATES= "checkboxaldapddmultiplecertificates";
    public static final String CHECKBOX_LDAP_REVOKE_REMOVECERTIFICATE = "checkboxldaprevokeremovecertificate";
    public static final String CHECKBOX_LDAP_REVOKE_REMOVEUSERONCERTREVOKE = "checkboxldaprevokeuseroncertrevoke";
    public static final String CHECKBOX_LDAP_SET_USERPASSWORD  = "checkboxldapsetuserpassword";
    public static final String CHECKBOX_ONLYUSEQUEUE           = "textfieldonlyusequeue";
    public static final String CHECKBOX_KEEPPUBLISHEDINQUEUE   = "textfieldkeeppublishedinqueue";
    public static final String CHECKBOX_USEQUEUEFORCRLS        = "textfieldusequeueforcrls";
    public static final String CHECKBOX_USEQUEUEFORCERTIFICATES = "textfieldusequeueforcertificates";
    public static final String CHECKBOX_VA_STORECERT           = "textfieldvastorecert";
    public static final String CHECKBOX_VA_STORECRL            = "textfieldvastorecrl";
    public static final String CHECKBOX_VA_ONLY_PUBLISH_REVOKED = "checkboxonlypublishrevoked";
    
    public static final String SELECT_LDAPUSEFIELDINLDAPDN     = "selectldapusefieldsinldapdn";

    public static final String CHECKBOX_ADUSEPASSWORD          = "checkboxadusepassword";
    public static final String SELECT_ADUSERACCOUNTCONTROL     = "selectaduseraccountcontrol";
    public static final String SELECT_ADSAMACCOUNTNAME         = "selectsamaccountname";
    public static final String TEXTFIELD_ADUSERDESCRIPTION     = "textfieldaduserdescription";

    public static final String PAGE_PUBLISHER                  = "publisherpage.jspf";
    public static final String PAGE_PUBLISHERS                 = "publisherspage.jspf";

    /** Creates new LogInterfaceBean */
    public EditPublisherJSPHelper(){
    }
    // Public methods.
    /**
     * Method that initialized the bean.
     *
     * @param request is a reference to the http request.
     */
    public void initialize(HttpServletRequest request, EjbcaWebBean ejbcawebbean,
            CAInterfaceBean cabean) throws  Exception{

        if(!initialized){
            this.cabean = cabean;
            initialized = true;
            issuperadministrator = false;
            try{
                issuperadministrator = ejbcawebbean.isAuthorizedNoLog(AccessRulesConstants.ROLE_SUPERADMINISTRATOR);
            }catch(AuthorizationDeniedException ade){}
        }
    }

    public String parseRequest(HttpServletRequest request) throws AuthorizationDeniedException{
        String includefile = PAGE_PUBLISHERS;
        String publisher = null;
        PublisherDataHandler handler  = cabean.getPublisherDataHandler();
        String action = null;

        try {
            RequestHelper.setDefaultCharacterEncoding(request);
        } catch (UnsupportedEncodingException e1) {
            // itgnore
        }
        action = request.getParameter(ACTION);
        if( action != null){
            if( action.equals(ACTION_EDIT_PUBLISHERS)){
                if( request.getParameter(BUTTON_EDIT_PUBLISHER) != null){
                    publisher = request.getParameter(SELECT_PUBLISHER);
                    if(publisher != null){
                        if(!publisher.trim().equals("")){
                            includefile=PAGE_PUBLISHER;
                            this.publishername = publisher;
                            this.publisherdata = handler.getPublisher(publishername);
                        }
                        else{
                            publisher= null;
                        }
                    }
                    if(publisher == null){
                        includefile=PAGE_PUBLISHERS;
                    }
                }
                if( request.getParameter(BUTTON_DELETE_PUBLISHER) != null) {
                    publisher = request.getParameter(SELECT_PUBLISHER);
                    if(publisher != null){
                        if(!publisher.trim().equals("")){
                            publisherdeletefailed = handler.removePublisher(publisher);
                        }
                    }
                    includefile=PAGE_PUBLISHERS;
                }
                if( request.getParameter(BUTTON_RENAME_PUBLISHER) != null){
                    // Rename selected publisher and display profilespage.
                    String newpublishername = request.getParameter(TEXTFIELD_PUBLISHERNAME);
                    String oldpublishername = request.getParameter(SELECT_PUBLISHER);
                    if(oldpublishername != null && newpublishername != null){
                        if(!newpublishername.trim().equals("") && !oldpublishername.trim().equals("")){
                            try{
                                handler.renamePublisher(oldpublishername.trim(),newpublishername.trim());
                            }catch( PublisherExistsException e){
                                publisherexists=true;
                            }
                        }
                    }
                    includefile=PAGE_PUBLISHERS;
                }
                if( request.getParameter(BUTTON_ADD_PUBLISHER) != null){
                    publisher = request.getParameter(TEXTFIELD_PUBLISHERNAME);
                    if(publisher != null){
                        if(!publisher.trim().equals("")){
                            try{
                                handler.addPublisher(publisher.trim(), new LdapPublisher());
                            }catch( PublisherExistsException e){
                                publisherexists=true;
                            }
                        }
                    }
                    includefile=PAGE_PUBLISHERS;
                }
                if( request.getParameter(BUTTON_CLONE_PUBLISHER) != null){
                    String newpublishername = request.getParameter(TEXTFIELD_PUBLISHERNAME);
                    String oldpublishername = request.getParameter(SELECT_PUBLISHER);
                    if(oldpublishername != null && newpublishername != null){
                        if(!newpublishername.trim().equals("") && !oldpublishername.trim().equals("")){
                            handler.clonePublisher(oldpublishername.trim(),newpublishername.trim());
                        }
                    }
                    includefile=PAGE_PUBLISHERS;
                }
            }
            if( action.equals(ACTION_EDIT_PUBLISHER)){
                // Display edit access rules page.
                publisher = request.getParameter(HIDDEN_PUBLISHERNAME);
                this.publishername = publisher;
                if(publisher != null){
                    if(!publisher.trim().equals("")){
                        if(request.getParameter(BUTTON_SAVE) != null ||
                                request.getParameter(BUTTON_TESTCONNECTION) != null){

                            if(publisherdata == null){
                                int tokentype = Integer.valueOf(request.getParameter(HIDDEN_PUBLISHERTYPE)).intValue();
                                if(tokentype == PublisherConst.TYPE_CUSTOMPUBLISHERCONTAINER) {
                                    publisherdata = new CustomPublisherContainer();
                                }
                                if(tokentype == PublisherConst.TYPE_LDAPPUBLISHER) {
                                    publisherdata = new LdapPublisher();
                                }
                                if (tokentype == PublisherConst.TYPE_LDAPSEARCHPUBLISHER) {
                                    publisherdata = new LdapSearchPublisher();
                                }
                                if(tokentype == PublisherConst.TYPE_ADPUBLISHER) {
                                    publisherdata = new ActiveDirectoryPublisher();
                                }
                                if(tokentype == PublisherConst.TYPE_VAPUBLISHER) {
                                    publisherdata = new ValidationAuthorityPublisher();
                                }
                            }
                            // Save changes.

                            // General settings
                            String value = request.getParameter(TEXTAREA_DESCRIPTION);
                            if(value != null){
                                value = value.trim();
                                publisherdata.setDescription(value);
                            }
                        	value = request.getParameter(CHECKBOX_ONLYUSEQUEUE);
                        	publisherdata.setOnlyUseQueue(value != null && value.equals(CHECKBOX_VALUE));
                        	value = request.getParameter(CHECKBOX_KEEPPUBLISHEDINQUEUE);
                        	publisherdata.setKeepPublishedInQueue(value != null && value.equals(CHECKBOX_VALUE));
                        	value = request.getParameter(CHECKBOX_USEQUEUEFORCRLS);
                        	publisherdata.setUseQueueForCRLs(value != null && value.equals(CHECKBOX_VALUE));
                        	value = request.getParameter(CHECKBOX_USEQUEUEFORCERTIFICATES);
                        	publisherdata.setUseQueueForCertificates(value != null && value.equals(CHECKBOX_VALUE));

                            if(publisherdata instanceof CustomPublisherContainer){
                                value = request.getParameter(TEXTFIELD_CUSTOMCLASSPATH);
                                if(value != null){
                                    value = value.trim();
                                    ((CustomPublisherContainer) publisherdata).setClassPath(value);
                                }
                                value = request.getParameter(TEXTAREA_CUSTOMPROPERTIES);
                                if(value != null){
                                    value = value.trim();
                                    ((CustomPublisherContainer) publisherdata).setPropertyData(value);
                                }
                            }

                            if(publisherdata instanceof LdapPublisher){
                                LdapPublisher ldappublisher = (LdapPublisher) publisherdata;

                                value = request.getParameter(TEXTFIELD_LDAPHOSTNAME);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setHostnames(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPPORT);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setPort(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPBASEDN);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setBaseDN(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPLOGINDN);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setLoginDN(value);
                                }
                                value = request.getParameter(PASSWORD_LDAPLOGINPASSWORD);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setLoginPassword(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPTIMEOUT);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setConnectionTimeOut(Integer.parseInt(value));
                                }
                                value = request.getParameter(TEXTFIELD_LDAPREADTIMEOUT);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setReadTimeOut(Integer.parseInt(value));
                                }
                                value = request.getParameter(TEXTFIELD_LDAPSTORETIMEOUT);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setStoreTimeOut(Integer.parseInt(value));
                                }
                                value = request.getParameter(TEXTFIELD_LDAPUSEROBJECTCLASS);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setUserObjectClass(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPCAOBJECTCLASS);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setCAObjectClass(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPUSERCERTATTRIBUTE);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setUserCertAttribute(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPCACERTATTRIBUTE);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setCACertAttribute(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPCRLATTRIBUTE);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setCRLAttribute(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPDELTACRLATTRIBUTE);
                                if(value != null){
                                	value = value.trim();
                                	ldappublisher.setDeltaCRLAttribute(value);
                                }
                                value = request.getParameter(TEXTFIELD_LDAPARLATTRIBUTE);
                                if(value != null){
                                    value = value.trim();
                                    ldappublisher.setARLAttribute(value);
                                }
                                value = request.getParameter(CHECKBOX_LDAPUSESSL);
                                if(value != null) {
                                    ldappublisher.setUseSSL(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    ldappublisher.setUseSSL(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAPCREATENONEXISTING);
                                if(value != null) {
                                    ldappublisher.setCreateNonExistingUsers(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    ldappublisher.setCreateNonExistingUsers(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAPMODIFYEXISTING);
                                if(value != null) {
                                    ldappublisher.setModifyExistingUsers(value.equals(CHECKBOX_VALUE));
                                } 
                                else {
                                    ldappublisher.setModifyExistingUsers(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAPMODIFYEXISTINGATTRIBUTES);
                                if(value != null) {
                                    ldappublisher.setModifyExistingAttributes(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    ldappublisher.setModifyExistingAttributes(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAPADDNONEXISTING);
                                if(value != null) {
                                    ldappublisher.setAddNonExistingAttributes(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    ldappublisher.setAddNonExistingAttributes(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAP_CREATEINTERMEDIATENODES);
                                if(value != null) {
                                    ldappublisher.setCreateIntermediateNodes(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    ldappublisher.setCreateIntermediateNodes(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAPADDMULTIPLECERTIFICATES);
                                if(value != null) {
                                    ldappublisher.setAddMultipleCertificates(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    ldappublisher.setAddMultipleCertificates(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAP_REVOKE_REMOVECERTIFICATE);
                                if(value != null) {
                                    ldappublisher.setRemoveRevokedCertificates(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    ldappublisher.setRemoveRevokedCertificates(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAP_REVOKE_REMOVEUSERONCERTREVOKE);
                                if(value != null) {
                                    ldappublisher.setRemoveUsersWhenCertRevoked(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    ldappublisher.setRemoveUsersWhenCertRevoked(false);
                                }
                                value = request.getParameter(CHECKBOX_LDAP_SET_USERPASSWORD);
                                if(value != null) {
                                    ldappublisher.setUserPassword(value.equals(CHECKBOX_VALUE));
                                } else {
                                    ldappublisher.setUserPassword(false);
                                }
                                
                                String[] values = request.getParameterValues(SELECT_LDAPUSEFIELDINLDAPDN);
                                if(values != null){
                                    ArrayList<Integer> usefields = new ArrayList<Integer>();
                                    for(int i=0;i< values.length;i++){
                                        usefields.add(Integer.valueOf(values[i]));
                                    }

                                    ldappublisher.setUseFieldInLdapDN(usefields);
                                }
                            }


                            if (publisherdata instanceof LdapSearchPublisher) {
                              LdapSearchPublisher ldapsearchpublisher = (LdapSearchPublisher) publisherdata;

                              value = request.getParameter(TEXTFIELD_LDAPSEARCHBASEDN);
                              if (value != null) {
                                value = value.trim();
                                ldapsearchpublisher.setSearchBaseDN(value);
                              }
                              value = request.getParameter(TEXTFIELD_LDAPSEARCHFILTER);
                              if (value != null) {
                                value = value.trim();
                                ldapsearchpublisher.setSearchFilter(value);
                              }
                            }


                            if(publisherdata instanceof ActiveDirectoryPublisher){
                                ActiveDirectoryPublisher adpublisher = (ActiveDirectoryPublisher) publisherdata;

                                value = request.getParameter(SELECT_ADSAMACCOUNTNAME);
                                if(value != null){
                                    value = value.trim();
                                    adpublisher.setSAMAccountName(Integer.parseInt(value));
                                }

                                value = request.getParameter(TEXTFIELD_ADUSERDESCRIPTION);
                                if(value != null){
                                    value = value.trim();
                                    adpublisher.setUserDescription(value);
                                }

                                value = request.getParameter(CHECKBOX_ADUSEPASSWORD);
                                if(value != null) {
                                    adpublisher.setUseUserPassword(value.equals(CHECKBOX_VALUE));
                                }
                                else {
                                    adpublisher.setUseUserPassword(false);
                                }
                                value = request.getParameter(SELECT_ADUSERACCOUNTCONTROL);
                                if(value != null) {
                                    value = value.trim();
                                    adpublisher.setUserAccountControl(Integer.parseInt(value));
                                }
                            }
                            
                            // Get parameters for ValidationAuthorityPublisher
                            if(this.publisherdata instanceof ValidationAuthorityPublisher){
                            	final ValidationAuthorityPublisher vaPub = (ValidationAuthorityPublisher)this.publisherdata;
                            	
                            	final String vDataSource = request.getParameter(TEXTFIELD_VA_DATASOURCE);
                            	if(vDataSource != null){
                            		vaPub.setDataSource(vDataSource.trim());
                            	}
                            	final String vCert = request.getParameter(CHECKBOX_VA_STORECERT);
                            	final boolean isCert = vCert!=null && vCert.equals(CHECKBOX_VALUE);
                            	vaPub.setStoreCert( isCert );

                            	final String vOnlyRevoked = request.getParameter(CHECKBOX_VA_ONLY_PUBLISH_REVOKED);
                            	final boolean isOnlyRevoked = vOnlyRevoked!=null && vOnlyRevoked.equals(CHECKBOX_VALUE);
                            	vaPub.setOnlyPublishRevoked(isOnlyRevoked);

                            	final String vCRL = request.getParameter(CHECKBOX_VA_STORECRL);
                            	// the CA certificate must be in the DB of the VA in order to fetch the CRL for this CA (isCert && !isOnlyRevoked)
                            	vaPub.setStoreCRL( isCert && !isOnlyRevoked && vCRL!=null && vCRL.equals(CHECKBOX_VALUE) );
                            }


                            if(request.getParameter(BUTTON_SAVE) != null){
                                handler.changePublisher(publisher,publisherdata);
                                includefile=PAGE_PUBLISHERS;
                            }
                            if(request.getParameter(BUTTON_TESTCONNECTION)!= null){
                                connectionmessage = true;
                                handler.changePublisher(publisher,publisherdata);
                                try{
                                    handler.testConnection(publisher);
                                    connectionsuccessful = true;
                                }catch(PublisherConnectionException pce){
                                    connectionerrormessage = pce.getMessage();
                                }
                                includefile=PAGE_PUBLISHER;
                            }

                        }
                        if(request.getParameter(BUTTON_CANCEL) != null){
                            // Don't save changes.
                            includefile=PAGE_PUBLISHERS;
                        }

                    }
                }
            }

            if( action.equals(ACTION_CHANGE_PUBLISHERTYPE)){
                this.publishername = request.getParameter(HIDDEN_PUBLISHERNAME);
                String value = request.getParameter(SELECT_PUBLISHERTYPE);
                if(value!=null){
                    int profiletype = Integer.parseInt(value);
                    switch(profiletype){
                    case PublisherConst.TYPE_CUSTOMPUBLISHERCONTAINER :
                        publisherdata = new CustomPublisherContainer();
                        break;
                    case PublisherConst.TYPE_LDAPPUBLISHER :
                        publisherdata =  new LdapPublisher();
                        break;
                    case PublisherConst.TYPE_LDAPSEARCHPUBLISHER:
                        publisherdata = new LdapSearchPublisher();
                        break;
                    case PublisherConst.TYPE_ADPUBLISHER :
                        publisherdata =  new ActiveDirectoryPublisher();
                        break;
                    case PublisherConst.TYPE_VAPUBLISHER:
                        publisherdata =  new ValidationAuthorityPublisher();
                        break;
                    }
                }

                includefile=PAGE_PUBLISHER;
            }
        }

        return includefile;
    }

    public int getPublisherType(){
        int retval = PublisherConst.TYPE_CUSTOMPUBLISHERCONTAINER;

        if(publisherdata instanceof CustomPublisherContainer) {
            retval = PublisherConst.TYPE_CUSTOMPUBLISHERCONTAINER;
        }
        if(publisherdata instanceof LdapPublisher) {
            retval = PublisherConst.TYPE_LDAPPUBLISHER;
        }
        if (publisherdata instanceof LdapSearchPublisher) {
            retval = PublisherConst.TYPE_LDAPSEARCHPUBLISHER;
        }
        if(publisherdata instanceof ActiveDirectoryPublisher) {
            retval = PublisherConst.TYPE_ADPUBLISHER;
        }
        if(publisherdata instanceof ValidationAuthorityPublisher) {
            retval = PublisherConst.TYPE_VAPUBLISHER;
        }
        return retval;
    }

    public int getPublisherQueueLength() {
    	return getPublisherQueueLength(publishername);
    }
    public int[] getPublisherQueueLength(int[] intervalLower, int[] intervalUpper) {
    	return getPublisherQueueLength(publishername, intervalLower, intervalUpper);
    }
    
    public int getPublisherQueueLength(String publishername) {
    	return cabean.getPublisherQueueLength(cabean.getPublisherDataHandler().getPublisherId(publishername));
    }
    public int[] getPublisherQueueLength(String publishername, int[] intervalLower, int[] intervalUpper) {
    	return cabean.getPublisherQueueLength(cabean.getPublisherDataHandler().getPublisherId(publishername), intervalLower, intervalUpper);
    }

    // Private fields.
    private CAInterfaceBean cabean;
    private boolean initialized=false;
    public boolean  publisherexists       = false;
    public boolean  publisherdeletefailed = false;
    public boolean  connectionmessage = false;
    public boolean  connectionsuccessful = false;
    public String   connectionerrormessage = "";
    public boolean  issuperadministrator = false;
    public BasePublisher publisherdata = null;
    public String publishername = null;


}

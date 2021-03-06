
package org.ejbca.core.protocol.ws.client.gen;

import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;


/**
 * This class was generated by the JAXWS SI.
 * JAX-WS RI 2.0_01-b59-fcs
 * Generated source version: 2.0
 * 
 */
@WebServiceClient(name = "EjbcaWSService", targetNamespace = "http://ws.protocol.core.ejbca.org/", wsdlLocation = "/home/anders/workspace/ejbca/modules/dist/EjbcaWSService.wsdl")
public class EjbcaWSService
    extends Service
{

    private final static URL EJBCAWSSERVICE_WSDL_LOCATION;

    static {
        URL url = null;
        try {
            url = new URL("file:/home/anders/workspace/ejbca/modules/dist/EjbcaWSService.wsdl");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        EJBCAWSSERVICE_WSDL_LOCATION = url;
    }

    public EjbcaWSService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public EjbcaWSService() {
        super(EJBCAWSSERVICE_WSDL_LOCATION, new QName("http://ws.protocol.core.ejbca.org/", "EjbcaWSService"));
    }

    /**
     * 
     * @return
     *     returns EjbcaWS
     */
    @WebEndpoint(name = "EjbcaWSPort")
    public EjbcaWS getEjbcaWSPort() {
        return (EjbcaWS)super.getPort(new QName("http://ws.protocol.core.ejbca.org/", "EjbcaWSPort"), EjbcaWS.class);
    }

}

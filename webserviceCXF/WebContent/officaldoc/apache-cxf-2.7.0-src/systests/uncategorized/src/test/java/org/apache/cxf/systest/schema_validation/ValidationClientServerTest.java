/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.systest.schema_validation;

import java.io.Serializable;
import java.net.URL;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.WebServiceException;

import org.apache.cxf.annotations.SchemaValidation.SchemaValidationType;
import org.apache.cxf.message.Message;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.schema_validation.SchemaValidation;
import org.apache.schema_validation.SchemaValidationService;
import org.apache.schema_validation.types.ComplexStruct;
import org.apache.schema_validation.types.OccuringStruct;
import org.apache.schema_validation.types.SomeRequest;
import org.apache.schema_validation.types.SomeResponse;
import org.junit.BeforeClass;
import org.junit.Test;

public class ValidationClientServerTest extends AbstractBusClientServerTestBase {
    public static final String PORT = ValidationServer.PORT;

    private final QName serviceName = new QName("http://apache.org/schema_validation",
                                                "SchemaValidationService");
    private final QName portName = new QName("http://apache.org/schema_validation", "SoapPort");


    @BeforeClass
    public static void startservers() throws Exception {
        assertTrue("server did not launch correctly", launchServer(ValidationServer.class, true));
    }

    // TODO : Change this test so that we test the combinations of
    // client and server with schema validation enabled/disabled...
    // Only tests client side validation enabled/server side disabled.
    @Test
    public void testSchemaValidation() throws Exception {
        SchemaValidation validation = createService(Boolean.TRUE);
        ComplexStruct complexStruct = new ComplexStruct();
        complexStruct.setElem1("one");
        // Don't initialize a member of the structure.  
        // Client side validation should throw an exception.
        // complexStruct.setElem2("two");
        complexStruct.setElem3(3);
        try {
            /*boolean result =*/
            validation.setComplexStruct(complexStruct);
            fail("Set ComplexStruct should have thrown ProtocolException");
        } catch (WebServiceException e) {
            String expected = "'{\"http://apache.org/schema_validation/types\":elem2}' is expected.";
            assertTrue(e.getMessage(), e.getMessage().indexOf(expected) != -1);
        }

        OccuringStruct occuringStruct = new OccuringStruct();
        // Populate the list in the wrong order.
        // Client side validation should throw an exception.
        List<Serializable> floatIntStringList = occuringStruct.getVarFloatAndVarIntAndVarString();
        floatIntStringList.add(new Integer(42));
        floatIntStringList.add(new Float(4.2f));
        floatIntStringList.add("Goofus and Gallant");
        try {
            /*boolean result =*/
            validation.setOccuringStruct(occuringStruct);
            fail("Set OccuringStruct should have thrown ProtocolException");
        } catch (WebServiceException e) {
            String expected = "'{\"http://apache.org/schema_validation/types\":varFloat}' is expected.";
            assertTrue(e.getMessage().indexOf(expected) != -1);
        }
        
        validation = createService(Boolean.FALSE);

        try {
            // The server will attempt to return an invalid ComplexStruct
            // When validation is disabled on the server side, we'll get the
            // exception while unmarshalling the invalid response.
            /*complexStruct =*/
            validation.getComplexStruct("Hello");
            fail("Get ComplexStruct should have thrown ProtocolException");
        } catch (WebServiceException e) {
            String expected = "'{\"http://apache.org/schema_validation/types\":elem2}' is expected.";
            assertTrue("Found message " + e.getMessage(), 
                       e.getMessage().indexOf(expected) != -1);
        }

        validation = createService(Boolean.TRUE);
        
        try {
            // The server will attempt to return an invalid OccuringStruct
            // When validation is disabled on the server side, we'll get the
            // exception while unmarshalling the invalid response.
            /*occuringStruct =*/
            validation.getOccuringStruct("World");
            fail("Get OccuringStruct should have thrown ProtocolException");
        } catch (WebServiceException e) {
            String expected = "'{\"http://apache.org/schema_validation/types\":varFloat}' is expected.";
            assertTrue(e.getMessage().indexOf(expected) != -1);
        }
    }

    @Test
    public void testRequestFailedSchemaValidation() throws Exception {
        assertFailedRequestValidation(Boolean.TRUE);
    }
    
    @Test
    public void testFailedRequestSchemaValidationTypeBoth() throws Exception {
        assertFailedRequestValidation(SchemaValidationType.BOTH.name());
    }
    
    @Test
    public void testFailedSchemaValidationSchemaValidationTypeOut() throws Exception {
        assertFailedRequestValidation(SchemaValidationType.OUT.name());
    }
    
    @Test
    public void testIgnoreRequestSchemaValidationNone() throws Exception {
        assertIgnoredRequestValidation(SchemaValidationType.NONE.name());
    }
    
    @Test
    public void testIgnoreRequestSchemaValidationResponseOnly() throws Exception {
        assertIgnoredRequestValidation(SchemaValidationType.IN.name());
    }
    
    @Test
    public void testIgnoreRequestSchemaValidationFalse() throws Exception {
        assertIgnoredRequestValidation(Boolean.FALSE);
    }
    
    @Test
    public void testResponseFailedSchemaValidation() throws Exception {
        assertFailureResponseValidation(Boolean.TRUE);
    }
    
    @Test
    public void testResponseSchemaFailedValidationBoth() throws Exception {
        assertFailureResponseValidation(SchemaValidationType.BOTH.name());
    }
    
    @Test
    public void testResponseSchemaFailedValidationIn() throws Exception {
        assertFailureResponseValidation(SchemaValidationType.IN.name());
    }
    
    @Test
    public void testIgnoreResponseSchemaFailedValidationNone() throws Exception {
        assertIgnoredResponseValidation(SchemaValidationType.NONE.name());
    }
    
    @Test
    public void testIgnoreResponseSchemaFailedValidationFalse() throws Exception {
        assertIgnoredResponseValidation(Boolean.FALSE);
    }

    @Test
    public void testIgnoreResponseSchemaFailedValidationOut() throws Exception {
        assertIgnoredResponseValidation(SchemaValidationType.OUT.name());
    }

    private SomeResponse execute(SchemaValidation service, String id) throws Exception { 
        SomeRequest request = new SomeRequest();
        request.setId(id);
        return service.doSomething(request);
    }
    
    private void assertFailureResponseValidation(Object validationConfig) throws Exception {
        SchemaValidation service = createService(validationConfig);
        
        SomeResponse response = execute(service, "1111111111"); // valid request
        assertEquals(response.getTransactionId(), "aaaaaaaaaa");
        
        try {
            execute(service, "1234567890"); // valid request, but will result in invalid response
            fail("should catch marshall exception as the invalid incoming message per schema");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Unmarshalling Error"));
            assertTrue(e.getMessage().contains("is not facet-valid with respect to pattern"));
        }
    }
    
    private void assertIgnoredRequestValidation(Object validationConfig) throws Exception {
        SchemaValidation service = createService(validationConfig);
        
        // this is an invalid request but validation is turned off.
        SomeResponse response = execute(service, "1234567890aaaa");
        assertEquals(response.getTransactionId(), "aaaaaaaaaa");
    }
    
    private void assertIgnoredResponseValidation(Object validationConfig) throws Exception {
        SchemaValidation service = createService(validationConfig);
        
        // the request will result in invalid response but validation is turned off
        SomeResponse response = execute(service, "1234567890");
        assertEquals(response.getTransactionId(), "aaaaaaaaaaxxx");
    }
    
    private void assertFailedRequestValidation(Object validationConfig) throws Exception {
        SchemaValidation service = createService(validationConfig);
        
        SomeResponse response = execute(service, "1111111111");
        assertEquals(response.getTransactionId(), "aaaaaaaaaa");
        
        try {
            execute(service, "1234567890aaa");
            fail("should catch marshall exception as the invalid outgoing message per schema");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Marshalling Error"));
            assertTrue(e.getMessage().contains("is not facet-valid with respect to pattern"));
        }
    }
    
    private SchemaValidation createService(Object validationConfig) throws Exception {
        URL wsdl = getClass().getResource("/wsdl/schema_validation.wsdl");
        assertNotNull(wsdl);

        SchemaValidationService service = new SchemaValidationService(wsdl, serviceName);
        assertNotNull(service);

        SchemaValidation validation = service.getPort(portName, SchemaValidation.class);
        updateAddressPort(validation, PORT);
        ((BindingProvider)validation).getRequestContext().put(Message.SCHEMA_VALIDATION_ENABLED, validationConfig);
        ((BindingProvider)validation).getResponseContext().put(Message.SCHEMA_VALIDATION_ENABLED, validationConfig);
        
        return validation;
    }
}

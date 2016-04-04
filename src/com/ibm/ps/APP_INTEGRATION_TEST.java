package com.ibm.ps;

import java.io.*;
import java.io.ObjectInputStream.GetField;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import com.ibm.broker.config.common.CommsMessageConstants;
import com.ibm.broker.config.common.XMLHelper;
import com.ibm.broker.config.proxy.*;
import com.ibm.broker.config.*;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.apache.http.*;
import org.apache.http.client.fluent.*;
import org.apache.http.entity.ContentType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.ibm.broker.config.proxy.MessageFlowProxy;
import com.ibm.broker.config.proxy.AdministeredObject;
	
public class APP_INTEGRATION_TEST {

	static boolean testOnServerUsingInegrationAPI(String integrationNodeName, String integrationServerName){

		boolean result = false;

		try {
			// Integration API initialization
			BrokerProxy nodeProxy = BrokerProxy.getLocalInstance(integrationNodeName);
			if (!nodeProxy.isRunning())	return false;
			
			//Trace
			System.out.println("nodeproxy status, running = " + nodeProxy.isRunning());
			
			ExecutionGroupProxy serverProxy = nodeProxy.getExecutionGroupByName(integrationServerName);
			if (!serverProxy.isRunning()) serverProxy.start();
			
			//Trace
			System.out.println("serverproxy status, running = " + serverProxy.isRunning());
			
			// ENABLE injection and recording mode
			serverProxy.setInjectionMode(AttributeConstants.MODE_ENABLED);
			serverProxy.setTestRecordMode(AttributeConstants.MODE_ENABLED);
			
			//Trace
			
			
			// the calls above are asynchronous. We could implement a listener to check when the
			// calls have taken effect but to keep the code simple we'll include a 1 second sleep.
			try { Thread.sleep(1000); } catch (InterruptedException e) { }
			
			System.out.println("InjectionMode status = " + serverProxy.getInjectionMode());
			System.out.println("TestRecordMode status = " + serverProxy.getTestRecordMode());
			
				// INJECT a previously-recorded message to drive the flow
				// you can cut and paste this from the Recorded Message in the Flow Exerciser, or obtain it programmatically
				// using similar code to this. In this case the behaviour of the flow does not depend on the content of the
				// Properties or HTTPInputHeader, so these are omitted. Likewise we haven't supplied data for the
				// LocalEnvironment or Environment trees (which you would need to associate with their respective properties).
				//String message = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><MessagePool xmlns='http://wwww.ibm.com/iib/test/1.0.0'> <InputMessage timestamp='1459737248686' nodeId='FCMComposite_1_1' name='new message 2' id='1459737248686-285'> <plainText></plainText> </InputMessage><InputMessage timestamp='1459391707906' nodeId='FCMComposite_1_1' name='new message 1' id='1459391707906-6'> <plainText>%7B%0A++%22left%22%3A+0%2C%0A++%22right%22%3A+0%0A%7D</plainText>  </InputMessage></MessagePool>";
			
			//String message = "<message xmlns:iib='http://com.ibm.iib/lt/1.0' iib:parser='WSRoot'><Properties/><XMLNSC iib:parser='xmlnsc'><ns1:data xmlns:ns1='http://ns1'><a iib:valueType='INTEGER'>42</a><b iib:valueType='INTEGER'>43</b></ns1:data></XMLNSC></message>";
				//String message = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><MessagePool xmlns='http://wwww.ibm.com/iib/test/1.0.0'><InputMessage timestamp='1459737248686' nodeId='FCMComposite_1_1' name='new message 2' id='1459737248686-285'><plainText></plainText></InputMessage><InputMessage timestamp='1459391707906' nodeId='FCMComposite_1_1' name='new message 1' id='1459391707906-6'><plainText>%7B%0A++%22left%22%3A+0%2C%0A++%22right%22%3A+6%0A%7D</plainText></InputMessage></MessagePool>";
				//String message  = IOUtils.toString(APP_INTEGRATION_TEST.class.getClass().getResourceAsStream("/root/git/JsonJsonApplication/FlowTest/src/com/ibm/ps/input.xml"));
				String message = readFile("/root/git/JsonJsonApplication/FlowTest/src/com/ibm/ps/inputData.xml", StandardCharsets.UTF_8);
				//Trace
				System.out.println("message = " + message);
				
				Properties injectProps = new Properties();
				injectProps.setProperty(AttributeConstants.DATA_INJECTION_APPLICATION_LABEL, "JsonJsonApplication"); // Application name
				injectProps.setProperty(AttributeConstants.DATA_INJECTION_MESSAGEFLOW_LABEL, "ZOSRest01"); // Flow name
				injectProps.setProperty(AttributeConstants.DATA_INJECTION_NODE_UUID, "ZOSRest01#FCMComposite_1_1");// "<flowName>#FCMComposite_1_<nodenumber>" nodenumber given by node ID in .bar>.msgflow>.txt file
				injectProps.setProperty(AttributeConstants.DATA_INJECTION_WAIT_TIME, "60000");
				injectProps.setProperty(AttributeConstants.DATA_INJECTION_MESSAGE_SECTION, message);
				// The node uuid consists of the flow name, appended to the node id that you can find in the '.msgflow' in the bar file
				boolean synchronous = true; // making the call synchronous means we don't need to sleep afterwards
				
				result = serverProxy.injectTestData(injectProps, synchronous);
				
				//Trace
				System.out.println("Injection result = " + result);
				
				// RETRIEVE recorded message
				// because we enabled recording, IIB has recorded the logical tree at each connection that
				// the injected message travelled through. Now we retrieve the message(s) we want by specifying the
				// set of properties that define those connections. In this case I just specified the mapping
				// node as the source of the connection I want to check, but you can specify more properties
				// if required such as the target node, terminal etc.
				Properties filterProps = new Properties();
				filterProps.put(Checkpoint.PROPERTY_SOURCE_NODE_NAME, "ZOSRest01#FCMComposite_1_2");
				List<RecordedTestData> dataList = serverProxy.getRecordedTestData(filterProps);
				
				// why do we get a list back? If the flow has been run more than once since recording was enabled, or
				// if the flow contains a loop, then multiple messages will be recorded.
				// TEST the message against some criteria
				boolean usingREST = false;
				result = runAssertionsAgainstData(dataList, usingREST);
				
				//Trace
				System.out.println("All assertions are: " + result);
				
				
				// clear recorded data and reset server to turn off recording and injection
				serverProxy.clearRecordedTestData();
				serverProxy.setInjectionMode(AttributeConstants.MODE_DISABLED);
				serverProxy.setTestRecordMode(AttributeConstants.MODE_DISABLED);
			}
			catch (ConfigManagerProxyPropertyNotInitializedException e) { System.out.println("ERROR: "+e); }
			catch (ConfigManagerProxyLoggedException e) { System.out.println("ERROR: "+e); }
			finally {
				return result;
			}
		}

	// helper method to run assertions against the recorded message you retrieve - this is used for the REST example too
	static boolean runAssertionsAgainstData(List<RecordedTestData> dataList, boolean useREST){
		
		//Trace
		System.out.println("Inside runAssertionsAgainstTestData");
		
		// check that at least one recorded message was retrieved
		if (dataList == null || dataList.size() == 0) { 
			
			//Trace
			System.out.println("dataList is null");
			
			
			return false; 
		}
		
		//Trace
		System.out.println("dataList not null");
		for(RecordedTestData data: dataList) {
			System.out.println(data.getTestData());
		}
		
		boolean result = true;
		try {
			// check that the message looks ok. You could make a string comparison against a
			// previously captured reference message. Or, as here, check the value of specific
			// elements in the message that you are interested in.
			String xPathAssertion1 = "/message/JSON/Data/Imeplementation[.='Java_SpingBoot']";
			//String xPathAssertion2 = "/message/JSON/Data/Result[.='6']";
			
			
			for (RecordedTestData data : dataList) {
				String messageData = data.getTestData().getMessage();
				
				//Trace
				System.out.println("Retrieved Message = " + messageData);
				//Trace
				System.out.println("Test retrieved message against following XPath assertion = " + xPathAssertion1);
				
				// see section on REST API if (useREST) { messageData = unwrapXML(messageData); }
				ByteArrayInputStream bais = new ByteArrayInputStream(messageData.getBytes(CommsMessageConstants.TEXT_ENCODING));
				Document logicalTree = XMLHelper.parse(bais);
				Element logicalTreeRoot = logicalTree.getDocumentElement();
				
				// evaluate XPath expression against the parsed XML
				XPath xPath = XPathFactory.newInstance().newXPath();
				NodeList nodes = (NodeList)xPath.evaluate(xPathAssertion1, logicalTreeRoot, XPathConstants.NODESET);
				
				if (nodes.getLength() != 1) {
					result = false;
					System.out.println("ERROR: no elements found that match XPath "+ xPathAssertion1);
				}
			}
		}
		finally {
			return result;
		}
	}
	
	public static void main(String[] args){

		System.out.println("Begin integration testing");
		String integrationNodeName = "TESTNODE_root";
		String integrationServerName = "default";
		testOnServerUsingInegrationAPI(integrationNodeName, integrationServerName);
	}
	
	static String readFile(String path, Charset encoding) 
			  throws IOException 
			{
			  byte[] encoded = Files.readAllBytes(Paths.get(path));
			  return new String(encoded, encoding);
			}
}

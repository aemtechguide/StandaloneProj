package com.standalone.aem;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

public class AssetUploadService {
	
	
	private static String grant_type = "password";
	private static String userName = "admin";
	private static String passWord = "admin";
	private static String envHost = "localhost";
	
	

	public static void main(String[] args) throws JSONException {
		AssetUploadService client = new AssetUploadService();
		String filePath = "C:\\Dev_Content\\testing.html";
		//String filePath = "C:\\GIT\\QA\\QA_BRANCH\\NEWUI_BRANCH\\MetaFiles\\sampleHtml.html";
		client.uploadcontent(filePath, "metadataString","text/html");
		
		
	}
	

		
	public HttpPost httpRequestSetUp(String servletPath, DefaultHttpClient client) throws JSONException {
		HttpPost post = null;
		
			post = new HttpPost(servletPath);
			post.addHeader("AUTH_USER", userName);
			CookieStore cookieStore = new BasicCookieStore();
			String ssoToken = generateOAMToken();
			BasicClientCookie cookie = new BasicClientCookie("ObSSOCookie", ssoToken);
			
			cookie.setPath("/");
			cookieStore.addCookie(cookie);
			client.setCookieStore(cookieStore);

		
		return post;

	}

	
	
	
	/**
	 * This method generates the OAM token by passing the credentials.
	 * 
	 * @return
	 */
	public String generateOAMToken() {
		String ssoToken = null;
		HttpHost targetHost = null;
		try {
			
			targetHost = new HttpHost(envHost, 4502, "http");
			
			DefaultHttpClient client = new DefaultHttpClient();
			BufferedReader rd = null;
			try {
				
	
				HttpPost post = new HttpPost("/c/login/index.html");
	
				AuthScope authScope = new AuthScope(targetHost.getHostName(), targetHost.getPort());
				UsernamePasswordCredentials creds = new UsernamePasswordCredentials(userName,passWord);//("cgcoreapp.gen", "cgcoreapp@wem");
				client.getCredentialsProvider().setCredentials(authScope, creds);
	
				// Create AuthCache instance
				AuthCache authCache = new BasicAuthCache();
				// Generate BASIC scheme object and add it to the local
				// auth cache
				BasicScheme basicAuth = new BasicScheme();
				authCache.put(targetHost, basicAuth);
	
				// Add AuthCache to the execution context
				BasicHttpContext localcontext = new BasicHttpContext();
				localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);
	
				HttpResponse response = client.execute(targetHost, post, localcontext);
				System.out.println("***************************"+response.getStatusLine());
				rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				String line = "";
				while ((line = rd.readLine()) != null) {
					System.out.println(line);
				}
				org.apache.http.Header[] allHeaders = response.getAllHeaders();
				for (int i = 0; i < allHeaders.length; i++) {
					System.out.println("Header Name:" + allHeaders[i].getName());
					System.out.println("Header Value:" + allHeaders[i].getValue());
				}
				/*List<org.apache.http.cookie.Cookie> cookies = client.getCookieStore().getCookies();
				if (cookies.isEmpty()) {
					System.out.println("None");
				} else {
					System.out.println("cookie not null");
					for (int i = 0; i < cookies.size(); i++) {
						System.out.println("- " + cookies.get(i).toString());
						String cookie = cookies.get(i).getName();
						if ("ObSSOCookie".equalsIgnoreCase(cookie)) {
							ssoToken = cookies.get(i).getValue();
						}
					}
				}*/
			} finally {
				System.out.println("&&&&&&&&&&&&&&&&&&&&&&&");
				if (rd != null) {
					rd.close();
				}
				client.getConnectionManager().shutdown();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("SSOToken = " + ssoToken);
		return ssoToken;
	}

	
	
	/**
	 * This method calls the uploadcontent webservice.
	 * 
	 * @param filePath the file to be
	 * @return
	 * @throws JSONException
	 */
	public String uploadcontent(String filePath, String contentMetadata, String mimeType) throws JSONException {
		HttpHost targetHost = null;
		try {
			
			
			targetHost = new HttpHost(envHost, 4502, "http");
			
			DefaultHttpClient client = new DefaultHttpClient();
			BufferedReader rd = null;
			try {
				
	
				//HttpPost post = httpRequestSetUp("/content/dam.completeUpload.json", client);
				HttpPost post = new HttpPost("/content/dam.completeUpload.json");		
				
				post.addHeader("Content-Type", "application/json");
				FileBody content = new FileBody(new File(filePath), mimeType);
				//FileBody content = new FileBody(new File(filePath));
				//StringBody metadata = new StringBody(contentMetadata, "text/plain", Charset.forName("UTF-8"));
			//	System.out.println("contentMetadata :"+contentMetadata);
				
								
				StringBody metadata = new StringBody(contentMetadata);
				MultipartEntity reqEntity = new MultipartEntity();
				//reqEntity.addPart("content", content);
				reqEntity.addPart("file", content);
				//reqEntity.addPart("content-type", "application/x-www-form-urlencoded");
	
				post.setEntity(reqEntity);
				
				HttpResponse response = client.execute(targetHost, post);
				System.out.println("@@@@@@@@@@@@@@@"+response.getStatusLine());
				rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
	
				StringBuffer responseContent = new StringBuffer();
				String line = "";
				while ((line = rd.readLine()) != null) {
					responseContent.append(line);
				}
				
				
				System.out.println("responseContent : "+responseContent.toString());
				JSONObject jsonObject = new JSONObject(responseContent.toString());
				String statusCode = jsonObject.getString("statuscode");
				String statusMessage = jsonObject.getString("statusmessage");
				String sourcePath = jsonObject.has("sourcepath") ? jsonObject.getString("sourcepath") : "";
				System.out.println("statusCode" + statusCode);
				System.out.println("statusMessage" + statusMessage);
				System.out.println("sourcePathHHH" + sourcePath);
			} finally {
				if (rd != null) {
					rd.close();
				}
				client.getConnectionManager().shutdown();
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("IO Error");
		}
		return "Success";
	}
	/*
	public static Node createFolderStructure(String path, String validFolderNamePattern, Session session) throws RepositoryException, WEMServicesException {
		log.debug("Entering createFolderStructure method.");
		long startTime = System.currentTimeMillis();
		Node parentNode = null;
		try {
			parentNode = session.getRootNode();
			StringTokenizer st = new StringTokenizer(path, "/");
			log.info("path==="+path);
			while (st.hasMoreTokens()) {
				String token = st.nextToken();
				log.debug("token = " + token);
				if (!parentNode.hasNode(token)) {
					if (!token.matches(validFolderNamePattern)) {
						log.error("Invalid folder name");
						ErrorInfo info = new ErrorInfo(WEMServicesConstants.STATUS_CODE_400, "Error: Invalid folder name.", null);
						throw new WEMServicesException(info);
					}
					Node folderNode = parentNode.addNode(token, "sling:OrderedFolder");
					folderNode.addNode("jcr:content", "nt:unstructured");
				}
				parentNode = parentNode.getNode(token);
			}
			log.info("folder structure created succesfully");
		} catch (RepositoryException e) {
			parentNode.refresh(false);
			throw e;
		}
		long endTime = System.currentTimeMillis();
		log.debug("Time Taken in the createFolderStructure() method : " + (endTime - startTime) / 1000 + " Seconds");
		log.debug("Exiting createFolderStructure method");
		return parentNode;
	}
	
	public static void unzip(String zipFile, String outputFolder) throws PostProcessorException {

		{
			logger.debug("Extraction of zip ---- started");
			File dir = new File(outputFolder);
			if (!dir.exists()) {
				dir.mkdirs();
			}
			byte[] buffer = new byte[1024];
			try (FileInputStream fis = new FileInputStream(zipFile);ZipInputStream zis = new ZipInputStream(fis)){
				ZipEntry ze = zis.getNextEntry();
				while (ze != null) {
					String fileName = ze.getName();
					File newFile = new File(outputFolder + File.separator + fileName);
					logger.debug("Unzipping to {}", newFile.getAbsolutePath());

					new File(newFile.getParent()).mkdirs();
					try(FileOutputStream fos = new FileOutputStream(newFile)){
						int len;
						while ((len = zis.read(buffer)) > 0) {
							fos.write(buffer, 0, len);
						}
					}
					zis.closeEntry();
					ze = zis.getNextEntry();
				}
				zis.closeEntry();
				logger.debug("Extraction of zip Successfully---- Completed");
			} catch (IOException e) {
				logger.error("Error in Extraction of zip :", e);
			}
		}
	}
	
	*
	*
	*/
	
}


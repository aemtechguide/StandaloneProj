package com.standalone.aem;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import com.standalone.aem.connect.Login;

/**
 * The Class CreateNodeHeirarchy. Simple standalone class to create some pages
 * 
 * @author sudheerdvn
 */
public class CreateNodeHeirarchy {

	/**
	 * The main method.
	 *
	 * @param args
	 *            the arguments
	 */
	public static void main(String[] args) {
		Session session = Login.getSession(null);
		System.out.println("session is  :" + session);
		if (null != session) {
			createNodes(session);
		}
	}

	/**
	 * Creates the nodes.
	 *
	 * @param session
	 *            the session
	 */
	private static void createNodes(Session session) {
		try {
			Node node = session.getNode("/content/aem-flash");
			if (null != node && node.hasNode("en")) {
				System.out.println(node.getPath());
				Node enNode = node.getNode("en");
				if (null != enNode) {
					System.out.println(enNode.getPath());
					Node aboutUsPage = enNode.addNode("about-us", "cq:Page");
					if (null != aboutUsPage) {
						System.out.println(aboutUsPage.getPath());
						Node jcrContent = aboutUsPage.addNode("jcr:content", "cq:PageContent");
						if (null != jcrContent) {
							jcrContent.setProperty("cq:template",
									"/conf/aem-flash/settings/wcm/templates/content-page");
							jcrContent.setProperty("sling:resourceType", "aem-flash/components/structure/page");
							jcrContent.setProperty("jcr:title", "About Us");
						}
					}
					session.save();
					System.out.println("Node created succesfully");
				}
			}
		} catch (PathNotFoundException e) {
			e.printStackTrace();
		} catch (RepositoryException e) {
			e.printStackTrace();
		} finally {
			session.logout();
		}
	}
}
